package com.mortalonline.lobby.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {

    public enum Mode { VERSUS_NORMAL, TEST_YOUR_MIGHT, RANDOM_POWERS }

    public enum Status { WAITING, IN_GAME }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode;

    @Column(nullable = false)
    private Long hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.WAITING;

    @Column(nullable = false)
    private int maxPlayers = 2;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Room() {
    }

    public Room(String name, Mode mode, Long hostUserId) {
        this.name = name;
        this.mode = mode;
        this.hostUserId = hostUserId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Mode getMode() { return mode; }
    public Long getHostUserId() { return hostUserId; }
    public Status getStatus() { return status; }
    public int getMaxPlayers() { return maxPlayers; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(Status status) { this.status = status; }
}
