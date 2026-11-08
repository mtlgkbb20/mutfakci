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
    currentLevel = "BEGINNER_CHEF";
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
    currentLevel =
        totalXp >= 100
            ? "KITCHEN_MASTER"
            : totalXp >= 60 ? "SKILLED_COOK" : totalXp >= 30 ? "HOME_COOK" : "BEGINNER_CHEF";
    updatedAt = Instant.now();
  }
}
