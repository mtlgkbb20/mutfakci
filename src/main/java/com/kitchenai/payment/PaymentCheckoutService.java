package com.kitchenai.payment;

import com.kitchenai.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCheckoutService {
  private final JdbcTemplate jdbc;
  private final PaymentProvider provider;
  private final boolean checkoutEnabled;
  private final boolean testMode;
  private final boolean livePaymentsEnabled;
  private final String callbackUrl;
  private final String returnUrl;
  private final BigDecimal monthlyPrice;
  private final BigDecimal yearlyPrice;

  public PaymentCheckoutService(
      JdbcTemplate jdbc,
      PaymentProvider provider,
      @Value("${app.payments.checkout-enabled:false}") boolean checkoutEnabled,
      @Value("${app.payments.test-mode:true}") boolean testMode,
      @Value("${app.payments.live-enabled:false}") boolean livePaymentsEnabled,
      @Value("${app.payments.callback-url:}") String callbackUrl,
      @Value("${app.payments.return-url:http://localhost:5173/}") String returnUrl,
      @Value("${app.membership.pricing.cooker-monthly:0}") BigDecimal monthlyPrice,
      @Value("${app.membership.pricing.cooker-yearly:0}") BigDecimal yearlyPrice) {
    this.jdbc = jdbc;
    this.provider = provider;
    this.checkoutEnabled = checkoutEnabled;
    this.testMode = testMode;
    this.livePaymentsEnabled = livePaymentsEnabled;
    this.callbackUrl = callbackUrl;
    this.returnUrl = returnUrl;
    this.monthlyPrice = monthlyPrice;
    this.yearlyPrice = yearlyPrice;
  }

  public List<Map<String, Object>> planCatalog() {
    return List.of(
        plan("FREE", "FREE", "NONE", BigDecimal.ZERO, true, false),
        plan("COOKER_MONTHLY", "COOKER", "MONTHLY", monthlyPrice, false, false),
        plan("COOKER_YEARLY", "COOKER", "YEARLY", yearlyPrice, false, false),
        plan("CHEF", "CHEF", "NONE", BigDecimal.ZERO, false, true));
  }

  public Map<String, Object> createCheckout(
      UUID userId,
      String planCode,
      String displayName,
      String address,
      String phone,
      String userIp) {
    if (!checkoutEnabled) {
      throw unavailable("Payment checkout is disabled. Enable PAYMENT_CHECKOUT_ENABLED first.");
    }
    if (!testMode && !livePaymentsEnabled) {
      throw unavailable("Live payments are locked. Explicitly enable PAYMENT_LIVE_ENABLED to proceed.");
    }
    if (!callbackUrl.startsWith("https://")) {
      throw unavailable("PAYMENT_CALLBACK_URL must be a public HTTPS callback endpoint.");
    }
    if (returnUrl.isBlank()) {
      throw unavailable("PAYMENT_RETURN_URL must be configured.");
    }

    BigDecimal amount = priceFor(planCode);
    if (amount.signum() <= 0) {
      throw unavailable("A positive server-side price must be configured for this plan.");
    }

    String email = jdbc.queryForObject(
        "SELECT email FROM app_user WHERE id = ?", String.class, userId);
    String orderId = UUID.randomUUID().toString().replace("-", "").toUpperCase();
    Instant expiresAt = Instant.now().plusSeconds(30 * 60L);

    jdbc.update("""
        INSERT INTO payment_checkout (
            id, merchant_order_id, user_id, plan_code, amount, currency,
            status, provider, expires_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, 'TRY', 'PENDING', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID(),
        orderId,
        userId,
        planCode,
        amount,
        provider.providerCode(),
        Timestamp.from(expiresAt));

    try {
      PaymentProvider.CheckoutResult result = provider.createCheckout(
          new PaymentProvider.CheckoutCommand(
              userId,
              email,
              displayName,
              address,
              phone,
              userIp,
              planCode,
              amount,
              "TRY",
              orderId,
              callbackUrl,
              returnUrl + (returnUrl.contains("?") ? "&" : "?") + "payment=success&order=" + orderId,
              returnUrl + (returnUrl.contains("?") ? "&" : "?") + "payment=failed&order=" + orderId));

      jdbc.update("""
          UPDATE payment_checkout
          SET status = 'CHECKOUT_CREATED', provider_reference = ?, updated_at = CURRENT_TIMESTAMP
          WHERE merchant_order_id = ? AND status = 'PENDING'
          """,
          result.reference(), orderId);

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("orderId", orderId);
      response.put("checkoutUrl", result.checkoutUrl());
      response.put("amount", amount);
      response.put("currency", "TRY");
      response.put("planCode", planCode);
      response.put("status", "CHECKOUT_CREATED");
      response.put("testMode", testMode);
      response.put("autoRenew", false);
      return response;
    } catch (RuntimeException exception) {
      jdbc.update("""
          UPDATE payment_checkout
          SET status = 'INITIALIZATION_FAILED', updated_at = CURRENT_TIMESTAMP
          WHERE merchant_order_id = ? AND status = 'PENDING'
          """, orderId);
      throw exception;
    }
  }

  public Map<String, Object> checkoutStatus(UUID userId, String orderId) {
    var rows = jdbc.queryForList("""
        SELECT merchant_order_id, plan_code, amount, currency, status, expires_at, paid_amount
        FROM payment_checkout
        WHERE merchant_order_id = ? AND user_id = ?
        """, orderId, userId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Checkout not found.");
    }
    Map<String, Object> row = rows.get(0);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", row.get("merchant_order_id"));
    response.put("planCode", row.get("plan_code"));
    response.put("amount", row.get("amount"));
    response.put("currency", row.get("currency"));
    response.put("status", row.get("status"));
    response.put("expiresAt", row.get("expires_at"));
    response.put("paidAmount", row.get("paid_amount"));
    return response;
  }

  @Transactional
  public void handleCallback(Map<String, String> payload) {
    PaymentProvider.VerifiedPayment payment = provider.verifyCallback(payload);
    var rows = jdbc.queryForList("""
        SELECT user_id, plan_code, amount, currency, status,
               (expires_at > CURRENT_TIMESTAMP) AS not_expired
        FROM payment_checkout
        WHERE merchant_order_id = ?
        FOR UPDATE
        """, payment.merchantOrderId());
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("Unknown PayTR merchant order.");
    }

    Map<String, Object> checkout = rows.get(0);
    String currentStatus = (String) checkout.get("status");
    if ("PAID".equals(currentStatus) || "FAILED".equals(currentStatus)
        || "AMOUNT_MISMATCH".equals(currentStatus) || "EXPIRED".equals(currentStatus)) {
      return;
    }

    String eventStatus = payment.successful() ? "SUCCESS" : "FAILED";
    int inserted = jdbc.update("""
        INSERT INTO payment_event (
            id, provider, provider_event_id, merchant_reference, event_status, received_at
        ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (provider, provider_event_id) DO NOTHING
        """,
        UUID.randomUUID(),
        payment.provider(),
        payment.eventId(),
        payment.merchantOrderId(),
        eventStatus);
    if (inserted == 0) {
      return;
    }

    BigDecimal expectedAmount =
        ((BigDecimal) checkout.get("amount")).setScale(2, RoundingMode.HALF_UP);
    boolean amountMatches = expectedAmount.compareTo(payment.paidAmount()) == 0;
    boolean currencyMatches = "TRY".equalsIgnoreCase(payment.currency())
        && "TRY".equalsIgnoreCase((String) checkout.get("currency"));
    if (payment.successful() && (!amountMatches || !currencyMatches)) {
      jdbc.update("""
          UPDATE payment_checkout
          SET status = 'AMOUNT_MISMATCH', paid_amount = ?, updated_at = CURRENT_TIMESTAMP
          WHERE merchant_order_id = ?
          """, payment.paidAmount(), payment.merchantOrderId());
      return;
    }

    if (!payment.successful()) {
      jdbc.update("""
          UPDATE payment_checkout
          SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP
          WHERE merchant_order_id = ?
          """, payment.merchantOrderId());
      return;
    }

    if (!Boolean.TRUE.equals(checkout.get("not_expired"))) {
      jdbc.update("""
          UPDATE payment_checkout
          SET status = 'EXPIRED', paid_amount = ?, updated_at = CURRENT_TIMESTAMP
          WHERE merchant_order_id = ?
          """, payment.paidAmount(), payment.merchantOrderId());
      return;
    }

    String planCode = (String) checkout.get("plan_code");
    String billingInterval = "COOKER_YEARLY".equals(planCode) ? "YEARLY" : "MONTHLY";
    jdbc.update("""
        UPDATE payment_checkout
        SET status = 'PAID', paid_amount = ?, updated_at = CURRENT_TIMESTAMP
        WHERE merchant_order_id = ?
        """, payment.paidAmount(), payment.merchantOrderId());

    jdbc.update("""
        UPDATE user_membership
        SET tier = 'COOKER',
            billing_interval = ?,
            status = 'ACTIVE',
            provider = 'PAYTR',
            provider_customer_id = NULL,
            provider_subscription_id = NULL,
            paid_until = CASE
                WHEN ? = 'YEARLY'
                    THEN GREATEST(COALESCE(paid_until, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP) + INTERVAL '1 year'
                ELSE GREATEST(COALESCE(paid_until, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP) + INTERVAL '1 month'
            END,
            cancel_at_period_end = FALSE,
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = ?
        """, billingInterval, billingInterval, checkout.get("user_id"));
  }

  private Map<String, Object> plan(
      String code,
      String tier,
      String billingInterval,
      BigDecimal price,
      boolean freePlan,
      boolean comingSoon) {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("code", code);
    plan.put("tier", tier);
    plan.put("billingInterval", billingInterval);
    plan.put("price", price);
    plan.put("available",
        freePlan || (!comingSoon && checkoutEnabled && price.signum() > 0
            && (testMode || livePaymentsEnabled)));
    plan.put("comingSoon", comingSoon);
    plan.put("autoRenew", false);
    return plan;
  }

  private BigDecimal priceFor(String planCode) {
    return switch (planCode) {
      case "COOKER_MONTHLY" -> monthlyPrice;
      case "COOKER_YEARLY" -> yearlyPrice;
      default -> throw new ApiException(
          HttpStatus.BAD_REQUEST, "This membership plan is not purchasable.");
    };
  }

  private static ApiException unavailable(String message) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
