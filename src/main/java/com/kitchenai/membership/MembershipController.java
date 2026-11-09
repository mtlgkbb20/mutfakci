package com.kitchenai.membership;

import com.kitchenai.common.ApiException;
import com.kitchenai.payment.PaymentCheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {
  private final EntitlementService entitlements;
  private final PaymentCheckoutService checkouts;

  public MembershipController(
      EntitlementService entitlements,
      PaymentCheckoutService checkouts) {
    this.entitlements = entitlements;
    this.checkouts = checkouts;
  }

  @GetMapping
  public Map<String, Object> summary(@AuthenticationPrincipal UUID userId) {
    Map<String, Object> result = new LinkedHashMap<>(entitlements.summary(userId));
    result.put("plans", checkouts.planCatalog());
    return result;
  }

  public record CheckoutRequest(
      @NotBlank String planCode,
      @NotBlank String displayName,
      @NotBlank String address,
      @NotBlank String phone) {}

  @PostMapping("/checkout")
  public Map<String, Object> checkout(
      @Valid @RequestBody CheckoutRequest request,
      @AuthenticationPrincipal UUID userId,
      HttpServletRequest servletRequest) {
    if (userId == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }
    entitlements.ensureMembership(userId);
    return checkouts.createCheckout(
        userId,
        request.planCode(),
        request.displayName(),
        request.address(),
        request.phone(),
        servletRequest.getRemoteAddr());
  }

  @GetMapping("/checkouts/{orderId}")
  public Map<String, Object> checkoutStatus(
      @PathVariable String orderId,
      @AuthenticationPrincipal UUID userId) {
    return checkouts.checkoutStatus(userId, orderId);
  }
}
