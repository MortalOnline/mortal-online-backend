package com.mortalonline.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Emision y validacion de JWT (HS256). Logica pura, sin Spring.
 */
class JwtServiceTest {

    private final JwtService jwt =
            new JwtService("mortal-online-dev-secret-0123456789abcdef0123456789abcdef", 60);

    @Test
    void elTokenDeAccesoConservaSujetoUsuarioYScope() {
        Claims c = jwt.parse(jwt.createAccessToken(42L, "juan"));
        assertEquals("42", c.getSubject());
        assertEquals("juan", c.get("username"));
        assertEquals(JwtService.SCOPE_ACCESS, c.get("scope"));
    }

    @Test
    void elTokenPendienteLlevaScope2fa() {
        Claims c = jwt.parse(jwt.createPending2faToken(7L, "ana"));
        assertEquals(JwtService.SCOPE_2FA, c.get("scope"));
    }

    @Test
    void unTokenAlteradoEsRechazado() {
        String token = jwt.createAccessToken(1L, "x");
        assertThrows(JwtException.class, () -> jwt.parse(token + "tampered"));
    }

    @Test
    void unTokenFirmadoConOtraClaveEsRechazado() {
        JwtService otro = new JwtService("otra-clave-secreta-distinta-0123456789abcdef0123", 60);
        String ajeno = otro.createAccessToken(1L, "x");
        assertThrows(JwtException.class, () -> jwt.parse(ajeno));
    }

    @Test
    void exponeLaDuracionDelTokenDeAccesoEnSegundos() {
        assertEquals(3600, jwt.accessTtlSeconds());
    }
}
