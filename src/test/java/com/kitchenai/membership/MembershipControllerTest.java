package com.kitchenai.membership;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.payment.PaymentProvider;
import com.kitchenai.support.WebTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class MembershipControllerTest {
  private final EntitlementService entitlements = mock(EntitlementService.class);
  private final PaymentProvider payments = mock(PaymentProvider.class);
  private final MockMvc mvc = WebTestSupport.mvc(new MembershipController(entitlements, payments));
  private final UUID user = UUID.randomUUID();

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  private org.springframework.test.web.servlet.ResultActions checkout(String plan) throws Exception {
    return mvc.perform(post("/api/v1/membership/checkout").contentType(MediaType.APPLICATION_JSON)
        .content("{\"planCode\":\"" + plan + "\"}"));
  }

  @Test
  void summaryIsLoadedForTheAuthenticatedUser() throws Exception {
    when(entitlements.summary(user)).thenReturn(Map.of("tier", "FREE", "remaining", 3));
    mvc.perform(get("/api/v1/membership")).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(3));
    verify(entitlements).summary(user);
  }

  @ParameterizedTest
  @ValueSource(strings = {"FREE", "CHEF", "cooker_monthly", "ANYTHING"})
  void nonPurchasablePlansAreRejectedWith400(String plan) throws Exception {
    checkout(plan).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("This membership plan is not purchasable."));
    verifyNoInteractions(payments);
  }

  @Test
  void blankPlanCodeFailsValidation() throws Exception {
    checkout("").andExpect(status().isBadRequest());
  }

  @Test
  void providerWithoutRecurringSupportReturns503() throws Exception {
    when(payments.supportsRecurringSubscriptions()).thenReturn(false);
    checkout("COOKER_MONTHLY").andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Recurring subscription checkout is not enabled for the selected payment provider."));
  }

  @ParameterizedTest
  @ValueSource(strings = {"COOKER_MONTHLY", "COOKER_YEARLY"})
  void checkoutStaysDisabledUntilPricingIsConfigured(String plan) throws Exception {
    when(payments.supportsRecurringSubscriptions()).thenReturn(true);
    checkout(plan).andExpect(status().isServiceUnavailable());
    verify(payments, never()).createCheckout(any());
  }
}
