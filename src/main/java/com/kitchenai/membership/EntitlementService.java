package com.kitchenai.membership;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
public class EntitlementService {

  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

  private final JdbcTemplate jdbc;
  private final Clock clock = Clock.systemUTC();

  public EntitlementService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record QuotaReservation(UUID userId, String periodKey) {}

  private record MembershipState(String tier, String status, Instant paidUntil) {}

  public Map<String, Object> summary(UUID userId) {
    ensureMembership(userId);

    var membership = jdbc.queryForMap(
        """
        SELECT tier, billing_interval, status, paid_until, provider
        FROM user_membership
        WHERE user_id = ?
        """,
        userId);

    String tier = (String) membership.get("tier");
    String interval = (String) membership.get("billing_interval");
    String status = (String) membership.get("status");

    Instant paidUntil = jdbc.queryForObject(
        """
        SELECT paid_until
        FROM user_membership
        WHERE user_id = ?
        """,
        (resultSet, rowNumber) ->
            resultSet.getTimestamp(1) == null
                ? null
                : resultSet.getTimestamp(1).toInstant(),
        userId);

    boolean paidAccess =
        "COOKER".equals(tier)
            && "ACTIVE".equals(status)
            && paidUntil != null
            && paidUntil.isAfter(clock.instant());

    if ("COOKER".equals(tier) && !paidAccess) {
      jdbc.update(
          """
          UPDATE user_membership
          SET tier = 'FREE',
              billing_interval = 'NONE',
              status = 'EXPIRED',
              paid_until = NULL,
              updated_at = CURRENT_TIMESTAMP
          WHERE user_id = ?
          """,
          userId);

      tier = "FREE";
      interval = "NONE";
      status = "EXPIRED";
    }

    String period =
        "COOKER".equals(tier) && paidAccess
            ? "COOKER_DAILY:" + LocalDate.now(clock.withZone(BUSINESS_ZONE))
            : "FREE_LIFETIME";

    int limit =
        "CHEF".equals(tier)
            ? 0
            : "COOKER".equals(tier) && paidAccess
                ? 10
                : 3;

    int used = usage(userId, period);

    Integer promotionalCredits = jdbc.queryForObject(
        """
        SELECT COUNT(*)::int
        FROM gamification_reward_grant
        WHERE user_id = ?
          AND status = 'AVAILABLE'
          AND expires_at > ?
        """,
        Integer.class,
        userId,
        clock.instant());

    Instant resetAt =
        "COOKER".equals(tier) && paidAccess
            ? LocalDate.now(clock.withZone(BUSINESS_ZONE))
                .plusDays(1)
                .atStartOfDay(BUSINESS_ZONE)
                .toInstant()
            : null;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tier", tier);
    result.put("billingInterval", interval);
    result.put("subscriptionStatus", status);
    result.put("paidAccess", paidAccess);
    result.put("usage", used);
    result.put("limit", limit);
    result.put(
        "remaining",
        Math.max(0, limit - used)
            + (promotionalCredits == null ? 0 : promotionalCredits));
    result.put(
        "promotionalCredits",
        promotionalCredits == null ? 0 : promotionalCredits);
    result.put(
        "period",
        "COOKER".equals(tier) && paidAccess ? "DAILY" : "LIFETIME");
    result.put("resetAt", resetAt);
    result.put("provider", membership.get("provider"));
    result.put(
        "plans",
        List.of(
            Map.of(
                "code", "FREE",
                "tier", "FREE",
                "billingInterval", "NONE",
                "limit", 3,
                "period", "LIFETIME",
                "available", true),
            Map.of(
                "code", "COOKER_MONTHLY",
                "tier", "COOKER",
                "billingInterval", "MONTHLY",
                "limit", 10,
                "period", "DAILY",
                "available", false),
            Map.of(
                "code", "COOKER_YEARLY",
                "tier", "COOKER",
                "billingInterval", "YEARLY",
                "limit", 10,
                "period", "DAILY",
                "available", false),
            Map.of(
                "code", "CHEF",
                "tier", "CHEF",
                "available", false,
                "comingSoon", true)));

    return result;
  }

  @Transactional
  public QuotaReservation reserve(UUID userId) {
    ensureMembership(userId);

    // Serialize quota mutations per account to prevent concurrent requests
    // from consuming the same remaining slot.
    MembershipState membership = jdbc.queryForObject(
        """
        SELECT tier, status, paid_until
        FROM user_membership
        WHERE user_id = ?
        FOR UPDATE
        """,
        (resultSet, rowNumber) ->
            new MembershipState(
                resultSet.getString("tier"),
                resultSet.getString("status"),
                resultSet.getTimestamp("paid_until") == null
                    ? null
                    : resultSet.getTimestamp("paid_until").toInstant()),
        userId);

    String tier = membership.tier();
    boolean cookerActive =
        "COOKER".equals(tier)
            && "ACTIVE".equals(membership.status())
            && membership.paidUntil() != null
            && membership.paidUntil().isAfter(clock.instant());

    if ("COOKER".equals(tier) && !cookerActive) {
      jdbc.update(
          """
          UPDATE user_membership
          SET tier = 'FREE',
              billing_interval = 'NONE',
              status = 'EXPIRED',
              paid_until = NULL,
              updated_at = CURRENT_TIMESTAMP
          WHERE user_id = ?
          """,
          userId);
      tier = "FREE";
    }

    List<UUID> rewardIds = jdbc.query(
        """
        SELECT id
        FROM gamification_reward_grant
        WHERE user_id = ?
          AND status = 'AVAILABLE'
          AND expires_at > ?
        ORDER BY issued_at
        LIMIT 1
        FOR UPDATE
        """,
        (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
        userId,
        clock.instant());

    if (!rewardIds.isEmpty()) {
      UUID rewardId = rewardIds.get(0);

      jdbc.update(
          """
          UPDATE gamification_reward_grant
          SET status = 'RESERVED', reserved_at = ?
          WHERE id = ? AND status = 'AVAILABLE'
          """,
          clock.instant(),
          rewardId);

      return new QuotaReservation(userId, "PROMO_REWARD:" + rewardId);
    }

    String period =
        cookerActive
            ? "COOKER_DAILY:" + LocalDate.now(clock.withZone(BUSINESS_ZONE))
            : "FREE_LIFETIME";

    if ("CHEF".equals(tier)) {
      return null;
    }

    int limit = cookerActive ? 10 : 3;

    List<Integer> currentUsage = jdbc.query(
        """
        SELECT used_count
        FROM recipe_quota_usage
        WHERE user_id = ? AND period_key = ?
        """,
        (resultSet, rowNumber) -> resultSet.getInt(1),
        userId,
        period);

    int used = currentUsage.isEmpty() ? 0 : currentUsage.get(0);
    if (used >= limit) {
      return null;
    }

    if (currentUsage.isEmpty()) {
      jdbc.update(
          """
          INSERT INTO recipe_quota_usage (
            user_id, period_key, used_count, updated_at
          )
          VALUES (?, ?, 1, CURRENT_TIMESTAMP)
          """,
          userId,
          period);
    } else {
      jdbc.update(
          """
          UPDATE recipe_quota_usage
          SET used_count = used_count + 1,
              updated_at = CURRENT_TIMESTAMP
          WHERE user_id = ? AND period_key = ?
          """,
          userId,
          period);
    }

    return new QuotaReservation(userId, period);
  }

  @Transactional
  public void commit(QuotaReservation reservation) {
    if (reservation == null
        || !reservation.periodKey().startsWith("PROMO_REWARD:")) {
      return;
    }

    UUID rewardId =
        UUID.fromString(
            reservation.periodKey().substring("PROMO_REWARD:".length()));

    jdbc.update(
        """
        UPDATE gamification_reward_grant
        SET status = 'REDEEMED',
            redeemed_at = ?,
            reserved_at = NULL
        WHERE id = ?
          AND user_id = ?
          AND status = 'RESERVED'
        """,
        clock.instant(),
        rewardId,
        reservation.userId());
  }

  @Transactional
  public void release(QuotaReservation reservation) {
    if (reservation == null) {
      return;
    }

    if (reservation.periodKey().startsWith("PROMO_REWARD:")) {
      UUID rewardId =
          UUID.fromString(
              reservation.periodKey().substring("PROMO_REWARD:".length()));

      jdbc.update(
          """
          UPDATE gamification_reward_grant
          SET status = 'AVAILABLE', reserved_at = NULL
          WHERE id = ?
            AND user_id = ?
            AND status = 'RESERVED'
            AND expires_at > ?
          """,
          rewardId,
          reservation.userId(),
          clock.instant());

      return;
    }

    jdbc.update(
        """
        UPDATE recipe_quota_usage
        SET used_count = CASE
              WHEN used_count > 0 THEN used_count - 1
              ELSE 0
            END,
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = ? AND period_key = ?
        """,
        reservation.userId(),
        reservation.periodKey());
  }

  @Scheduled(fixedDelay = 300_000)
  @Transactional
  public void releaseStalePromoReservations() {
    jdbc.update(
        """
        UPDATE gamification_reward_grant
        SET status = 'AVAILABLE', reserved_at = NULL
        WHERE status = 'RESERVED'
          AND reserved_at < ?
          AND expires_at > ?
        """,
        clock.instant().minus(Duration.ofHours(1)),
        clock.instant());
  }

  @Transactional
  public void ensureMembership(UUID userId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM user_membership WHERE user_id = ?",
        Integer.class,
        userId);

    if (count == null || count == 0) {
      jdbc.update(
          """
          INSERT INTO user_membership (
            user_id, tier, billing_interval, status, created_at, updated_at
          )
          VALUES (?, 'FREE', 'NONE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """,
          userId);
    }
  }

  private int usage(UUID userId, String period) {
    List<Integer> values = jdbc.query(
        """
        SELECT used_count
        FROM recipe_quota_usage
        WHERE user_id = ? AND period_key = ?
        """,
        (resultSet, rowNumber) -> resultSet.getInt(1),
        userId,
        period);

    return values.isEmpty() ? 0 : values.get(0);
  }
}
