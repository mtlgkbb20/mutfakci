package com.kitchenai.payment;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/paytr")
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "paytr", matchIfMissing = true)
public class PayTrCallbackController {
  private final PaymentProvider provider;

  public PayTrCallbackController(PaymentProvider provider) { this.provider = provider; }

  @PostMapping("/callback")
  public ResponseEntity<String> callback(@RequestParam Map<String, String> payload) {
    provider.verifyCallback(payload);
    // No membership activation is performed here until a recurring product contract,
    // persisted checkout/order mapping, and amount reconciliation are configured.
    return ResponseEntity.ok("OK");
  }
}
