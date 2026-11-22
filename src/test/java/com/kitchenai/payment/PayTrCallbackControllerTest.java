package com.kitchenai.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.support.WebTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class PayTrCallbackControllerTest {
  private static final String KEY = "merchant-key", SALT = "merchant-salt";
  private final MockMvc mvc = WebTestSupport.mvc(new PayTrCallbackController(new PayTrPaymentProvider(KEY, SALT)));

  private static String sign(String oid, String status, String amount) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal((oid + SALT + status + amount).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void validSignatureIsAcknowledgedWithPlainOk() throws Exception {
    mvc.perform(post("/api/v1/payments/paytr/callback")
            .param("merchant_oid", "ORDER1").param("status", "success").param("total_amount", "1299")
            .param("hash", sign("ORDER1", "success", "1299")))
        .andExpect(status().isOk()).andExpect(content().string("OK"));
  }

  @Test
  void forgedOrTamperedCallbackIs400NotA500() throws Exception {
    mvc.perform(post("/api/v1/payments/paytr/callback")
            .param("merchant_oid", "ORDER1").param("status", "success").param("total_amount", "999999")
            .param("hash", sign("ORDER1", "success", "1299")))
        .andExpect(status().isBadRequest()).andExpect(content().string("INVALID"));
  }

  @Test
  void incompleteCallbackIs400() throws Exception {
    mvc.perform(post("/api/v1/payments/paytr/callback").param("merchant_oid", "ORDER1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void misconfiguredProviderIsAServerError() throws Exception {
    MockMvc unconfigured = WebTestSupport.mvc(new PayTrCallbackController(new PayTrPaymentProvider("", "")));
    unconfigured.perform(post("/api/v1/payments/paytr/callback").param("merchant_oid", "x"))
        .andExpect(status().isInternalServerError());
  }
}
