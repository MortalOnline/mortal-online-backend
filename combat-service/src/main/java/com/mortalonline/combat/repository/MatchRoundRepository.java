package com.mortalonline.combat.repository;

import com.mortalonline.combat.entity.MatchRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRoundRepository extends JpaRepository<MatchRound, Long> {
    List<MatchRound> findByMatchIdOrderByRoundNumber(Long matchId);
}
