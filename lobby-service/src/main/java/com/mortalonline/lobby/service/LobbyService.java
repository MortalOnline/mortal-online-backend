package com.mortalonline.lobby.service;

import com.mortalonline.lobby.entity.Room;
import com.mortalonline.lobby.entity.RoomPlayer;
import com.mortalonline.lobby.repository.ChatMessageRepository;
import com.mortalonline.lobby.repository.RoomPlayerRepository;
import com.mortalonline.lobby.repository.RoomRepository;
import com.mortalonline.lobby.web.Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Gestion de salas (HU-03 / HU-04). Cada cambio en las salas se difunde por
 * /topic/rooms para que todos los clientes vean la lista en tiempo real.
 * Disenado para 15+ salas concurrentes: la lista se arma con una consulta
 * ligera y la difusion es un solo mensaje broadcast.
 */
@Service
public class LobbyService {

    private final RoomRepository rooms;
    private final RoomPlayerRepository players;
    private final ChatMessageRepository messages;
    private final SimpMessagingTemplate broker;

    public LobbyService(RoomRepository rooms, RoomPlayerRepository players,
                        ChatMessageRepository messages, SimpMessagingTemplate broker) {
        this.rooms = rooms;
        this.players = players;
        this.messages = messages;
        this.broker = broker;
    }

    @Transactional(readOnly = true)
    public List<Dtos.RoomView> listActiveRooms() {
        return rooms.findByStatusInOrderByCreatedAtDesc(List.of(Room.Status.WAITING, Room.Status.IN_GAME))
                .stream()
                .map(r -> new Dtos.RoomView(r.getId(), r.getName(), r.getMode(), r.getStatus(),
                        r.getHostUserId(), players.countByRoomId(r.getId()), r.getMaxPlayers()))
                .toList();
    }

    @Transactional
    public Dtos.RoomView createRoom(Long hostUserId, Dtos.CreateRoomRequest req) {
        if (req.name() == null || req.name().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sala necesita un nombre");
        }
        if (req.mode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo de juego invalido");
        }
        Room room = rooms.save(new Room(req.name().trim(), req.mode(), hostUserId));
        players.save(new RoomPlayer(room.getId(), hostUserId));
        broadcastRooms();
        return new Dtos.RoomView(room.getId(), room.getName(), room.getMode(), room.getStatus(),
                hostUserId, 1, room.getMaxPlayers());
    }

    @Transactional
    public Dtos.RoomView join(Long roomId, Long userId) {
        Room room = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La sala no existe"));
        if (room.getStatus() != Room.Status.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala ya esta en partida");
        }
        long count = players.countByRoomId(roomId);
        boolean alreadyIn = players.existsByRoomIdAndUserId(roomId, userId);
        if (!alreadyIn) {
            if (count >= room.getMaxPlayers()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La sala esta llena");
            }
            players.save(new RoomPlayer(roomId, userId));
            count++;
            broadcastRooms();
        }
        return new Dtos.RoomView(room.getId(), room.getName(), room.getMode(), room.getStatus(),
                room.getHostUserId(), count, room.getMaxPlayers());
    }

    /**
     * Saca a un jugador de la sala: salida EXPLICITA (boton "Salir") o al
     * vencer la ventana de reconexion. Si la sala queda vacia, se elimina.
     */
    @Transactional
    public void removePlayer(Long roomId, Long userId) {
        players.deleteByRoomIdAndUserId(roomId, userId);
        if (players.countByRoomId(roomId) == 0) {
            messages.deleteByRoomId(roomId);
            rooms.deleteById(roomId); // sala vacia: se cierra
        }
        broadcastRooms();
    }

    /** Borra la sala completa (solo el CREADOR puede hacerlo). */
    @Transactional
    public void deleteRoom(Long roomId, Long userId) {
        Room room = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La sala no existe"));
        if (!room.getHostUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el creador puede borrar la sala");
        }
        players.deleteByRoomId(roomId);
        messages.deleteByRoomId(roomId);
        rooms.delete(room);
        broadcastRooms();
    }

    /** Difunde la lista de salas a todos los clientes conectados al lobby. */
    public void broadcastRooms() {
        broker.convertAndSend("/topic/rooms", listActiveRooms());
    }
}
