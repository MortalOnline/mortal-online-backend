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
 * Flujo de autenticacion completo (verificacion de dos pasos por CORREO):
 *   register (correo + contrasena BCrypt)
 *   -> login: si la contrasena es correcta, se ENVIA UN CODIGO DE 6 DIGITOS
 *      al correo registrado (no se emite el JWT todavia)
 *   -> verify-2fa: valida el codigo recibido por correo y emite el JWT.
 * El codigo expira, es de un solo uso y admite maximo 5 intentos.
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
    private final Duration refreshTtl;
    private final Duration otpTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, EmailOtpRepository otps,
                       PasswordEncoder passwordEncoder, JwtService jwt, MailService mail,
                       @Value("${security.jwt.refresh-ttl-days:7}") long refreshTtlDays,
                       @Value("${security.otp.ttl-minutes:10}") long otpTtlMinutes) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.mail = mail;
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
        return new Dtos.RegisterResponse(user.getId(), user.getUsername(), user.getEmail());
    }

    /**
     * Paso 1 del login: valida la contrasena y, si es correcta, envia el
     * codigo de verificacion AL CORREO registrado. El JWT NO se emite aqui.
     */
    @Transactional
    public Dtos.LoginResponse login(Dtos.LoginRequest req) {
        User user = users.findByUsername(req.username() == null ? "" : req.username())
                .orElseThrow(AuthService::badCredentials);
        if (!passwordEncoder.matches(req.password() == null ? "" : req.password(), user.getPasswordHash())) {
            throw badCredentials();
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        otps.deleteByUserId(user.getId()); // un solo codigo vigente por usuario
        otps.save(new EmailOtp(user.getId(), code, Instant.now().plus(otpTtl)));
        mail.sendLoginCode(user.getEmail(), user.getUsername(), code, otpTtl.toMinutes());
        return new Dtos.LoginResponse(true,
                jwt.createPending2faToken(user.getId(), user.getUsername()),
                maskEmail(user.getEmail()));
    }

    /** Paso 2: valida el codigo que llego al correo y emite el JWT. */
    @Transactional
    public Dtos.TokenResponse verify2fa(Dtos.Verify2faRequest req) {
        Claims claims = parseOrUnauthorized(req.pendingToken());
        if (!JwtService.SCOPE_2FA.equals(claims.get("scope", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de 2FA invalido");
        }
        User user = users.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(AuthService::badCredentials);

        EmailOtp otp = otps.findFirstByUserIdOrderByIdDesc(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "No hay un codigo vigente; inicia sesion de nuevo"));
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            otps.delete(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El codigo expiro; inicia sesion de nuevo");
        }
        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otps.delete(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Demasiados intentos; inicia sesion de nuevo");
        }
        if (req.code() == null || !otp.getCode().equals(req.code().trim())) {
            otp.registerFailedAttempt();
            otps.save(otp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Codigo incorrecto");
        }
        otps.delete(otp); // un solo uso
        return issueTokens(user);
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
