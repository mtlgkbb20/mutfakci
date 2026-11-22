package com.kitchenai.gamification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class GamificationServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final GamificationService service = new GamificationService(jdbc);
  private final UUID user = UUID.randomUUID();

  // ---- grantXp ----
  @Test
  void firstGrantWritesLedgerAndRecomputesProgressFromTheLedger() {
    when(jdbc.update(contains("INSERT INTO xp_ledger_entry"), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForObject(contains("SUM(xp_delta)"), eq(Integer.class), eq(user))).thenReturn(160);

    assertTrue(service.grantXp(user, 10, "COOKING_SESSION", "ref", "key-1", "reason"));

    verify(jdbc).update(contains("INSERT INTO user_progress"), any(), eq(user), eq(160), eq("LEVEL_3"));
  }

  @Test
  void duplicateIdempotencyKeyDoesNotTouchProgress() {
    when(jdbc.update(contains("INSERT INTO xp_ledger_entry"), any(Object[].class))).thenReturn(0);
    assertFalse(service.grantXp(user, 10, "COOKING_SESSION", "ref", "key-1", "reason"));
    verify(jdbc, never()).update(contains("user_progress"), any(Object[].class));
  }

  @Test
  void negativeLedgerTotalIsStoredAsZeroXp() {
    when(jdbc.update(contains("INSERT INTO xp_ledger_entry"), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForObject(contains("SUM(xp_delta)"), eq(Integer.class), eq(user))).thenReturn(-20);
    service.grantXp(user, -30, "ADJUSTMENT", "ref", "key-2", "reason");
    verify(jdbc).update(contains("INSERT INTO user_progress"), any(), eq(user), eq(0), eq("LEVEL_1"));
  }

  @ParameterizedTest
  @CsvSource(value = {"0,key", "5,", "5,'  '"}, nullValues = "")
  void invalidGrantsAreRejected(int amount, String key) {
    assertThrows(IllegalArgumentException.class, () -> service.grantXp(user, amount, "T", "r", key, "x"));
    verifyNoInteractions(jdbc);
  }

  // ---- badges ----
  @Test
  @SuppressWarnings("unchecked")
  void awardBadgeIsIdempotentAndSkipsInactiveOrUnknownBadges() {
    UUID badge = UUID.randomUUID();
    when(jdbc.query(contains("badge_definition"), any(RowMapper.class), eq("FIRST_DISH"))).thenReturn(List.of(badge));
    when(jdbc.query(contains("badge_definition"), any(RowMapper.class), eq("RETIRED"))).thenReturn(List.of());

    service.awardBadge(user, "FIRST_DISH", "first");
    service.awardBadge(user, "RETIRED", "first");

    verify(jdbc).update(contains("ON CONFLICT(user_id,badge_id,source_key) DO NOTHING"), any(), eq(user), eq(badge), eq("first"));
    verify(jdbc, times(1)).update(contains("user_badge_award"), any(Object[].class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void weeklyRegularBadgeNeedsThreeCompletedSessionsThisWeek() {
    UUID badge = UUID.randomUUID();
    when(jdbc.query(contains("badge_definition"), any(RowMapper.class), eq("WEEKLY_REGULAR"))).thenReturn(List.of(badge));
    when(jdbc.queryForObject(contains("cooking_session"), eq(Integer.class), eq(user), any(), any())).thenReturn(2, 3);

    service.evaluateCookingBadges(user);
    verify(jdbc, never()).update(contains("user_badge_award"), any(Object[].class));

    service.evaluateCookingBadges(user);
    verify(jdbc).update(contains("user_badge_award"), any(), eq(user), eq(badge), eq("week:" + service.currentWeek().start()));
  }

  // ---- weeks & leaderboard ----
  @Test
  void currentWeekStartsOnMondayAndLastsSevenDays() {
    GamificationService.Week week = service.currentWeek();
    assertEquals(DayOfWeek.MONDAY, week.start().getDayOfWeek());
    assertEquals(week.start().plusWeeks(1), week.end());
    LocalDate today = LocalDate.now(GamificationService.ZONE);
    assertFalse(today.isBefore(week.start()));
    assertTrue(today.isBefore(week.end()));
  }

  @ParameterizedTest
  @CsvSource({"-3,0,0,1", "2,500,2,100", "1,20,1,20"})
  void leaderboardClampsPageAndSize(int page, int size, int expectedPage, int expectedSize) {
    when(jdbc.queryForList(anyString(), any(Instant.class), any(Instant.class), anyInt(), anyInt())).thenReturn(List.of());
    Map<String, Object> result = service.leaderboard(page, size);
    assertEquals(expectedPage, result.get("page"));
    assertEquals(expectedSize, result.get("size"));
    assertEquals(0, result.get("total"));
    verify(jdbc).queryForList(anyString(), any(Instant.class), any(Instant.class), eq(expectedPage * expectedSize), eq(expectedSize));
  }

  @Test
  void meExposesConsistentLevelFields() {
    when(jdbc.queryForObject(startsWith("SELECT COALESCE(SUM(xp_delta),0)::int FROM xp_ledger_entry WHERE user_id=?"),
        eq(Integer.class), eq(user))).thenReturn(60);
    when(jdbc.queryForObject(contains("created_at >= ? AND created_at < ?"), eq(Integer.class), eq(user), any(), any())).thenReturn(15);
    when(jdbc.queryForObject(startsWith("WITH weekly"), eq(Integer.class), any(Object[].class))).thenReturn(4);
    when(jdbc.queryForList(anyString(), eq(user))).thenReturn(List.of());

    Map<String, Object> me = service.me(user);

    assertEquals(60, me.get("lifetimeXp"));
    assertEquals(15, me.get("weeklyXp"));
    assertEquals(4, me.get("rank"));
    assertEquals(2, me.get("level"));
    assertEquals("HOME_COOK", me.get("tier"));
    assertEquals(150, me.get("nextLevelMinXp"));
    assertEquals(90, me.get("xpToNextLevel"));
  }

  // ---- settleWeek ----
  private final LocalDate week = LocalDate.of(2026, 11, 16);
  private final UUID snapshot = UUID.randomUUID();

  private void stubSnapshot(String status) {
    when(jdbc.queryForObject(contains("SELECT id FROM weekly_leaderboard_snapshot"), eq(UUID.class), eq(week))).thenReturn(snapshot);
    when(jdbc.queryForObject(contains("FOR UPDATE"), eq(String.class), eq(snapshot))).thenReturn(status);
  }

  @Test
  void settlingAnAlreadySettledWeekIsANoOp() {
    stubSnapshot("SETTLED");
    when(jdbc.queryForObject(contains("weekly_leaderboard_entry"), eq(Integer.class), eq(snapshot))).thenReturn(7);
    assertEquals(Map.of("weekStart", week, "status", "ALREADY_SETTLED", "entries", 7), service.settleWeek(week));
    verify(jdbc, never()).update(contains("INSERT INTO weekly_leaderboard_entry"), any(Object[].class));
    verify(jdbc, never()).update(contains("INSERT INTO xp_ledger_entry"), any(Object[].class));
  }

  @Test
  void settlementRanksUsersAwardsPodiumXpAndGivesTheWinnerAFreeGeneration() {
    stubSnapshot("SETTLING");
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
    List<Map<String, Object>> rows = new ArrayList<>();
    int xp = 100;
    for (UUID id : List.of(a, b, c, d)) rows.add(Map.of("user_id", id, "weekly_xp", xp--, "lifetime_xp", 500));
    when(jdbc.queryForList(contains("weekly_xp"), any(Instant.class), any(Instant.class))).thenReturn(rows);
    when(jdbc.update(contains("INSERT INTO xp_ledger_entry"), any(Object[].class))).thenReturn(1);
    when(jdbc.queryForObject(contains("SUM(xp_delta)"), eq(Integer.class), any(UUID.class))).thenReturn(500);

    Map<String, Object> result = service.settleWeek(week);

    assertEquals("SETTLED", result.get("status"));
    assertEquals(4, result.get("entries"));
    for (var expected : List.of(List.of(a, 50), List.of(b, 30), List.of(c, 20)))
      verify(jdbc).update(contains("INSERT INTO xp_ledger_entry"), any(), eq(expected.get(0)), eq(expected.get(1)),
          eq("WEEKLY_PLACEMENT"), eq(week.toString()), eq("weekly-placement:" + week + ":" + expected.get(0)), anyString());
    verify(jdbc, never()).update(contains("INSERT INTO xp_ledger_entry"), any(), eq(d), anyInt(), any(), any(), any(), any());
    verify(jdbc).update(contains("FREE_RECIPE_GENERATION"), any(), eq(a), eq("week:" + week), any(Instant.class));
    verify(jdbc).update(contains("status='SETTLED'"), eq(snapshot));
  }

  @Test
  void settlingAnEmptyWeekGrantsNoReward() {
    stubSnapshot("SETTLING");
    when(jdbc.queryForList(contains("weekly_xp"), any(Instant.class), any(Instant.class))).thenReturn(List.of());
    assertEquals(0, service.settleWeek(week).get("entries"));
    verify(jdbc, never()).update(contains("gamification_reward_grant"), any(Object[].class));
  }
}
