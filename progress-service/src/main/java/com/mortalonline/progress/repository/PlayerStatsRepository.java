package com.mortalonline.progress.repository;

import com.mortalonline.progress.entity.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerStatsRepository extends JpaRepository<PlayerStats, Long> {
    List<PlayerStats> findTop20ByOrderByTotalWinsDescCurrentWinStreakDesc();
}
