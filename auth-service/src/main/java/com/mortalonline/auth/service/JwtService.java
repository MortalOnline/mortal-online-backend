package com.mortalonline.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Emision y validacion de JWT (HS256). Emite dos tipos de token:
 *  - scope "2fa":    token temporal tras el login con contrasena correcta;
 *                    solo sirve para completar la verificacion TOTP.
 *  - scope "access": token definitivo que aceptan el gateway y los demas
 *                    microservicios.
 */
@Service
public class JwtService {

    public static final String SCOPE_ACCESS = "access";
    public static final String SCOPE_2FA = "2fa";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-ttl-minutes:60}") long accessTtlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    public String createAccessToken(Long userId, String username) {
        return buildToken(userId, username, SCOPE_ACCESS, accessTtl);
    }

    /** Token corto que solo autoriza a completar el paso de 2FA. */
    public String createPending2faToken(Long userId, String username) {
        return buildToken(userId, username, SCOPE_2FA, Duration.ofMinutes(5));
    }

    private String buildToken(Long userId, String username, String scope, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("scope", scope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException si el token es invalido o expiro */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
