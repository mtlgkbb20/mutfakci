package com.kitchenai.membership;

import com.kitchenai.common.ApiException;
import com.kitchenai.payment.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {
  private final EntitlementService entitlements;
  private final PaymentProvider payments;
  private final JdbcTemplate jdbc;
  private final String callbackUrl;
  private final String frontendUrl;

  public MembershipController(
      EntitlementService entitlements, PaymentProvider payments, JdbcTemplate jdbc,
      @Value("${app.payments.callback-url:}") String callbackUrl,
      @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
    this.entitlements = entitlements;
    this.payments = payments;
    this.jdbc = jdbc;
    this.callbackUrl = callbackUrl;
    this.frontendUrl = frontendUrl;
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
