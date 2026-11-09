package com.kitchenai.payment;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "iyzico")
public class IyzicoPaymentProvider implements PaymentProvider {
  @Override
  public String providerCode() {
    return "IYZICO";
  }

  @Override
  public boolean supportsRecurringSubscriptions() {
    return false;
  }

  @Override
  public boolean isCheckoutConfigured() {
    return false;
  }

  @Override
  public CheckoutResult createCheckout(CheckoutCommand command) {
    throw new IllegalStateException(
        "iyzico adapter is selected but its merchant credentials and subscription API integration are not configured.");
  }

  @Override
  public VerifiedPayment verifyCallback(Map<String, String> payload) {
    throw new IllegalStateException("iyzico callback verification is not configured.");
  }
}
