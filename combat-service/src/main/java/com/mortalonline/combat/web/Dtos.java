package com.mortalonline.combat.web;

import com.mortalonline.combat.entity.Match;
import com.mortalonline.combat.entity.MatchRound;

import java.time.Instant;
import java.util.List;

/** DTOs del API de combate. */
public final class Dtos {

    private Dtos() {
    }

    public record StartMatchRequest(Long roomId, Long player1Id, Long player2Id) {
    }

    public record ActiveMatchView(Long roomId, Long player1Id, Long player2Id, Instant startedAt) {
    }

    public record MatchView(Long id, Long roomId, Long player1Id, Long player2Id, Long winnerId,
                            int roundsWonP1, int roundsWonP2, Instant startedAt, Instant endedAt,
                            List<RoundView> rounds) {
        public static MatchView of(Match m, List<MatchRound> rounds) {
            return new MatchView(m.getId(), m.getRoomId(), m.getPlayer1Id(), m.getPlayer2Id(), m.getWinnerId(),
                    m.getRoundsWonP1(), m.getRoundsWonP2(), m.getStartedAt(), m.getEndedAt(),
                    rounds.stream().map(r -> new RoundView(r.getRoundNumber(), r.getWinnerId(), r.getDurationSeconds())).toList());
        }
    }

    public record RoundView(int roundNumber, Long winnerId, int durationSeconds) {
    }

    /** Input de combate: se retransmite tal cual (relay de baja latencia). */
    public record CombatInput(Object input) {
    }

    public record RoundEnd(int roundNumber, Long winnerId, int durationSeconds) {
    }

    public record MatchEnd(Long winnerId) {
    }
}
