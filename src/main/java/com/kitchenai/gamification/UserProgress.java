package com.kitchenai.gamification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class UserProgress {
  @Id private UUID id;
  private UUID userId;
  private int totalXp;
  private String currentLevel;
  private Instant updatedAt;

  public UserProgress() {}

  public UserProgress(UUID u) {
    id = UUID.randomUUID();
    userId = u;
    currentLevel = LevelProgress.levelName(1);
    updatedAt = Instant.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public int getTotalXp() {
    return totalXp;
  }

  public String getCurrentLevel() {
    return currentLevel;
  }

  public void add(int xp) {
    totalXp += xp;
    currentLevel = LevelProgress.levelName(LevelProgress.levelFor(Math.max(0, totalXp)));
    updatedAt = Instant.now();
  }
}
