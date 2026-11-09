package com.kitchenai.gamification;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GamificationService {

  public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

  private final JdbcTemplate jdbc;

  public GamificationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Week(LocalDate start, LocalDate end) {}

  public Week currentWeek() {
    LocalDate today = LocalDate.now(ZONE);
    LocalDate start =
        today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    return new Week(start, start.plusWeeks(1));
  }

  @Scheduled(cron = "0 5 0 * * MON", zone = "Europe/Istanbul")
  @Transactional
  public void settlePreviousWeekAutomatically() {
    settleWeek(currentWeek().start().minusWeeks(1));
  }

  public Map<String, Object> me(UUID userId) {
    Week week = currentWeek();

    Integer lifetimeXp = jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(xp_delta), 0)::int
        FROM xp_ledger_entry
        WHERE user_id = ?
        """,
        Integer.class,
        userId);

    Integer weeklyXp = jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(xp_delta), 0)::int
        FROM xp_ledger_entry
        WHERE user_id = ?
          AND created_at >= ?
          AND created_at < ?
        """,
        Integer.class,
        userId,
        week.start().atStartOfDay(ZONE).toInstant(),
        week.end().atStartOfDay(ZONE).toInstant());

    int total = Math.max(0, lifetimeXp == null ? 0 : lifetimeXp);
    int weekTotal = Math.max(0, weeklyXp == null ? 0 : weeklyXp);
    int level = levelFor(total);
    int currentFloor = xpForLevel(level);
    int nextFloor = xpForLevel(level + 1);

    Integer rank = jdbc.queryForObject(
        """
        WITH weekly AS (
          SELECT user_id, SUM(xp_delta) AS xp
          FROM xp_ledger_entry
          WHERE created_at >= ? AND created_at < ?
          GROUP BY user_id
        ),
        current_user AS (
          SELECT id, created_at
          FROM app_user
          WHERE id = ?
        )
        SELECT COUNT(*)::int + 1
        FROM weekly
        JOIN app_user ON app_user.id = weekly.user_id
        CROSS JOIN current_user
        WHERE weekly.xp > ?
           OR (
             weekly.xp = ?
             AND (
               app_user.created_at < current_user.created_at
               OR (
                 app_user.created_at = current_user.created_at
                 AND app_user.id < current_user.id
               )
             )
           )
        """,
        Integer.class,
        week.start().atStartOfDay(ZONE).toInstant(),
        week.end().atStartOfDay(ZONE).toInstant(),
        userId,
        weekTotal,
        weekTotal);

    List<Map<String, Object>> recentBadges = jdbc.queryForList(
        """
        SELECT
          badge.code,
          badge.name,
          badge.description,
          badge.icon,
          badge.category,
          award.awarded_at AS awardedAt
        FROM user_badge_award award
        JOIN badge_definition badge ON badge.id = award.badge_id
        WHERE award.user_id = ?
          AND award.revoked_at IS NULL
          AND award.awarded_at >= now() - interval '30 days'
        ORDER BY award.awarded_at DESC
        """,
        userId);

    List<Map<String, Object>> availableRewards = jdbc.queryForList(
        """
        SELECT
          id,
          reward_type AS rewardType,
          status,
          issued_at AS issuedAt,
          expires_at AS expiresAt
        FROM gamification_reward_grant
        WHERE user_id = ?
          AND status = 'AVAILABLE'
          AND expires_at > now()
        ORDER BY issued_at
        """,
        userId);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("lifetimeXp", total);
    result.put("weeklyXp", weekTotal);
    result.put("rank", rank);
    result.put("level", level);
    result.put("levelProgressXp", total - currentFloor);
    result.put("nextLevelXp", nextFloor - currentFloor);
    result.put("weekStart", week.start());
    result.put("weekEnd", week.end());
    result.put("recentBadges", recentBadges);
    result.put("availableRewards", availableRewards);

    return result;
  }

  public Map<String, Object> leaderboard(int page, int size) {
    Week week = currentWeek();
    int safePage = Math.max(0, page);
    int safeSize = Math.min(100, Math.max(1, size));

    var entries = jdbc.queryForList(
        """
        WITH weekly AS (
          SELECT user_id, SUM(xp_delta)::int AS weekly_xp
          FROM xp_ledger_entry
          WHERE created_at >= ? AND created_at < ?
          GROUP BY user_id
          HAVING SUM(xp_delta) > 0
        ),
        lifetime AS (
          SELECT user_id, SUM(xp_delta)::int AS lifetime_xp
          FROM xp_ledger_entry
          GROUP BY user_id
        )
        SELECT
          ROW_NUMBER() OVER (
            ORDER BY weekly.weekly_xp DESC, app_user.created_at ASC, app_user.id ASC
          )::int AS rank,
          app_user.id AS userId,
          app_user.display_name AS displayName,
          weekly.weekly_xp AS weeklyXp,
          COALESCE(lifetime.lifetime_xp, 0)::int AS lifetimeXp,
          recent_badge.icon AS badgeIcon,
          recent_badge.name AS badgeName,
          recent_badge.description AS badgeDescription
        FROM weekly
        JOIN app_user ON app_user.id = weekly.user_id
        LEFT JOIN lifetime ON lifetime.user_id = weekly.user_id
        LEFT JOIN LATERAL (
          SELECT badge.icon, badge.name, badge.description
          FROM user_badge_award award
          JOIN badge_definition badge ON badge.id = award.badge_id
          WHERE award.user_id = app_user.id
            AND award.revoked_at IS NULL
            AND award.awarded_at >= now() - interval '30 days'
          ORDER BY award.awarded_at DESC
          LIMIT 1
        ) recent_badge ON TRUE
        ORDER BY weekly.weekly_xp DESC, app_user.created_at ASC, app_user.id ASC
        OFFSET ? LIMIT ?
        """,
        week.start().atStartOfDay(ZONE).toInstant(),
        week.end().atStartOfDay(ZONE).toInstant(),
        safePage * safeSize,
        safeSize);

    Integer total = jdbc.queryForObject(
        """
        SELECT COUNT(DISTINCT user_id)::int
        FROM xp_ledger_entry
        WHERE created_at >= ? AND created_at < ?
        """,
        Integer.class,
        week.start().atStartOfDay(ZONE).toInstant(),
        week.end().atStartOfDay(ZONE).toInstant());

    return Map.of(
        "weekStart", week.start(),
        "weekEnd", week.end(),
        "page", safePage,
        "size", safeSize,
        "total", total == null ? 0 : total,
        "entries", entries);
  }

  public List<Map<String, Object>> badgeHistory(UUID userId) {
    return jdbc.queryForList(
        """
        SELECT
          badge.code,
          badge.name,
          badge.description,
          badge.icon,
          badge.category,
          award.source_key AS sourceKey,
          award.awarded_at AS awardedAt
        FROM user_badge_award award
        JOIN badge_definition badge ON badge.id = award.badge_id
        WHERE award.user_id = ?
          AND award.revoked_at IS NULL
        ORDER BY award.awarded_at DESC
        """,
        userId);
  }

  @Transactional
  public boolean grantXp(
      UUID userId,
      int amount,
      String sourceType,
      String sourceReference,
      String idempotencyKey,
      String reason) {

    if (amount == 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Invalid XP grant");
    }

    int inserted = jdbc.update(
        """
        INSERT INTO xp_ledger_entry (
          id, user_id, xp_delta, source_type, source_reference,
          idempotency_key, reason
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (idempotency_key) DO NOTHING
        """,
        UUID.randomUUID(),
        userId,
        amount,
        sourceType,
        sourceReference,
        idempotencyKey,
        reason);

    if (inserted != 1) {
      return false;
    }

    Integer currentTotal = jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(xp_delta), 0)::int
        FROM xp_ledger_entry
        WHERE user_id = ?
        """,
        Integer.class,
        userId);

    int total = Math.max(0, currentTotal == null ? 0 : currentTotal);

    jdbc.update(
        """
        INSERT INTO user_progress (
          id, user_id, total_xp, current_level, updated_at
        )
        VALUES (?, ?, ?, ?, now())
        ON CONFLICT (user_id) DO UPDATE SET
          total_xp = EXCLUDED.total_xp,
          current_level = EXCLUDED.current_level,
          updated_at = now()
        """,
        UUID.randomUUID(),
        userId,
        total,
        levelName(levelFor(total)));

    return true;
  }

  @Transactional
  public void evaluateCookingBadges(UUID userId) {
    Week week = currentWeek();

    Integer completedSessions = jdbc.queryForObject(
        """
        SELECT COUNT(*)::int
        FROM cooking_session
        WHERE user_id = ?
          AND status = 'COMPLETED'
          AND completed_at >= ?
          AND completed_at < ?
        """,
        Integer.class,
        userId,
        week.start().atStartOfDay(ZONE).toInstant(),
        week.end().atStartOfDay(ZONE).toInstant());

    if (completedSessions != null && completedSessions >= 3) {
      awardBadge(userId, "WEEKLY_REGULAR", "week:" + week.start());
    }
  }

  @Transactional
  public void awardBadge(UUID userId, String code, String sourceKey) {
    List<UUID> badgeIds = jdbc.query(
        """
        SELECT id
        FROM badge_definition
        WHERE code = ? AND active = TRUE
        """,
        (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
        code);

    if (badgeIds.isEmpty()) {
      return;
    }

    jdbc.update(
        """
        INSERT INTO user_badge_award (id, user_id, badge_id, source_key)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (user_id, badge_id, source_key) DO NOTHING
        """,
        UUID.randomUUID(),
        userId,
        badgeIds.get(0),
        sourceKey);
  }

  @Transactional
  public Map<String, Object> settleWeek(LocalDate weekStart) {
    LocalDate weekEnd = weekStart.plusWeeks(1);
    UUID snapshotId = UUID.randomUUID();

    jdbc.update(
        """
        INSERT INTO weekly_leaderboard_snapshot (
          id, week_start, week_end, status
        )
        VALUES (?, ?, ?, 'SETTLING')
        ON CONFLICT (week_start) DO NOTHING
        """,
        snapshotId,
        weekStart,
        weekEnd);

    UUID actualSnapshotId = jdbc.queryForObject(
        """
        SELECT id
        FROM weekly_leaderboard_snapshot
        WHERE week_start = ?
        """,
        UUID.class,
        weekStart);

    String settlementStatus = jdbc.queryForObject(
        """
        SELECT status
        FROM weekly_leaderboard_snapshot
        WHERE id = ?
        FOR UPDATE
        """,
        String.class,
        actualSnapshotId);

    if ("SETTLED".equals(settlementStatus)) {
      Integer existingCount = jdbc.queryForObject(
          """
          SELECT COUNT(*)::int
          FROM weekly_leaderboard_entry
          WHERE snapshot_id = ?
          """,
          Integer.class,
          actualSnapshotId);

      return Map.of(
          "weekStart", weekStart,
          "status", "ALREADY_SETTLED",
          "entries", existingCount == null ? 0 : existingCount);
    }

    var entries = jdbc.queryForList(
        """
        WITH weekly AS (
          SELECT user_id, SUM(xp_delta)::int AS weekly_xp
          FROM xp_ledger_entry
          WHERE created_at >= ? AND created_at < ?
          GROUP BY user_id
          HAVING SUM(xp_delta) > 0
        ),
        lifetime AS (
          SELECT user_id, SUM(xp_delta)::int AS lifetime_xp
          FROM xp_ledger_entry
          GROUP BY user_id
        )
        SELECT
          weekly.user_id,
          weekly.weekly_xp,
          COALESCE(lifetime.lifetime_xp, 0)::int AS lifetime_xp
        FROM weekly
        LEFT JOIN lifetime ON lifetime.user_id = weekly.user_id
        JOIN app_user ON app_user.id = weekly.user_id
        ORDER BY weekly.weekly_xp DESC, app_user.created_at ASC, app_user.id ASC
        """,
        weekStart.atStartOfDay(ZONE).toInstant(),
        weekEnd.atStartOfDay(ZONE).toInstant());

    int rank = 0;

    for (var entry : entries) {
      rank++;

      UUID userId = (UUID) entry.get("user_id");
      int weeklyXp = ((Number) entry.get("weekly_xp")).intValue();
      int lifetime = ((Number) entry.get("lifetime_xp")).intValue();

      jdbc.update(
          """
          INSERT INTO weekly_leaderboard_entry (
            id, snapshot_id, user_id, rank_position, weekly_xp, lifetime_xp
          )
          VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT (snapshot_id, user_id) DO NOTHING
          """,
          UUID.randomUUID(),
          actualSnapshotId,
          userId,
          rank,
          weeklyXp,
          lifetime);

      if (rank <= 3) {
        int bonusXp = rank == 1 ? 50 : rank == 2 ? 30 : 20;

        grantXp(
            userId,
            bonusXp,
            "WEEKLY_PLACEMENT",
            weekStart.toString(),
            "weekly-placement:" + weekStart + ":" + userId,
            "Haftalık leaderboard " + rank + ". sıra");

        awardBadge(userId, "PODIUM_FINISH", "week:" + weekStart);
      }
    }

    if (!entries.isEmpty()) {
      UUID winnerId = (UUID) entries.get(0).get("user_id");

      jdbc.update(
          """
          INSERT INTO gamification_reward_grant (
            id, user_id, reward_type, source_key, expires_at
          )
          VALUES (?, ?, 'FREE_RECIPE_GENERATION', ?, ?)
          ON CONFLICT (user_id, reward_type, source_key) DO NOTHING
          """,
          UUID.randomUUID(),
          winnerId,
          "week:" + weekStart,
          Instant.now().plus(Duration.ofDays(30)));
    }

    jdbc.update(
        """
        UPDATE weekly_leaderboard_snapshot
        SET status = 'SETTLED', settled_at = now()
        WHERE id = ?
        """,
        actualSnapshotId);

    return Map.of(
        "weekStart", weekStart,
        "status", "SETTLED",
        "entries", entries.size());
  }

  public static int levelFor(int xp) {
    int level = 1;

    while (xp >= xpForLevel(level + 1) && level < 100) {
      level++;
    }

    return level;
  }

  private static int xpForLevel(int level) {
    return (level - 1) * level * 25;
  }

  private static String levelName(int level) {
    return "LEVEL_" + level;
  }
}
