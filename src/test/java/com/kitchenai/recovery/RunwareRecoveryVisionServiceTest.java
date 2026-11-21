package com.kitchenai.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.ai.AiOutputNormalizer;
import com.kitchenai.common.ApiException;
import com.kitchenai.recovery.RecoveryContracts.ActionType;
import com.kitchenai.support.FakeRunwareServer;
import java.math.BigDecimal;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class RunwareRecoveryVisionServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AiOutputNormalizer normalizer = mock(AiOutputNormalizer.class);
  private FakeRunwareServer runware;
  private RunwareRecoveryVisionService vision;
  private final byte[] image = {1, 2, 3};

  static final String VALID = """
      {"imageTrustScore":85,"analysisConfidence":0.8,"problemCategory":"TOO_SOFT","problemSummary":"Yumusak",
       "observedState":"akiskan","targetState":"sert","gapSummary":"kivam","reason":"su fazla",
       "targetProximityScore":80,
       "action":{"type":"REST","instruction":"10 dk dinlendir","quantity":null,"unit":null,"durationSeconds":600}}
      """;

  @BeforeEach void start() throws Exception {
    runware = new FakeRunwareServer();
    vision = new RunwareRecoveryVisionService(json, runware.environment(), normalizer);
  }
  @AfterEach void stop() { runware.close(); }

  private static String withAction(String action) {
    return VALID.replaceFirst("\"action\":\\{[^}]*}", "\"action\":" + action);
  }

  @Test
  void unconfiguredProviderIsUnavailableAndRefuses() {
    var off = new RunwareRecoveryVisionService(json, new MockEnvironment(), normalizer);
    assertFalse(off.available());
    assertEquals(503, assertThrows(ApiException.class, () -> off.analyze(image, "image/png", "{}")).status.value());
  }

  @Test
  void validResponseIsParsedAndImageIsSentAsDataUri() throws Exception {
    runware.respond(200, "{\"data\":[{\"text\":" + FakeRunwareServer.quote(VALID) + ",\"finishReason\":\"stop\"}]}");
    var analysis = vision.analyze(image, "image/png", "{\"productName\":\"Kek\"}");
    assertEquals(85, analysis.imageTrustScore());
    assertEquals(new BigDecimal("0.8"), analysis.analysisConfidence());
    assertEquals(ActionType.REST, analysis.action().type());
    assertEquals(600, analysis.action().durationSeconds());

    var task = json.readTree(runware.requests().get(0).body()).get(0);
    assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(image),
        task.path("inputs").path("images").get(0).asText());
    assertTrue(task.path("messages").get(0).path("content").asText().contains("{\"productName\":\"Kek\"}"));
    verifyNoInteractions(normalizer);
  }

  @Test
  void markdownFenceIsTolerated() {
    runware.respondText("```json\n" + VALID + "\n```");
    assertEquals(80, vision.analyze(image, "image/jpeg", "{}").targetProximityScore());
  }

  @Test
  void nullFieldsAreAllowedByTheContract() {
    runware.respondText(VALID.replace("\"imageTrustScore\":85", "\"imageTrustScore\":null").replace("\"reason\":\"su fazla\"", "\"reason\":\"  \""));
    var analysis = vision.analyze(image, "image/png", "{}");
    assertNull(analysis.imageTrustScore());
    assertNull(analysis.reason());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
    "{\"type\":\"ADD_INGREDIENT\",\"instruction\":\"un ekle\",\"quantity\":null,\"unit\":null,\"durationSeconds\":null}",
    "{\"type\":\"NONE\",\"instruction\":\"bir sey yap\",\"quantity\":null,\"unit\":null,\"durationSeconds\":null}",
    "{\"type\":\"REST\",\"instruction\":\"x\",\"quantity\":5,\"unit\":null,\"durationSeconds\":null}",
    "{\"type\":\"REST\",\"instruction\":\"x\",\"quantity\":500,\"unit\":\"g\",\"durationSeconds\":null}",
    "{\"type\":\"REST\",\"instruction\":\"x\",\"quantity\":null,\"unit\":null,\"durationSeconds\":7200}",
    "{\"type\":\"FLAMBE\",\"instruction\":\"x\",\"quantity\":null,\"unit\":null,\"durationSeconds\":null}",
    "{\"type\":\"REST\",\"instruction\":\"x\",\"quantity\":null,\"unit\":null}",
  })
  void unsafeOrMalformedActionsFailValidation(String action) {
    runware.respondText(withAction(action));
    when(normalizer.available()).thenReturn(false);
    assertThrows(RecoveryAnalysisFormatException.class, () -> vision.analyze(image, "image/png", "{}"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"imageTrustScore\":85", "\"targetProximityScore\":80"})
  void outOfRangeScoresFailValidation(String field) {
    runware.respondText(VALID.replace(field, field.replaceAll("\\d+", "150")));
    when(normalizer.available()).thenReturn(false);
    assertThrows(RecoveryAnalysisFormatException.class, () -> vision.analyze(image, "image/png", "{}"));
  }

  @Test
  void extraOrMissingTopLevelFieldsFailValidation() {
    runware.respondText(VALID.replace("{\"imageTrustScore\"", "{\"safeToEat\":true,\"imageTrustScore\""));
    when(normalizer.available()).thenReturn(false);
    assertThrows(RecoveryAnalysisFormatException.class, () -> vision.analyze(image, "image/png", "{}"));
  }

  @Test
  void invalidOutputIsRepairedByTheNormalizerAndRevalidated() {
    runware.respondText("Analiz: " + VALID);
    when(normalizer.available()).thenReturn(true);
    when(normalizer.normalize(eq("recipe-recovery"), anyString(), anyString(), anyString())).thenReturn(VALID);
    assertEquals(85, vision.analyze(image, "image/png", "{}").imageTrustScore());
  }

  @Test
  void normalizerOutputMustAlsoSatisfyTheContract() {
    runware.respondText("garbage");
    when(normalizer.available()).thenReturn(true);
    when(normalizer.normalize(any(), any(), any(), any())).thenReturn(withAction(
        "{\"type\":\"ADD_INGREDIENT\",\"instruction\":\"x\",\"quantity\":null,\"unit\":null,\"durationSeconds\":null}"));
    assertThrows(RecoveryAnalysisFormatException.class, () -> vision.analyze(image, "image/png", "{}"));
  }

  @Test
  void truncatedOutputSkipsTheNormalizer() {
    runware.respond(200, "{\"data\":[{\"text\":\"{\\\"imageTrust\",\"finishReason\":\"length\"}]}");
    assertThrows(RecoveryAnalysisFormatException.class, () -> vision.analyze(image, "image/png", "{}"));
    verify(normalizer, never()).normalize(any(), any(), any(), any());
  }

  @Test
  void providerFailuresBecomeBadGateway() {
    runware.respond(500, "{\"errors\":[{\"code\":\"x\"}]}");
    assertEquals(502, assertThrows(ApiException.class, () -> vision.analyze(image, "image/png", "{}")).status.value());
    runware.respond(200, "{\"errors\":[{\"code\":\"x\"}],\"data\":[]}");
    assertEquals(502, assertThrows(ApiException.class, () -> vision.analyze(image, "image/png", "{}")).status.value());
    runware.respondText("");
    assertEquals(502, assertThrows(ApiException.class, () -> vision.analyze(image, "image/png", "{}")).status.value());
    runware.respond(200, "not json at all");
    assertEquals(502, assertThrows(ApiException.class, () -> vision.analyze(image, "image/png", "{}")).status.value());
  }
}
