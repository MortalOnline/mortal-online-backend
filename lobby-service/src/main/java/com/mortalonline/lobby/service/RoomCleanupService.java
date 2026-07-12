package com.mortalonline.lobby.service;

import com.mortalonline.lobby.entity.Room;
import com.mortalonline.lobby.repository.ChatMessageRepository;
import com.mortalonline.lobby.repository.RoomPlayerRepository;
import com.mortalonline.lobby.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Limpieza automatica de salas: toda sala con mas de {@code lobby.room-ttl-minutes}
 * de antiguedad se elimina (con sus jugadores y su chat), aunque siga ocupada —
 * evita que el lobby acumule salas fantasma de sesiones viejas.
 */
@Service
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    private final RoomRepository rooms;
    private final RoomPlayerRepository players;
    private final ChatMessageRepository messages;
    private final LobbyService lobby;
    private final Duration ttl;

    public RoomCleanupService(RoomRepository rooms, RoomPlayerRepository players,
                              ChatMessageRepository messages, LobbyService lobby,
                              @Value("${lobby.room-ttl-minutes:30}") long ttlMinutes) {
        this.rooms = rooms;
        this.players = players;
        this.messages = messages;
        this.lobby = lobby;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** Barrido cada minuto: borra las salas que superaron su tiempo de vida. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void purgeExpiredRooms() {
        List<Room> expired = rooms.findByCreatedAtBefore(Instant.now().minus(ttl));
        if (expired.isEmpty()) return;
        for (Room room : expired) {
            players.deleteByRoomId(room.getId());
            messages.deleteByRoomId(room.getId());
            rooms.delete(room);
        }
        log.info("Salas expiradas eliminadas: {}", expired.size());
        lobby.broadcastRooms();
    }
}
