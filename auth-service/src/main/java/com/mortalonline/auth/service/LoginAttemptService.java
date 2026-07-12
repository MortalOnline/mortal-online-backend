package com.mortalonline.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteccion contra fuerza bruta de contraseñas: tras N intentos fallidos
 * seguidos, la cuenta queda BLOQUEADA temporalmente (el rate limiting del
 * gateway limita por IP; esto limita por CUENTA, que es lo que frena un
 * ataque distribuido de diccionario).
 *
 * El estado vive en memoria: un reinicio lo limpia, lo cual es aceptable —
 * el objetivo es frenar la velocidad del ataque, no llevar contabilidad.
 */
@Service
public class LoginAttemptService {

    private record Attempts(int fails, Instant lockedUntil) {
    }

    private final Map<String, Attempts> byUser = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockout;

    public LoginAttemptService(
            @Value("${security.lockout.max-attempts:5}") int maxAttempts,
            @Value("${security.lockout.minutes:15}") long lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    /** @throws ResponseStatusException 429 si la cuenta esta bloqueada */
    public void checkNotLocked(String username) {
        Attempts a = byUser.get(key(username));
        if (a == null || a.lockedUntil() == null) return;
        if (a.lockedUntil().isAfter(Instant.now())) {
            long minutes = Math.max(1, Duration.between(Instant.now(), a.lockedUntil()).toMinutes() + 1);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos; espera " + minutes + " minuto(s)");
        }
        byUser.remove(key(username)); // el bloqueo ya vencio: empezar de cero
    }

    public void recordFailure(String username) {
        byUser.compute(key(username), (k, cur) -> {
            int fails = (cur == null ? 0 : cur.fails()) + 1;
            return fails >= maxAttempts
                    ? new Attempts(0, Instant.now().plus(lockout)) // se bloquea
                    : new Attempts(fails, null);
        });
    }

    /** Login correcto: limpia el contador. */
    public void reset(String username) {
        byUser.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
