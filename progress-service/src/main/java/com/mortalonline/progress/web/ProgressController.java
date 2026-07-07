package com.mortalonline.progress.web;

import com.mortalonline.progress.service.ProgressService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/progress")
public class ProgressController {

    private final ProgressService progress;
    private final String internalToken;

    public ProgressController(ProgressService progress,
                              @Value("${progress.internal-token}") String internalToken) {
        this.progress = progress;
        this.internalToken = internalToken;
    }

    /** Tabla ordenada de mejores jugadores (HU-12). */
    @GetMapping("/scoreboard")
    public List<Dtos.ScoreboardEntry> scoreboard() {
        return progress.scoreboard();
    }

    /** Estadisticas de un jugador (incluye si tiene a Reptile desbloqueado). */
    @GetMapping("/stats/{userId}")
    public Dtos.StatsView stats(@PathVariable Long userId) {
        return progress.statsOf(userId);
    }

    /**
     * Endpoint INTERNO: solo Combat Service lo invoca al terminar una partida
     * (validado por el token interno). Un resultado que no pase por Combat
     * Service NUNCA se acepta — el cliente no puede reportar victorias.
     */
    @PostMapping("/matches-completed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void matchCompleted(@RequestHeader(value = "X-Internal-Token", required = false) String token,
                               @RequestBody Dtos.MatchCompletedRequest req) {
        if (token == null || !token.equals(internalToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Combat Service puede reportar resultados");
        }
        progress.matchCompleted(req);
    }
}
