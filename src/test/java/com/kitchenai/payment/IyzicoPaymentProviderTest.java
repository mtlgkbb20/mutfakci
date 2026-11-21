package com.kitchenai.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IyzicoPaymentProviderTest {
  private final IyzicoPaymentProvider provider = new IyzicoPaymentProvider();

  @Test
  void unconfiguredAdapterNeverPretendsToTakePayments() {
    assertEquals("IYZICO", provider.providerCode());
    assertFalse(provider.supportsRecurringSubscriptions());
    assertThrows(IllegalStateException.class, () -> provider.createCheckout(new PaymentProvider.CheckoutCommand(
        UUID.randomUUID(), "a@b.c", "COOKER_MONTHLY", BigDecimal.TEN, "TRY", "o", "cb", "ok", "fail")));
    assertThrows(IllegalStateException.class, () -> provider.verifyCallback(Map.of()));
    assertThrows(UnsupportedOperationException.class, () -> provider.chargeRecurring(null));
    assertThrows(UnsupportedOperationException.class, () -> provider.cancelSubscription(null));
  }
}
