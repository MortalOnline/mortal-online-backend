package com.mortalonline.combat.ws;

import com.mortalonline.combat.match.MatchService;
import com.mortalonline.combat.web.Dtos;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * Canal de combate (HU-07). El input del jugador (golpe, patada, movimiento)
 * se RETRANSMITE al rival de inmediato — relay de baja latencia, cero acceso
 * a base de datos en este camino (escenario de rendimiento: <100ms normal,
 * <150ms bajo carga). La logica del juego (fisicas/hitboxes) vive en el
 * cliente gordo; el backend no la procesa.
 */
@Controller
public class CombatSocketController {

    private final SimpMessagingTemplate broker;
    private final MatchService matchService;

    public CombatSocketController(SimpMessagingTemplate broker, MatchService matchService) {
        this.broker = broker;
        this.matchService = matchService;
    }

    /** Relay inmediato del input al topic de la sala (el rival lo consume). */
    @MessageMapping("/combat/{roomId}/input")
    public void input(@DestinationVariable Long roomId, Map<String, Object> input, Principal principal) {
        if (input == null) return;
        broker.convertAndSend("/topic/combat/" + roomId + "/input",
                Map.of("senderId", Long.valueOf(principal.getName()), "input", input));
    }

    /** Fin de ronda: se acumula EN MEMORIA (se persiste al terminar la partida). */
    @MessageMapping("/combat/{roomId}/round-end")
    public void roundEnd(@DestinationVariable Long roomId, Dtos.RoundEnd payload, Principal principal) {
        matchService.roundEnded(roomId, Long.valueOf(principal.getName()),
                payload.roundNumber(), payload.winnerId(), payload.durationSeconds());
        broker.convertAndSend("/topic/combat/" + roomId + "/round-end", payload);
    }

    /**
     * Fin de partida: persiste Match/MatchRound y notifica a Progress Service
     * (REST sincrono) para scoreboard, racha y desbloqueo.
     */
    @MessageMapping("/combat/{roomId}/match-end")
    public void matchEnd(@DestinationVariable Long roomId, Dtos.MatchEnd payload, Principal principal) {
        var saved = matchService.matchEnded(roomId, Long.valueOf(principal.getName()), payload.winnerId());
        broker.convertAndSend("/topic/combat/" + roomId + "/match-end",
                Map.of("matchId", saved.getId(), "winnerId", saved.getWinnerId()));
    }
}
