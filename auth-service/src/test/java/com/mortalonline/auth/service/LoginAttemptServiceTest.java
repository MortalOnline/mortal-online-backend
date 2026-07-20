package com.mortalonline.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proteccion anti fuerza-bruta por CUENTA (HU-02 / seguridad). Logica pura en
 * memoria: se prueba sin Spring ni base de datos.
 */
class LoginAttemptServiceTest {

    @Test
    void unaCuentaNuevaNoEstaBloqueada() {
        LoginAttemptService s = new LoginAttemptService(5, 15);
        assertDoesNotThrow(() -> s.checkNotLocked("juan"));
    }

    @Test
    void seBloqueaTrasCincoIntentosFallidos() {
        LoginAttemptService s = new LoginAttemptService(5, 15);
        for (int i = 0; i < 5; i++) s.recordFailure("juan");

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> s.checkNotLocked("juan"));
        assertEquals(429, ex.getStatusCode().value()); // 429 TOO_MANY_REQUESTS
    }

    @Test
    void cuatroFallosNoBloquean() {
        LoginAttemptService s = new LoginAttemptService(5, 15);
        for (int i = 0; i < 4; i++) s.recordFailure("juan");
        assertDoesNotThrow(() -> s.checkNotLocked("juan"));
    }

    @Test
    void loginCorrectoReiniciaElContador() {
        LoginAttemptService s = new LoginAttemptService(5, 15);
        for (int i = 0; i < 4; i++) s.recordFailure("juan");
        s.reset("juan");
        // tras el reset, 4 fallos mas siguen sin bloquear (no se acumulan 8)
        for (int i = 0; i < 4; i++) s.recordFailure("juan");
        assertDoesNotThrow(() -> s.checkNotLocked("juan"));
    }

    @Test
    void laCuentaSeIdentificaSinDistinguirMayusculas() {
        LoginAttemptService s = new LoginAttemptService(5, 15);
        for (int i = 0; i < 5; i++) s.recordFailure("Juan");
        // "Juan" y "juan" son la misma cuenta
        assertThrows(ResponseStatusException.class, () -> s.checkNotLocked("juan"));
    }
}
