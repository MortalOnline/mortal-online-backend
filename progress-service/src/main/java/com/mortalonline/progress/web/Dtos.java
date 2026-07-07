package com.mortalonline.progress.web;

import java.util.Set;

/** DTOs del API de progreso. */
public final class Dtos {

    private Dtos() {
    }

    /** Reporte interno de Combat Service al terminar una partida. */
    public record MatchCompletedRequest(Long matchId, Long winnerId, Long loserId) {
    }

    public record ScoreboardEntry(int rank, Long userId, int totalWins, int totalLosses, int currentWinStreak) {
    }

    public record StatsView(Long userId, int totalWins, int totalLosses, int currentWinStreak,
                            Set<String> unlockedCharacters, boolean reptileUnlocked) {
    }
}
