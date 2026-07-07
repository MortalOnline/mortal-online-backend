package com.mortalonline.combat.match;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Estado EN MEMORIA de las partidas en curso, un registro por sala activa.
 * Este es el corazon del escenario de rendimiento: el flujo de sincronizacion
 * de combate (inputs y rondas) NUNCA toca la base de datos; PostgreSQL solo
 * se usa al finalizar la partida.
 */
@Component
public class ActiveMatchRegistry {

    public static final class ActiveMatch {
        public final Long roomId;
        public final Long player1Id;
        public final Long player2Id;
        public final Instant startedAt = Instant.now();
        public final List<RoundResult> rounds = new CopyOnWriteArrayList<>();

        ActiveMatch(Long roomId, Long player1Id, Long player2Id) {
            this.roomId = roomId;
            this.player1Id = player1Id;
            this.player2Id = player2Id;
        }

        public boolean hasPlayer(Long userId) {
            return player1Id.equals(userId) || player2Id.equals(userId);
        }
    }

    public record RoundResult(int roundNumber, Long winnerId, int durationSeconds) {
    }

    private final Map<Long, ActiveMatch> byRoom = new ConcurrentHashMap<>();

    /** Registra la partida en memoria (idempotente por sala). */
    public ActiveMatch start(Long roomId, Long player1Id, Long player2Id) {
        return byRoom.computeIfAbsent(roomId, id -> new ActiveMatch(id, player1Id, player2Id));
    }

    public ActiveMatch get(Long roomId) {
        return byRoom.get(roomId);
    }

    public ActiveMatch remove(Long roomId) {
        return byRoom.remove(roomId);
    }
}
