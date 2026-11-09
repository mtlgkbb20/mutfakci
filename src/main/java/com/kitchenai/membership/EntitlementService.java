package com.kitchenai.membership;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");
  private final JdbcTemplate jdbc;
  private final Clock clock = Clock.systemUTC();

  public EntitlementService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record QuotaReservation(UUID userId, String periodKey) {}

  public Map<String, Object> summary(UUID userId) {
    ensureMembership(userId);
    var row = jdbc.queryForMap(
        "SELECT tier, billing_interval, status, paid_until, provider FROM user_membership WHERE user_id = ?",
        userId);
    String tier = (String) row.get("tier");
    String interval = (String) row.get("billing_interval");
    String status = (String) row.get("status");
    Instant paidUntil = row.get("paid_until") == null
        ? null
        : jdbc.queryForObject("SELECT paid_until FROM user_membership WHERE user_id = ?",
            (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), userId);
    boolean paid = "COOKER".equals(tier) && "ACTIVE".equals(status)
        && paidUntil != null && paidUntil.isAfter(clock.instant());
    if ("COOKER".equals(tier) && !paid) {
      jdbc.update("UPDATE user_membership SET tier='FREE', billing_interval='NONE', status='EXPIRED', paid_until=NULL, updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
      tier = "FREE"; interval = "NONE"; status = "EXPIRED";
    }
    String period = "COOKER".equals(tier) && paid
        ? "COOKER_DAILY:" + LocalDate.now(clock.withZone(BUSINESS_ZONE))
        : "FREE_LIFETIME";
    int limit = "COOKER".equals(tier) && paid ? 10 : 3;
    int used = usage(userId, period);
    Instant resetAt = "COOKER".equals(tier) && paid
        ? LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        : null;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tier", tier);
    result.put("billingInterval", interval);
    result.put("subscriptionStatus", status);
    result.put("paidAccess", paid);
    result.put("usage", used);
    result.put("limit", limit);
    result.put("remaining", Math.max(0, limit - used));
    result.put("period", "COOKER".equals(tier) && paid ? "DAILY" : "LIFETIME");
    result.put("resetAt", resetAt);
    result.put("provider", row.get("provider"));
    result.put("plans", List.of(
        Map.of("code", "FREE", "tier", "FREE", "billingInterval", "NONE", "limit", 3, "period", "LIFETIME", "available", true),
        Map.of("code", "COOKER_MONTHLY", "tier", "COOKER", "billingInterval", "MONTHLY", "limit", 10, "period", "DAILY", "available", false),
        Map.of("code", "COOKER_YEARLY", "tier", "COOKER", "billingInterval", "YEARLY", "limit", 10, "period", "DAILY", "available", false),
        Map.of("code", "CHEF", "tier", "CHEF", "available", false, "comingSoon", true)
    ));
    return result;
  }

  @Transactional
  public QuotaReservation reserve(UUID userId) {
    ensureMembership(userId);
    // Serialize quota mutations per account; this prevents concurrent requests from
    // passing the same remaining slot, on PostgreSQL and H2 alike.
    String tier = jdbc.queryForObject(
        "SELECT tier FROM user_membership WHERE user_id=? FOR UPDATE", String.class, userId);
    String period = "COOKER".equals(tier)
        ? "COOKER_DAILY:" + LocalDate.now(clock.withZone(BUSINESS_ZONE))
        : "FREE_LIFETIME";
    int limit = "COOKER".equals(tier) ? 10 : 3;
    List<Integer> current = jdbc.query(
        "SELECT used_count FROM recipe_quota_usage WHERE user_id=? AND period_key=?",
        (rs, n) -> rs.getInt(1), userId, period);
    int used = current.isEmpty() ? 0 : current.get(0);
    if (used >= limit) return null;
    if (current.isEmpty()) {
      jdbc.update(
          "INSERT INTO recipe_quota_usage(user_id, period_key, used_count, updated_at) VALUES (?, ?, 1, CURRENT_TIMESTAMP)",
          userId, period);
    } else {
      jdbc.update(
          "UPDATE recipe_quota_usage SET used_count=used_count+1, updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND period_key=?",
          userId, period);
    }
    return new QuotaReservation(userId, period);
  }

  @Transactional
  public void release(QuotaReservation reservation) {
    if (reservation == null) return;
    jdbc.update(
        "UPDATE recipe_quota_usage SET used_count=CASE WHEN used_count > 0 THEN used_count-1 ELSE 0 END, updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND period_key=?",
        reservation.userId(), reservation.periodKey());
  }

  @Transactional
  public void ensureMembership(UUID userId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM user_membership WHERE user_id=?", Integer.class, userId);
    if (count == null || count == 0) {
      jdbc.update(
          "INSERT INTO user_membership(user_id, tier, billing_interval, status, created_at, updated_at) VALUES (?, 'FREE', 'NONE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
          userId);
    }
  }

  private int usage(UUID userId, String period) {
    List<Integer> values = jdbc.query(
        "SELECT used_count FROM recipe_quota_usage WHERE user_id=? AND period_key=?",
        (rs, n) -> rs.getInt(1), userId, period);
    return values.isEmpty() ? 0 : values.get(0);
  }
}
