package com.mortalonline.combat.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Comunicacion SINCRONA entre microservicios (REST, sin broker de mensajes):
 * al terminar una partida, Combat Service notifica a Progress Service para
 * que actualice scoreboard, racha y desbloqueos. El header X-Internal-Token
 * garantiza que SOLO Combat Service puede reportar resultados (Progress
 * nunca confia en un resultado que no haya pasado por aqui).
 */
@Component
public class ProgressClient {

    private static final Logger log = LoggerFactory.getLogger(ProgressClient.class);

    private final WebClient client;
    private final String internalToken;

    public ProgressClient(@Value("${progress.base-url}") String baseUrl,
                          @Value("${progress.internal-token}") String internalToken) {
        this.client = WebClient.builder().baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    public void notifyMatchCompleted(Long matchId, Long winnerId, Long loserId) {
        try {
            client.post()
                    .uri("/progress/matches-completed")
                    .header("X-Internal-Token", internalToken)
                    .bodyValue(Map.of("matchId", matchId, "winnerId", winnerId, "loserId", loserId))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            // La partida ya quedo persistida en Combat DB: no se pierde el
            // resultado, solo el reflejo inmediato en el scoreboard.
            log.error("No se pudo notificar a Progress Service la partida {}: {}", matchId, e.getMessage());
        }
    }
}
