package com.kitchenai.membership;

import com.kitchenai.common.ApiException;
import com.kitchenai.payment.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {
  private final EntitlementService entitlements;
  private final PaymentProvider payments;

  public MembershipController(EntitlementService entitlements, PaymentProvider payments) {
    this.entitlements = entitlements;
    this.payments = payments;
  }

  @GetMapping
  public Map<String, Object> summary(@AuthenticationPrincipal UUID userId) {
    return entitlements.summary(userId);
  }

  public record CheckoutRequest(@NotBlank String planCode) {}

  @PostMapping("/checkout")
  public Map<String, Object> checkout(
      @Valid @RequestBody CheckoutRequest request, @AuthenticationPrincipal UUID userId) {
    if (!Set.of("COOKER_MONTHLY", "COOKER_YEARLY").contains(request.planCode())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "This membership plan is not purchasable.");
    }
    if (!payments.supportsRecurringSubscriptions()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
          "Recurring subscription checkout is not enabled for the selected payment provider.");
    }
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
        "Plan pricing and merchant subscription configuration must be completed before checkout.");
  }
}
