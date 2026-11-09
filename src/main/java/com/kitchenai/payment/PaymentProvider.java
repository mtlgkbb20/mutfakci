package com.kitchenai.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface PaymentProvider {
  String providerCode();
  boolean supportsRecurringSubscriptions();
  CheckoutResult createCheckout(CheckoutCommand command);
  VerifiedPayment verifyCallback(Map<String, String> payload);

  record CheckoutCommand(
      UUID userId, String email, String planCode, BigDecimal amount, String currency,
      String merchantOrderId, String callbackUrl, String successUrl, String failureUrl) {}

  record CheckoutResult(String provider, String checkoutUrl, String checkoutToken, String reference) {}

  record VerifiedPayment(
      String provider, String eventId, String merchantOrderId, boolean successful,
      BigDecimal paidAmount, String currency) {}
}
