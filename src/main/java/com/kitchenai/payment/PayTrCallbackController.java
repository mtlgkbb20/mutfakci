package com.kitchenai.payment;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/paytr")
@ConditionalOnProperty(
    name = "app.payments.provider",
    havingValue = "paytr",
    matchIfMissing = true)
public class PayTrCallbackController {
  private final PaymentCheckoutService checkouts;

  public PayTrCallbackController(PaymentCheckoutService checkouts) {
    this.checkouts = checkouts;
  }

  @PostMapping("/callback")
  public ResponseEntity<String> callback(@RequestParam Map<String, String> payload) {
    checkouts.handleCallback(payload);
    // PayTR requires a plain-text OK response after a valid notification.
    return ResponseEntity.ok("OK");
  }
}
