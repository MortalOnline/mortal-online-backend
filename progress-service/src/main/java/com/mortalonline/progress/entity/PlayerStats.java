package com.mortalonline.progress.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "player_stats")
public class PlayerStats {

    /** Personajes disponibles para todos desde el inicio. */
    public static final Set<String> DEFAULT_CHARACTERS = Set.of("scorpion", "subzero", "johnnycage");

    @Id
    private Long userId;

    @Column(nullable = false)
    private int totalWins;

    @Column(nullable = false)
    private int totalLosses;

    @Column(nullable = false)
    private int currentWinStreak;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_unlocked_characters", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "character_id", length = 30)
    private Set<String> unlockedCharacters = new HashSet<>(DEFAULT_CHARACTERS);

    protected PlayerStats() {
    }

    public PlayerStats(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
    public int getTotalWins() { return totalWins; }
    public int getTotalLosses() { return totalLosses; }
    public int getCurrentWinStreak() { return currentWinStreak; }
    public Set<String> getUnlockedCharacters() { return unlockedCharacters; }

    public void registerWin() {
        totalWins++;
        currentWinStreak++;
    }

    public void registerLoss() {
        totalLosses++;
        currentWinStreak = 0; // al perder, la racha vuelve a 0
    }

    public void unlockCharacter(String characterId) {
        unlockedCharacters.add(characterId);
    }
}
