package com.mortalonline.progress.service;

import com.mortalonline.progress.entity.PlayerStats;
import com.mortalonline.progress.repository.PlayerStatsRepository;
import com.mortalonline.progress.web.Dtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de progreso:
 *  - victoria: totalWins+1 y racha+1; al llegar la racha a 3, se desbloquea
 *    Reptile para ese usuario.
 *  - derrota: totalLosses+1 y la racha vuelve a 0.
 */
@Service
public class ProgressService {

    public static final String REPTILE = "reptile";

    private final PlayerStatsRepository stats;
    private final int reptileUnlockStreak;

    public ProgressService(PlayerStatsRepository stats,
                           @Value("${progress.reptile-unlock-streak:3}") int reptileUnlockStreak) {
        this.stats = stats;
        this.reptileUnlockStreak = reptileUnlockStreak;
    }

    @Transactional
    public void matchCompleted(Dtos.MatchCompletedRequest req) {
        if (req.winnerId() == null || req.loserId() == null || req.winnerId().equals(req.loserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultado de partida invalido");
        }
        PlayerStats winner = stats.findById(req.winnerId()).orElseGet(() -> new PlayerStats(req.winnerId()));
        PlayerStats loser = stats.findById(req.loserId()).orElseGet(() -> new PlayerStats(req.loserId()));

        winner.registerWin();
        if (winner.getCurrentWinStreak() >= reptileUnlockStreak) {
            winner.unlockCharacter(REPTILE);
        }
        loser.registerLoss();

        stats.save(winner);
        stats.save(loser);
    }

    @Transactional(readOnly = true)
    public List<Dtos.ScoreboardEntry> scoreboard() {
        List<Dtos.ScoreboardEntry> out = new ArrayList<>();
        int rank = 1;
        for (PlayerStats s : stats.findTop20ByOrderByTotalWinsDescCurrentWinStreakDesc()) {
            out.add(new Dtos.ScoreboardEntry(rank++, s.getUserId(), s.getTotalWins(),
                    s.getTotalLosses(), s.getCurrentWinStreak()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Dtos.StatsView statsOf(Long userId) {
        PlayerStats s = stats.findById(userId).orElseGet(() -> new PlayerStats(userId));
        return new Dtos.StatsView(userId, s.getTotalWins(), s.getTotalLosses(), s.getCurrentWinStreak(),
                s.getUnlockedCharacters(), s.getUnlockedCharacters().contains(REPTILE));
    }
}
