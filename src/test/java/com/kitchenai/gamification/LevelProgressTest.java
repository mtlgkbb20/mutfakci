package com.kitchenai.gamification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class LevelProgressTest {

  @ParameterizedTest(name = "{0} XP -> level {1} ({2})")
  @CsvSource({
    "0,   1, BEGINNER_CHEF,  0,   50,  0,  50",
    "49,  1, BEGINNER_CHEF,  0,   50,  49, 1",
    "50,  2, HOME_COOK,      50,  150, 0,  100",
    "149, 2, HOME_COOK,      50,  150, 99, 1",
    "150, 3, SKILLED_COOK,   150, 300, 0,  150",
    "299, 3, SKILLED_COOK,   150, 300, 149, 1",
    "300, 4, KITCHEN_MASTER, 300, 500, 0,  200",
  })
  void thresholdsAreConsistentAcrossLevelTierAndProgress(
      int xp, int level, String tier, int floor, int next, int into, int toNext) {
    LevelProgress p = LevelProgress.of(xp);
    assertEquals(level, p.level());
    assertEquals(tier, p.tier());
    assertEquals(floor, p.currentLevelMinXp());
    assertEquals(next, p.nextLevelMinXp());
    assertEquals(into, p.xpIntoLevel());
    assertEquals(toNext, p.xpToNextLevel());
    assertEquals((double) into / (next - floor), p.progressRatio(), 1e-9);
  }

  @Test
  void negativeXpIsClampedToZero() {
    LevelProgress p = LevelProgress.of(-40);
    assertEquals(0, p.totalXp());
    assertEquals(1, p.level());
    assertEquals(0.0, p.progressRatio());
  }

  @Test
  void maxLevelHasNoNextLevelAndFullProgress() {
    int maxFloor = LevelProgress.minXpForLevel(LevelProgress.MAX_LEVEL);
    LevelProgress p = LevelProgress.of(maxFloor + 10_000);
    assertEquals(LevelProgress.MAX_LEVEL, p.level());
    assertNull(p.nextLevelMinXp());
    assertNull(p.xpToNextLevel());
    assertEquals(1.0, p.progressRatio());
    assertNull(p.toFields().get("nextLevelXp"));
  }

  @Test
  void levelJustBelowMaxStillReportsNextLevel() {
    LevelProgress p = LevelProgress.of(LevelProgress.minXpForLevel(LevelProgress.MAX_LEVEL) - 1);
    assertEquals(LevelProgress.MAX_LEVEL - 1, p.level());
    assertEquals(1, p.xpToNextLevel());
  }

  @Test
  void userProgressEntityUsesSameThresholds() {
    UserProgress entity = new UserProgress(java.util.UUID.randomUUID());
    assertEquals("LEVEL_1", entity.getCurrentLevel());
    entity.add(49);
    assertEquals("LEVEL_1", entity.getCurrentLevel());
    entity.add(1);
    assertEquals("LEVEL_2", entity.getCurrentLevel());
    entity.add(250);
    assertEquals("LEVEL_4", entity.getCurrentLevel());
  }
}
