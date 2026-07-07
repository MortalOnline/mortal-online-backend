package com.mortalonline.lobby.service;

import com.mortalonline.lobby.repository.RoomPlayerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana de RECONEXION (escenario de disponibilidad): si un jugador pierde
 * el WebSocket, su puesto en la sala se conserva durante 5 segundos. Si
 * reconecta dentro de la ventana, no pierde la sala; si no, se le expulsa y
 * la lista de salas se actualiza para todos.
 */
@Service
public class PresenceService {

    private record PendingKick(Long roomId, Instant disconnectedAt) {
    }

    private final Map<Long, PendingKick> pending = new ConcurrentHashMap<>();
    private final RoomPlayerRepository players;
    private final LobbyService lobby;
    private final Duration window;

    public PresenceService(RoomPlayerRepository players, LobbyService lobby,
                           @Value("${lobby.reconnect-window-seconds:5}") long windowSeconds) {
        this.players = players;
        this.lobby = lobby;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = userIdOf(event.getUser());
        if (userId == null) return;
        players.findFirstByUserIdOrderByJoinedAtDesc(userId).ifPresent(rp ->
                pending.put(userId, new PendingKick(rp.getRoomId(), Instant.now())));
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        Long userId = userIdOf(event.getUser());
        if (userId != null) {
            // reconecto dentro de la ventana: conserva su sala
            pending.remove(userId);
        }
    }

    /** Barrido cada segundo: expulsa a quien no reconecto a tiempo. */
    @Scheduled(fixedDelay = 1000)
    public void evictExpired() {
        Instant limit = Instant.now().minus(window);
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().disconnectedAt().isBefore(limit)) {
                lobby.removePlayer(entry.getValue().roomId(), entry.getKey());
                return true;
            }
            return false;
        });
    }

    private Long userIdOf(Principal principal) {
        if (principal == null) return null;
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
