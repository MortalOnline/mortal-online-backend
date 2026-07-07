package com.mortalonline.combat.repository;

import com.mortalonline.combat.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
}
