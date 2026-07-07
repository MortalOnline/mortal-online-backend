package com.mortalonline.lobby.web;

import com.mortalonline.lobby.entity.Room;

import java.time.Instant;

/** DTOs del API de lobby. */
public final class Dtos {

    private Dtos() {
    }

    public record CreateRoomRequest(String name, Room.Mode mode) {
    }

    public record RoomView(Long id, String name, Room.Mode mode, Room.Status status,
                           Long hostUserId, long players, int maxPlayers) {
    }

    /** Mensaje entrante de chat (STOMP /app/rooms/{id}/chat). */
    public record ChatIn(String content) {
    }

    /** Mensaje difundido de chat (/topic/rooms/{id}/chat). */
    public record ChatOut(Long roomId, Long userId, String username, String content, Instant sentAt) {
    }
}
