package com.kitchenai.gamification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for XP level thresholds and level progress.
 *
 * <p>Level {@code n} starts at {@code (n-1) * n * 25} XP (0, 50, 150, 300, ...). The legacy tier
 * names used by the mobile app are derived from the numeric level so that the title, the level
 * number and the progress bar can never disagree.
 */
public record LevelProgress(
    int totalXp,
    int level,
    String tier,
    int currentLevelMinXp,
    Integer nextLevelMinXp,
    int xpIntoLevel,
    Integer xpToNextLevel,
    double progressRatio) {

  public static final int MAX_LEVEL = 100;

  public static LevelProgress of(int rawTotalXp) {
    int total = Math.max(0, rawTotalXp);
    int level = levelFor(total);
    int currentFloor = minXpForLevel(level);
    if (level >= MAX_LEVEL) {
      return new LevelProgress(total, level, tierFor(level), currentFloor, null, total - currentFloor, null, 1.0);
    }
    int nextFloor = minXpForLevel(level + 1);
    double ratio = Math.min(1.0, Math.max(0.0, (double) (total - currentFloor) / (nextFloor - currentFloor)));
    return new LevelProgress(
        total, level, tierFor(level), currentFloor, nextFloor, total - currentFloor, nextFloor - total, ratio);
  }

  public static int levelFor(int xp) {
    int level = 1;
    while (level < MAX_LEVEL && xp >= minXpForLevel(level + 1)) level++;
    return level;
  }

  public static int minXpForLevel(int level) {
    return (level - 1) * level * 25;
  }

  /** Legacy tier code kept for the mobile dashboard/profile contract. */
  public static String tierFor(int level) {
    return switch (level) {
      case 1 -> "BEGINNER_CHEF";
      case 2 -> "HOME_COOK";
      case 3 -> "SKILLED_COOK";
      default -> "KITCHEN_MASTER";
    };
  }

  public static String levelName(int level) {
    return "LEVEL_" + level;
  }

  /** Progress fields shared by {@code /gamification/me} and {@code /gamification/progress}. */
  public Map<String, Object> toFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("currentLevelMinXp", currentLevelMinXp);
    fields.put("nextLevelMinXp", nextLevelMinXp);
    fields.put("xpIntoLevel", xpIntoLevel);
    fields.put("xpToNextLevel", xpToNextLevel);
    fields.put("progressRatio", progressRatio);
    fields.put("levelProgressXp", xpIntoLevel);
    fields.put("nextLevelXp", nextLevelMinXp == null ? null : nextLevelMinXp - currentLevelMinXp);
    return fields;
  }
}
