package com.mortalonline.combat.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "match_rounds")
public class MatchRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private int roundNumber;

    @Column(nullable = false)
    private Long winnerId;

    @Column(nullable = false)
    private int durationSeconds;

    protected MatchRound() {
    }

    public MatchRound(Long matchId, int roundNumber, Long winnerId, int durationSeconds) {
        this.matchId = matchId;
        this.roundNumber = roundNumber;
        this.winnerId = winnerId;
        this.durationSeconds = durationSeconds;
    }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public int getRoundNumber() { return roundNumber; }
    public Long getWinnerId() { return winnerId; }
    public int getDurationSeconds() { return durationSeconds; }
}
