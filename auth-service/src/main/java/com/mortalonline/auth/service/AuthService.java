package com.mortalonline.auth.service;

import com.mortalonline.auth.entity.EmailOtp;
import com.mortalonline.auth.entity.RefreshToken;
import com.mortalonline.auth.entity.User;
import com.mortalonline.auth.repository.EmailOtpRepository;
import com.mortalonline.auth.repository.RefreshTokenRepository;
import com.mortalonline.auth.repository.UserRepository;
import com.mortalonline.auth.web.Dtos;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Flujo de autenticacion completo (verificacion de correo UNA sola vez):
 *   register (correo + contrasena BCrypt) -> se envia un CODIGO DE 6 DIGITOS
 *      al correo para VERIFICARLO
 *   -> verify-2fa: valida el codigo, marca el correo como verificado y emite
 *      el JWT (la cuenta queda lista y el usuario dentro).
 *   -> login: con el correo ya verificado es DIRECTO (usuario + contrasena);
 *      si la cuenta aun no verifico su correo, se reenvia el codigo.
 * Ademas: recuperacion de contrasena con codigo por correo (forgot/reset).
 * Todo codigo expira, es de un solo uso y admite maximo 5 intentos.
 */
@Service
public class AuthService {

    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final EmailOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final MailService mail;
    private final LoginAttemptService loginAttempts;
    private final Duration refreshTtl;
    private final Duration otpTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, EmailOtpRepository otps,
                       PasswordEncoder passwordEncoder, JwtService jwt, MailService mail,
                       LoginAttemptService loginAttempts,
                       @Value("${security.jwt.refresh-ttl-days:7}") long refreshTtlDays,
                       @Value("${security.otp.ttl-minutes:10}") long otpTtlMinutes) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.mail = mail;
        this.loginAttempts = loginAttempts;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.otpTtl = Duration.ofMinutes(otpTtlMinutes);
    }

    @Transactional
    public Dtos.RegisterResponse register(Dtos.RegisterRequest req) {
        if (req.username() == null || req.username().trim().length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario debe tener al menos 3 caracteres");
        }
        if (req.email() == null || !req.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo invalido");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 6 caracteres");
        }
        if (users.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya esta tomado");
        }
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya esta registrado");
        }
        User user = users.save(new User(req.username().trim(), req.email(), passwordEncoder.encode(req.password())));
        // la verificacion del correo se hace UNA vez, aqui al registrarse:
        // se envia el codigo y el cliente pasa directo a la pantalla de codigo
        String pendingToken = sendVerificationCode(user);
        return new Dtos.RegisterResponse(user.getId(), user.getUsername(), user.getEmail(),
                pendingToken, maskEmail(user.getEmail()));
    }

    /**
     * Login: valida la contrasena. Correo ya verificado -> emite el JWT
     * directamente. Correo sin verificar -> reenvia el codigo de verificacion.
     */
    @Transactional
    public Dtos.LoginResponse login(Dtos.LoginRequest req) {
        // anti fuerza-bruta: cuenta bloqueada temporalmente tras N fallos
        loginAttempts.checkNotLocked(req.username());
        User user = users.findByUsername(req.username() == null ? "" : req.username())
                .orElseThrow(() -> {
                    loginAttempts.recordFailure(req.username());
                    return badCredentials();
                });
        if (!passwordEncoder.matches(req.password() == null ? "" : req.password(), user.getPasswordHash())) {
            loginAttempts.recordFailure(req.username());
            throw badCredentials();
        }
        loginAttempts.reset(req.username()); // contraseña correcta
        if (user.isEmailVerified()) {
            return new Dtos.LoginResponse(false, null, null, issueTokens(user));
        }
        // cuenta que nunca verifico su correo (o creada antes de este flujo)
        String pendingToken = sendVerificationCode(user);
        return new Dtos.LoginResponse(true, pendingToken, maskEmail(user.getEmail()), null);
    }

    private String sendVerificationCode(User user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        otps.deleteByUserIdAndPurpose(user.getId(), EmailOtp.PURPOSE_VERIFY); // un solo codigo vigente
        otps.save(new EmailOtp(user.getId(), code, EmailOtp.PURPOSE_VERIFY, Instant.now().plus(otpTtl)));
        mail.sendLoginCode(user.getEmail(), user.getUsername(), code, otpTtl.toMinutes());
        return jwt.createPending2faToken(user.getId(), user.getUsername());
    }

    /** Valida el codigo de verificacion, marca el correo verificado y emite el JWT. */
    @Transactional
    public Dtos.TokenResponse verify2fa(Dtos.Verify2faRequest req) {
        Claims claims = parseOrUnauthorized(req.pendingToken());
        if (!JwtService.SCOPE_2FA.equals(claims.get("scope", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de 2FA invalido");
        }
        User user = users.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(AuthService::badCredentials);

        consumeOtp(user, EmailOtp.PURPOSE_VERIFY, req.code(), "inicia sesion de nuevo");
        user.markEmailVerified(); // desde ahora el login es directo
        users.save(user);
        return issueTokens(user);
    }

    // ---- Recuperacion de contrasena ----

    /**
     * Paso 1: envia un codigo de recuperacion al correo de la cuenta. La
     * respuesta es SIEMPRE la misma exista o no la cuenta (no filtrar cuales
     * usuarios/correos estan registrados).
     */
    @Transactional
    public Dtos.MessageResponse forgotPassword(Dtos.ForgotPasswordRequest req) {
        findByUsernameOrEmail(req.usernameOrEmail()).ifPresent(user -> {
            String code = String.format("%06d", random.nextInt(1_000_000));
            otps.deleteByUserIdAndPurpose(user.getId(), EmailOtp.PURPOSE_RESET);
            otps.save(new EmailOtp(user.getId(), code, EmailOtp.PURPOSE_RESET, Instant.now().plus(otpTtl)));
            mail.sendResetCode(user.getEmail(), user.getUsername(), code, otpTtl.toMinutes());
        });
        return new Dtos.MessageResponse(
                "Si la cuenta existe, enviamos un codigo de recuperacion a su correo");
    }

    /**
     * Paso 2: con el codigo que llego al correo, cambia la contrasena. Solo
     * quien tiene acceso a ese correo puede hacerlo (el codigo expira, es de
     * un solo uso y admite 5 intentos). Cierra todas las sesiones abiertas.
     */
    @Transactional
    public Dtos.MessageResponse resetPassword(Dtos.ResetPasswordRequest req) {
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 6 caracteres");
        }
        User user = findByUsernameOrEmail(req.usernameOrEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Codigo incorrecto"));
        consumeOtp(user, EmailOtp.PURPOSE_RESET, req.code(), "pide un codigo nuevo");
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        users.save(user);
        refreshTokens.deleteByUserId(user.getId()); // cerrar sesiones abiertas
        loginAttempts.reset(user.getUsername()); // desbloquear si estaba bloqueada
        return new Dtos.MessageResponse("Contrasena actualizada; ya puedes iniciar sesion");
    }

    private java.util.Optional<User> findByUsernameOrEmail(String id) {
        if (id == null || id.isBlank()) return java.util.Optional.empty();
        String value = id.trim();
        return users.findByUsername(value).or(() -> users.findByEmail(value));
    }

    /** Validacion comun de un codigo (existencia, expiracion, intentos, valor). */
    private void consumeOtp(User user, String purpose, String code, String retryHint) {
        EmailOtp otp = otps.findFirstByUserIdAndPurposeOrderByIdDesc(user.getId(), purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "No hay un codigo vigente; " + retryHint));
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otps.delete(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El codigo expiro; " + retryHint);
        }
        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otps.delete(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Demasiados intentos; " + retryHint);
        }
        if (code == null || !otp.getCode().equals(code.trim())) {
            otp.registerFailedAttempt();
            otps.save(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Codigo incorrecto");
        }
        otps.delete(otp); // un solo uso
    }

    @Transactional
    public Dtos.TokenResponse refresh(Dtos.RefreshRequest req) {
        RefreshToken stored = refreshTokens.findByToken(req.refreshToken() == null ? "" : req.refreshToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalido"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokens.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expirado");
        }
        User user = users.findById(stored.getUserId()).orElseThrow(AuthService::badCredentials);
        refreshTokens.delete(stored); // rotacion: cada refresh token es de un solo uso
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public Dtos.MeResponse me(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return new Dtos.MeResponse(user.getId(), user.getUsername(), user.getEmail());
    }

    /** Nombres publicos por id (scoreboard/salas). Solo id y username. */
    @Transactional(readOnly = true)
    public java.util.List<Dtos.UserSummary> usersByIds(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) return java.util.List.of();
        return users.findAllById(ids).stream()
                .map(u -> new Dtos.UserSummary(u.getId(), u.getUsername()))
                .toList();
    }

    private Dtos.TokenResponse issueTokens(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), refreshValue, Instant.now().plus(refreshTtl)));
        return new Dtos.TokenResponse(
                jwt.createAccessToken(user.getId(), user.getUsername()),
                refreshValue, "Bearer", jwt.accessTtlSeconds());
    }

    /** "juan.gomez@mail.com" -> "j•••@mail.com" (pista sin exponer el correo). */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "•••" + email.substring(Math.max(at, 0));
        return email.charAt(0) + "•••" + email.substring(at);
    }

    private Claims parseOrUnauthorized(String token) {
        try {
            return jwt.parse(token == null ? "" : token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalido o expirado");
        }
    }

    private static ResponseStatusException badCredentials() {
        // Mensaje unico para usuario o contrasena incorrectos (no filtrar cual)
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }
}
