package com.kitchenai.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.payments.provider",
    havingValue = "paytr",
    matchIfMissing = true)
public class PayTrPaymentProvider implements PaymentProvider {
  private static final String TOKEN_URL = "https://www.paytr.com/odeme/api/get-token";

  private final String merchantId;
  private final String merchantKey;
  private final String merchantSalt;
  private final String userIpOverride;
  private final boolean testMode;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public PayTrPaymentProvider(
      @Value("${PAYTR_MERCHANT_ID:}") String merchantId,
      @Value("${PAYTR_MERCHANT_KEY:}") String merchantKey,
      @Value("${PAYTR_MERCHANT_SALT:}") String merchantSalt,
      @Value("${PAYTR_TEST_USER_IP:}") String userIpOverride,
      @Value("${app.payments.test-mode:true}") boolean testMode,
      ObjectMapper objectMapper) {
    this.merchantId = merchantId;
    this.merchantKey = merchantKey;
    this.merchantSalt = merchantSalt;
    this.userIpOverride = userIpOverride;
    this.testMode = testMode;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  @Override
  public String providerCode() {
    return "PAYTR";
  }

  @Override
  public boolean supportsRecurringSubscriptions() {
    return false;
  }

  @Override
  public boolean isCheckoutConfigured() {
    return !merchantId.isBlank() && !merchantKey.isBlank() && !merchantSalt.isBlank();
  }

  @Override
  public CheckoutResult createCheckout(CheckoutCommand command) {
    requireCredentials();
    String userIp = userIpOverride.isBlank() ? command.userIp() : userIpOverride;
    if (userIp == null || userIp.isBlank()) {
      throw new IllegalArgumentException(
          "PayTR requires a public user IP. Configure PAYTR_TEST_USER_IP for local tests.");
    }

    String amount = command.amount()
        .setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .toBigIntegerExact()
        .toString();
    String basket = encodeBasket(command);
    String noInstallment = "1";
    String maxInstallment = "0";
    String currency = "TL";
    String testModeValue = testMode ? "1" : "0";

    String hashSource = merchantId
        + userIp
        + command.merchantOrderId()
        + command.email()
        + amount
        + basket
        + noInstallment
        + maxInstallment
        + currency
        + testModeValue;
    String token = hmacBase64(merchantKey, hashSource + merchantSalt);

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("merchant_id", merchantId);
    fields.put("user_ip", userIp);
    fields.put("merchant_oid", command.merchantOrderId());
    fields.put("email", command.email());
    fields.put("payment_amount", amount);
    fields.put("paytr_token", token);
    fields.put("user_basket", basket);
    fields.put("debug_on", testMode ? "1" : "0");
    fields.put("no_installment", noInstallment);
    fields.put("max_installment", maxInstallment);
    fields.put("user_name", limit(command.displayName(), 60));
    fields.put("user_address", limit(command.address(), 400));
    fields.put("user_phone", limit(command.phone(), 20));
    fields.put("merchant_ok_url", command.successUrl());
    fields.put("merchant_fail_url", command.failureUrl());
    fields.put("timeout_limit", "30");
    fields.put("currency", currency);
    fields.put("test_mode", testModeValue);
    fields.put("lang", "tr");
    fields.put("iframe_v2", "1");

    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
          .timeout(Duration.ofSeconds(25))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(formEncode(fields)))
          .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "PayTR token request failed with HTTP " + response.statusCode());
      }

      JsonNode body = objectMapper.readTree(response.body());
      if (!"success".equalsIgnoreCase(body.path("status").asText())) {
        String reason = body.path("reason").asText("PayTR did not return a checkout token.");
        throw new IllegalStateException("PayTR checkout initialization failed: " + reason);
      }

      String iframeToken = body.path("token").asText();
      if (iframeToken.isBlank()) {
        throw new IllegalStateException("PayTR returned an empty checkout token.");
      }
      return new CheckoutResult(
          providerCode(),
          "https://www.paytr.com/odeme/guvenli/" + iframeToken,
          iframeToken,
          command.merchantOrderId());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("PayTR checkout request was interrupted.", exception);
    } catch (Exception exception) {
      if (exception instanceof IllegalStateException illegalStateException) {
        throw illegalStateException;
      }
      throw new IllegalStateException("Could not initialize PayTR checkout.", exception);
    }
  }

  @Override
  public VerifiedPayment verifyCallback(Map<String, String> payload) {
    requireCredentials();
    String orderId = required(payload, "merchant_oid");
    String status = required(payload, "status");
    String totalAmount = required(payload, "total_amount");
    String receivedHash = required(payload, "hash");

    String expected = hmacBase64(
        merchantKey, orderId + merchantSalt + status + totalAmount);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        receivedHash.getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("Invalid PayTR callback signature.");
    }

    String currency = payload.getOrDefault("currency", "TL");
    String normalizedCurrency = "TL".equalsIgnoreCase(currency)
        ? "TRY"
        : currency.toUpperCase();
    return new VerifiedPayment(
        providerCode(),
        orderId,
        orderId,
        "success".equalsIgnoreCase(status),
        new BigDecimal(totalAmount).movePointLeft(2),
        normalizedCurrency);
  }

  private void requireCredentials() {
    if (!isCheckoutConfigured()) {
      throw new IllegalStateException("PayTR merchant credentials are not configured.");
    }
  }

  private String encodeBasket(CheckoutCommand command) {
    try {
      List<Object> item = new ArrayList<>();
      item.add("Mutfakçı " + command.planCode());
      item.add(command.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
      item.add(1);
      return Base64.getEncoder().encodeToString(
          objectMapper.writeValueAsBytes(List.of(item)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not encode PayTR basket.", exception);
    }
  }

  private static String formEncode(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .reduce((left, right) -> left + "&" + right)
        .orElse("");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String limit(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Required PayTR customer information is missing.");
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String required(Map<String, String> payload, String key) {
    String value = payload.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing PayTR field: " + key);
    }
    return value;
  }

  private static String hmacBase64(String key, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(
          mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not calculate PayTR signature.", exception);
    }
  }
}
