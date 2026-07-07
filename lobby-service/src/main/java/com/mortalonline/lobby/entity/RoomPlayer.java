package com.mortalonline.lobby.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "room_players", uniqueConstraints = @UniqueConstraint(columnNames = {"roomId", "userId"}))
public class RoomPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected RoomPlayer() {
    }

    public RoomPlayer(Long roomId, Long userId) {
        this.roomId = roomId;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getUserId() { return userId; }
    public Instant getJoinedAt() { return joinedAt; }
}
