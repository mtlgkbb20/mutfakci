package com.kitchenai.gamification;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GamificationService {
  public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
  private final JdbcTemplate jdbc;

  public GamificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Week(LocalDate start, LocalDate end) {}
  public Week currentWeek() {
    LocalDate today = LocalDate.now(ZONE);
    LocalDate start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    return new Week(start, start.plusWeeks(1));
  }

  @Scheduled(cron = "0 5 0 * * MON", zone = "Europe/Istanbul")
  @Transactional
  public void settlePreviousWeekAutomatically() {
    settleWeek(currentWeek().start().minusWeeks(1));
  }

  public Map<String, Object> me(UUID userId) {
    Week week = currentWeek();
    Integer lifetime = jdbc.queryForObject(
        "SELECT COALESCE(SUM(xp_delta),0)::int FROM xp_ledger_entry WHERE user_id=?",
        Integer.class, userId);
    Integer weekly = jdbc.queryForObject(
        "SELECT COALESCE(SUM(xp_delta),0)::int FROM xp_ledger_entry WHERE user_id=? AND created_at >= ? AND created_at < ?",
        Integer.class, userId, week.start().atStartOfDay(ZONE).toInstant(), week.end().atStartOfDay(ZONE).toInstant());
    int total = Math.max(0, lifetime == null ? 0 : lifetime);
    int weekXp = Math.max(0, weekly == null ? 0 : weekly);
    int level = levelFor(total);
    int currentFloor = xpForLevel(level);
    int nextFloor = xpForLevel(level + 1);
    Integer rank = jdbc.queryForObject(
        "WITH weekly AS (SELECT user_id,SUM(xp_delta)::int AS xp FROM xp_ledger_entry WHERE created_at >= ? AND created_at < ? GROUP BY user_id), me AS (SELECT id,created_at FROM app_user WHERE id=?) SELECT COUNT(*)::int + 1 FROM weekly w JOIN app_user u ON u.id=w.user_id CROSS JOIN me WHERE w.xp > ? OR (w.xp = ? AND (u.created_at < me.created_at OR (u.created_at = me.created_at AND u.id < me.id)))",
        Integer.class, week.start().atStartOfDay(ZONE).toInstant(), week.end().atStartOfDay(ZONE).toInstant(), userId, weekXp, weekXp);
    List<Map<String,Object>> recent = jdbc.queryForList(
        "SELECT b.code,b.name,b.description,b.icon,b.category,a.awarded_at AS awardedAt FROM user_badge_award a JOIN badge_definition b ON b.id=a.badge_id WHERE a.user_id=? AND a.revoked_at IS NULL AND a.awarded_at >= now() - interval '30 days' ORDER BY a.awarded_at DESC",
        userId);
    List<Map<String,Object>> rewards = jdbc.queryForList(
        "SELECT id,reward_type AS rewardType,status,issued_at AS issuedAt,expires_at AS expiresAt FROM gamification_reward_grant WHERE user_id=? AND status='AVAILABLE' AND expires_at > now() ORDER BY issued_at",
        userId);
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("lifetimeXp", total);
    result.put("weeklyXp", weekXp);
    result.put("rank", rank);
    result.put("level", level);
    result.put("levelProgressXp", total - currentFloor);
    result.put("nextLevelXp", nextFloor - currentFloor);
    result.put("weekStart", week.start());
    result.put("weekEnd", week.end());
    result.put("recentBadges", recent);
    result.put("availableRewards", rewards);
    return result;
  }

  public Map<String,Object> leaderboard(int page, int size) {
    Week week = currentWeek();
    int safePage = Math.max(0, page);
    int safeSize = Math.min(100, Math.max(1, size));
    var rows = jdbc.queryForList(
        "WITH weekly AS (SELECT user_id, SUM(xp_delta)::int AS weekly_xp FROM xp_ledger_entry WHERE created_at >= ? AND created_at < ? GROUP BY user_id HAVING SUM(xp_delta) > 0), lifetime AS (SELECT user_id, SUM(xp_delta)::int AS lifetime_xp FROM xp_ledger_entry GROUP BY user_id) SELECT row_number() OVER (ORDER BY w.weekly_xp DESC, u.created_at ASC, u.id ASC)::int AS rank, u.id AS userId, u.display_name AS displayName, w.weekly_xp AS weeklyXp, COALESCE(l.lifetime_xp,0)::int AS lifetimeXp, badge.icon AS badgeIcon, badge.name AS badgeName, badge.description AS badgeDescription FROM weekly w JOIN app_user u ON u.id=w.user_id LEFT JOIN lifetime l ON l.user_id=w.user_id LEFT JOIN LATERAL (SELECT b.icon,b.name,b.description FROM user_badge_award a JOIN badge_definition b ON b.id=a.badge_id WHERE a.user_id=u.id AND a.revoked_at IS NULL AND a.awarded_at >= now() - interval '30 days' ORDER BY a.awarded_at DESC LIMIT 1) badge ON TRUE ORDER BY w.weekly_xp DESC, u.created_at ASC, u.id ASC OFFSET ? LIMIT ?",
        week.start().atStartOfDay(ZONE).toInstant(), week.end().atStartOfDay(ZONE).toInstant(), safePage * safeSize, safeSize);
    Integer total = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT user_id)::int FROM xp_ledger_entry WHERE created_at >= ? AND created_at < ?",
        Integer.class, week.start().atStartOfDay(ZONE).toInstant(), week.end().atStartOfDay(ZONE).toInstant());
    return Map.of("weekStart", week.start(), "weekEnd", week.end(), "page", safePage, "size", safeSize,
        "total", total == null ? 0 : total, "entries", rows);
  }

  public List<Map<String,Object>> badgeHistory(UUID userId) {
    return jdbc.queryForList(
        "SELECT b.code,b.name,b.description,b.icon,b.category,a.source_key AS sourceKey,a.awarded_at AS awardedAt FROM user_badge_award a JOIN badge_definition b ON b.id=a.badge_id WHERE a.user_id=? AND a.revoked_at IS NULL ORDER BY a.awarded_at DESC",
        userId);
  }

  @Transactional
  public boolean grantXp(UUID userId, int amount, String sourceType, String sourceReference, String idempotencyKey, String reason) {
    if (amount == 0 || idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Invalid XP grant");
    int inserted = jdbc.update(
        "INSERT INTO xp_ledger_entry(id,user_id,xp_delta,source_type,source_reference,idempotency_key,reason) VALUES (?,?,?,?,?,?,?) ON CONFLICT (idempotency_key) DO NOTHING",
        UUID.randomUUID(), userId, amount, sourceType, sourceReference, idempotencyKey, reason);
    if (inserted == 1) {
      int total = jdbc.queryForObject("SELECT COALESCE(SUM(xp_delta),0)::int FROM xp_ledger_entry WHERE user_id=?", Integer.class, userId);
      jdbc.update("INSERT INTO user_progress(id,user_id,total_xp,current_level,updated_at) VALUES (?,?,?,?,now()) ON CONFLICT(user_id) DO UPDATE SET total_xp=EXCLUDED.total_xp,current_level=EXCLUDED.current_level,updated_at=now()",
          UUID.randomUUID(), userId, Math.max(0,total), levelName(levelFor(Math.max(0,total))));
      return true;
    }
    return false;
  }

  @Transactional
  public void evaluateCookingBadges(UUID userId) {
    Week week=currentWeek();
    Integer count=jdbc.queryForObject(
      "SELECT COUNT(*)::int FROM cooking_session WHERE user_id=? AND status='COMPLETED' AND completed_at >= ? AND completed_at < ?",
      Integer.class,userId,week.start().atStartOfDay(ZONE).toInstant(),week.end().atStartOfDay(ZONE).toInstant());
    if(count != null && count >= 3) awardBadge(userId,"WEEKLY_REGULAR","week:"+week.start());
  }

  @Transactional
  public void awardBadge(UUID userId, String code, String sourceKey) {
    var ids = jdbc.query("SELECT id FROM badge_definition WHERE code=? AND active=true",
        (rs,n)->rs.getObject(1, UUID.class), code);
    if (ids.isEmpty()) return;
    jdbc.update("INSERT INTO user_badge_award(id,user_id,badge_id,source_key) VALUES (?,?,?,?) ON CONFLICT(user_id,badge_id,source_key) DO NOTHING",
        UUID.randomUUID(), userId, ids.get(0), sourceKey);
  }

  @Transactional
  public Map<String,Object> settleWeek(LocalDate weekStart) {
    LocalDate weekEnd = weekStart.plusWeeks(1);
    UUID snapshotId = UUID.randomUUID();
    jdbc.update("INSERT INTO weekly_leaderboard_snapshot(id,week_start,week_end,status) VALUES (?,?,?,'SETTLING') ON CONFLICT(week_start) DO NOTHING",
        snapshotId, weekStart, weekEnd);
    UUID actualId = jdbc.queryForObject("SELECT id FROM weekly_leaderboard_snapshot WHERE week_start=?", UUID.class, weekStart);
    String settlementStatus = jdbc.queryForObject("SELECT status FROM weekly_leaderboard_snapshot WHERE id=? FOR UPDATE", String.class, actualId);
    if ("SETTLED".equals(settlementStatus)) {
      Integer count = jdbc.queryForObject("SELECT COUNT(*)::int FROM weekly_leaderboard_entry WHERE snapshot_id=?", Integer.class, actualId);
      return Map.of("weekStart",weekStart,"status","ALREADY_SETTLED","entries",count == null ? 0 : count);
    }
    var entries = jdbc.queryForList(
        "WITH w AS (SELECT user_id,SUM(xp_delta)::int weekly_xp FROM xp_ledger_entry WHERE created_at >= ? AND created_at < ? GROUP BY user_id HAVING SUM(xp_delta)>0), l AS (SELECT user_id,SUM(xp_delta)::int lifetime_xp FROM xp_ledger_entry GROUP BY user_id) SELECT w.user_id,w.weekly_xp,COALESCE(l.lifetime_xp,0)::int lifetime_xp FROM w LEFT JOIN l ON l.user_id=w.user_id JOIN app_user u ON u.id=w.user_id ORDER BY w.weekly_xp DESC,u.created_at ASC,u.id ASC",
        weekStart.atStartOfDay(ZONE).toInstant(), weekEnd.atStartOfDay(ZONE).toInstant());
    int rank = 0;
    for (var entry : entries) {
      rank++;
      UUID uid = (UUID) entry.get("user_id");
      int xp = ((Number)entry.get("weekly_xp")).intValue();
      int lifetimeXp = ((Number)entry.get("lifetime_xp")).intValue();
      jdbc.update("INSERT INTO weekly_leaderboard_entry(id,snapshot_id,user_id,rank_position,weekly_xp,lifetime_xp) VALUES (?,?,?,?,?,?) ON CONFLICT(snapshot_id,user_id) DO NOTHING",
          UUID.randomUUID(),actualId,uid,rank,xp,lifetimeXp);
      if (rank <= 3) {
        int bonus = rank == 1 ? 50 : rank == 2 ? 30 : 20;
        grantXp(uid, bonus, "WEEKLY_PLACEMENT", weekStart.toString(),
            "weekly-placement:"+weekStart+":"+uid, "Haftalık leaderboard "+rank+". sıra");
        awardBadge(uid,"PODIUM_FINISH","week:"+weekStart);
      }
    }
    if (!entries.isEmpty()) {
      UUID winner = (UUID) entries.get(0).get("user_id");
      jdbc.update("INSERT INTO gamification_reward_grant(id,user_id,reward_type,source_key,expires_at) VALUES (?,?, 'FREE_RECIPE_GENERATION', ?, ?) ON CONFLICT(user_id,reward_type,source_key) DO NOTHING",
          UUID.randomUUID(),winner,"week:"+weekStart,Instant.now().plus(Duration.ofDays(30)));
    }
    jdbc.update("UPDATE weekly_leaderboard_snapshot SET status='SETTLED',settled_at=now() WHERE id=?",actualId);
    return Map.of("weekStart",weekStart,"status","SETTLED","entries",entries.size());
  }

  public static int levelFor(int xp) {
    int level=1;
    while (xp >= xpForLevel(level+1) && level < 100) level++;
    return level;
  }
  private static int xpForLevel(int level) { return (level-1)*level*25; }
  private static String levelName(int level) { return "LEVEL_"+level; }
}
