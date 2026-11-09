package com.kitchenai.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "paytr", matchIfMissing = true)
public class PayTrPaymentProvider implements PaymentProvider {
  private final String merchantKey;
  private final String merchantSalt;

  public PayTrPaymentProvider(
      @Value("${PAYTR_MERCHANT_KEY:}") String merchantKey,
      @Value("${PAYTR_MERCHANT_SALT:}") String merchantSalt) {
    this.merchantKey = merchantKey;
    this.merchantSalt = merchantSalt;
  }

  @Override public String providerCode() { return "PAYTR"; }

  // The standard PayTR iFrame callback verifies a payment, but does not by itself
  // establish recurring subscription collection. Keep paid plans disabled until
  // the merchant's recurring product/API contract has been enabled and implemented.
  @Override public boolean supportsRecurringSubscriptions() { return false; }

  @Override
  public CheckoutResult createCheckout(CheckoutCommand command) {
    throw new IllegalStateException(
        "PayTR recurring subscription checkout is not enabled for this merchant configuration.");
  }

  @Override
  public VerifiedPayment verifyCallback(Map<String, String> payload) {
    if (merchantKey.isBlank() || merchantSalt.isBlank()) {
      throw new IllegalStateException("PayTR callback credentials are not configured.");
    }
    String orderId = required(payload, "merchant_oid");
    String status = required(payload, "status");
    String totalAmount = required(payload, "total_amount");
    String receivedHash = required(payload, "hash");
    String source = orderId + merchantSalt + status + totalAmount;
    String expected = hmacBase64(merchantKey, source);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), receivedHash.getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("Invalid PayTR callback signature.");
    }
    return new VerifiedPayment(
        "PAYTR", orderId, orderId, "success".equalsIgnoreCase(status),
        new BigDecimal(totalAmount).movePointLeft(2), "TRY");
  }

  private static String required(Map<String, String> payload, String key) {
    String value = payload.get(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing PayTR field: " + key);
    return value;
  }

  private static String hmacBase64(String key, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not verify PayTR callback.", e);
    }
  }
}
