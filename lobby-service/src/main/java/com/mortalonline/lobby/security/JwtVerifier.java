package com.mortalonline.lobby.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validacion del JWT emitido por Auth Service (este servicio NUNCA emite
 * tokens). Compartido por el filtro HTTP y el interceptor de STOMP.
 */
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws io.jsonwebtoken.JwtException si es invalido, expirado o no es de acceso */
    public Claims verifyAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!"access".equals(claims.get("scope", String.class))) {
            throw new io.jsonwebtoken.JwtException("El token no es de acceso");
        }
        return claims;
    }
}
