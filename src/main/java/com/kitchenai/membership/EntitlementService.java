package com.kitchenai.membership;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public EntitlementService(JdbcTemplate jdbc, @Value("${app.membership.clock-zone:Europe/Istanbul}") String ignoredZone) {
    this.jdbc = jdbc;
    this.clock = Clock.systemUTC();
  }

  public Map<String, Object> summary(UUID userId) {
    ensureMembership(userId);
    var row = jdbc.queryForMap(
        "SELECT tier, billing_interval, status, paid_until FROM user_membership WHERE user_id = ?",
        userId);
    String tier = (String) row.get("tier");
    String interval = (String) row.get("billing_interval");
    String status = (String) row.get("status");
    Instant paidUntil = row.get("paid_until") == null ? null : ((Timestamp) row.get("paid_until")).toInstant();
    boolean paid = "COOKER".equals(tier) && "ACTIVE".equals(status)
        && (paidUntil == null || paidUntil.isAfter(clock.instant()));
    if (!paid && "COOKER".equals(tier)) {
      jdbc.update("UPDATE user_membership SET tier='FREE', billing_interval='NONE', status='ACTIVE', paid_until=NULL WHERE user_id=?", userId);
      tier = "FREE"; interval = "NONE"; status = "ACTIVE";
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
  public boolean reserve(UUID userId) {
    ensureMembership(userId);
    String tier = jdbc.queryForObject("SELECT tier FROM user_membership WHERE user_id=?", String.class, userId);
    String period = "COOKER".equals(tier)
        ? "COOKER_DAILY:" + LocalDate.now(clock.withZone(BUSINESS_ZONE))
        : "FREE_LIFETIME";
    int limit = "COOKER".equals(tier) ? 10 : 3;
    int changed = jdbc.update(
        "INSERT INTO recipe_quota_usage(user_id, period_key, used_count, updated_at) VALUES (?, ?, 1, now()) " +
        "ON CONFLICT (user_id, period_key) DO UPDATE SET used_count=recipe_quota_usage.used_count+1, updated_at=now() " +
        "WHERE recipe_quota_usage.used_count < ?",
        userId, period, limit);
    return changed == 1;
  }

  @Transactional
  public void release(UUID userId) {
    String tier = jdbc.queryForObject("SELECT tier FROM user_membership WHERE user_id=?", String.class, userId);
    String period = "COOKER".equals(tier)
        ? "COOKER_DAILY:" + LocalDate.now(clock.withZone(BUSINESS_ZONE))
        : "FREE_LIFETIME";
    jdbc.update(
        "UPDATE recipe_quota_usage SET used_count=GREATEST(0, used_count-1), updated_at=now() WHERE user_id=? AND period_key=?",
        userId, period);
  }

  public void ensureMembership(UUID userId) {
    jdbc.update(
        "INSERT INTO user_membership(user_id, tier, billing_interval, status, created_at, updated_at) " +
        "VALUES (?, 'FREE', 'NONE', 'ACTIVE', now(), now()) ON CONFLICT (user_id) DO NOTHING",
        userId);
  }

  private int usage(UUID userId, String period) {
    List<Integer> values = jdbc.query(
        "SELECT used_count FROM recipe_quota_usage WHERE user_id=? AND period_key=?",
        (rs, n) -> rs.getInt(1), userId, period);
    return values.isEmpty() ? 0 : values.get(0);
  }
}
