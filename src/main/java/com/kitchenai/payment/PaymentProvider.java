package com.kitchenai.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface PaymentProvider {
  String providerCode();

  boolean supportsRecurringSubscriptions();

  CheckoutResult createCheckout(CheckoutCommand command);

  VerifiedPayment verifyCallback(Map<String, String> payload);

  default VerifiedPayment chargeRecurring(RecurringChargeCommand command) {
    throw new UnsupportedOperationException(
        providerCode() + " recurring charges are not configured.");
  }

  default void cancelSubscription(CancelSubscriptionCommand command) {
    throw new UnsupportedOperationException(
        providerCode() + " subscription cancellation is not configured.");
  }

  record CheckoutCommand(
      UUID userId,
      String email,
      String displayName,
      String address,
      String phone,
      String userIp,
      String planCode,
      BigDecimal amount,
      String currency,
      String merchantOrderId,
      String callbackUrl,
      String successUrl,
      String failureUrl) {}

  record RecurringChargeCommand(
      UUID userId,
      String providerCustomerToken,
      String providerCardToken,
      String merchantOrderId,
      BigDecimal amount,
      String currency) {}

  record CancelSubscriptionCommand(
      UUID userId,
      String providerSubscriptionId,
      boolean atPeriodEnd) {}

  record CheckoutResult(
      String provider,
      String checkoutUrl,
      String checkoutToken,
      String reference) {}

  record VerifiedPayment(
      String provider,
      String eventId,
      String merchantOrderId,
      boolean successful,
      BigDecimal paidAmount,
      String currency) {}
}
