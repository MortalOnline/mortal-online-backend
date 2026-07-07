package com.mortalonline.auth.service;

import com.mortalonline.auth.entity.RefreshToken;
import com.mortalonline.auth.entity.User;
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
 * Flujo de autenticacion completo:
 *   register -> login (contrasena, BCrypt) -> verify-2fa (TOTP) -> JWT.
 * El 2FA es OBLIGATORIO: el login con contrasena correcta nunca emite el JWT
 * final, solo un token temporal con scope "2fa".
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final TotpService totp;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwt, TotpService totp,
                       @Value("${security.jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.totp = totp;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
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
        String secret = totp.generateSecret();
        User user = new User(req.username().trim(), req.email(), passwordEncoder.encode(req.password()), secret);
        user = users.save(user);
        // El secreto TOTP se entrega UNA sola vez para configurar la app de
        // autenticacion; no vuelve a exponerse por ningun endpoint.
        return new Dtos.RegisterResponse(user.getId(), user.getUsername(), secret,
                totp.otpauthUrl(user.getUsername(), secret));
    }

    @Transactional(readOnly = true)
    public Dtos.LoginResponse login(Dtos.LoginRequest req) {
        User user = users.findByUsername(req.username() == null ? "" : req.username())
                .orElseThrow(AuthService::badCredentials);
        if (!passwordEncoder.matches(req.password() == null ? "" : req.password(), user.getPasswordHash())) {
            throw badCredentials();
        }
        // Contrasena correcta: NO se emite el JWT todavia, se exige el 2FA.
        return new Dtos.LoginResponse(true, jwt.createPending2faToken(user.getId(), user.getUsername()));
    }

    @Transactional
    public Dtos.TokenResponse verify2fa(Dtos.Verify2faRequest req) {
        Claims claims = parseOrUnauthorized(req.pendingToken());
        if (!JwtService.SCOPE_2FA.equals(claims.get("scope", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de 2FA invalido");
        }
        User user = users.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(AuthService::badCredentials);
        if (!totp.verify(user.getTotpSecret(), req.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Codigo 2FA incorrecto");
        }
        if (!user.isTotpEnabled()) user.setTotpEnabled(true);
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
        return new Dtos.MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.isTotpEnabled());
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
