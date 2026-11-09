package com.kitchenai.payment;

import static org.junit.jupiter.api.Assertions.*;
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
    String amount = "12990";
    String source = orderId + salt + status + amount;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String hash = Base64.getEncoder().encodeToString(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));

    var verified = new PayTrPaymentProvider(key, salt).verifyCallback(Map.of(
        "merchant_oid", orderId, "status", status, "total_amount", amount, "hash", hash));

    assertTrue(verified.successful());
    assertEquals("12.99", verified.paidAmount().toPlainString());
    assertEquals(orderId, verified.merchantOrderId());
  }

  @Test
  void rejectsInvalidCallbackHash() {
    var provider = new PayTrPaymentProvider("merchant-key", "merchant-salt");
    assertThrows(IllegalArgumentException.class, () -> provider.verifyCallback(Map.of(
        "merchant_oid", "ORDER123", "status", "success", "total_amount", "12990", "hash", "invalid")));
  }

  @Test
  void doesNotPretendStandardCallbackIsRecurringCheckout() {
    assertFalse(new PayTrPaymentProvider("", "").supportsRecurringSubscriptions());
  }
}
