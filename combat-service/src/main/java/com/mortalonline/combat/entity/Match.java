package com.mortalonline.combat.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long player1Id;

    @Column(nullable = false)
    private Long player2Id;

    private Long winnerId;

    @Column(nullable = false)
    private int roundsWonP1;

    @Column(nullable = false)
    private int roundsWonP2;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    protected Match() {
    }

    public Match(Long roomId, Long player1Id, Long player2Id, Long winnerId,
                 int roundsWonP1, int roundsWonP2, Instant startedAt, Instant endedAt) {
        this.roomId = roomId;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.winnerId = winnerId;
        this.roundsWonP1 = roundsWonP1;
        this.roundsWonP2 = roundsWonP2;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getPlayer1Id() { return player1Id; }
    public Long getPlayer2Id() { return player2Id; }
    public Long getWinnerId() { return winnerId; }
    public int getRoundsWonP1() { return roundsWonP1; }
    public int getRoundsWonP2() { return roundsWonP2; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}
