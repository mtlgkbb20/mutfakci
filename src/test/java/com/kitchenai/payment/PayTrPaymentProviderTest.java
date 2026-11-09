package com.kitchenai.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PayTrPaymentProviderTest {
  @Test
  void verifiesValidCallbackAndConvertsKurusToTry() throws Exception {
    String key = "test-merchant-key";
    String salt = "test-merchant-salt";
    String orderId = "ORDER123";
    String status = "success";
    String amount = "1299";
    String source = orderId + salt + status + amount;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String hash = Base64.getEncoder().encodeToString(
        mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));

    var provider = new PayTrPaymentProvider(
        "merchant-id", key, salt, "", true, new ObjectMapper());
    var verified = provider.verifyCallback(Map.of(
        "merchant_oid", orderId,
        "status", status,
        "total_amount", amount,
        "currency", "TL",
        "hash", hash));

    assertTrue(verified.successful());
    assertEquals("12.99", verified.paidAmount().toPlainString());
    assertEquals("TRY", verified.currency());
    assertEquals(orderId, verified.merchantOrderId());
    assertTrue(provider.isCheckoutConfigured());
  }

  @Test
  void rejectsInvalidCallbackHash() {
    var provider = new PayTrPaymentProvider(
        "merchant-id", "merchant-key", "merchant-salt", "", true, new ObjectMapper());
    assertThrows(IllegalArgumentException.class, () -> provider.verifyCallback(Map.of(
        "merchant_oid", "ORDER123",
        "status", "success",
        "total_amount", "12990",
        "hash", "invalid")));
  }

  @Test
  void doesNotPretendIframeCheckoutIsAnAutoRenewingSubscription() {
    var provider = new PayTrPaymentProvider(
        "merchant-id", "merchant-key", "merchant-salt", "", true, new ObjectMapper());
    assertFalse(provider.supportsRecurringSubscriptions());
  }

  @Test
  void doesNotEnableCheckoutWithoutMerchantCredentials() {
    var provider = new PayTrPaymentProvider(
        "", "", "", "", true, new ObjectMapper());
    assertFalse(provider.isCheckoutConfigured());
  }
}
