package com.kitchenai.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.ai.AiOutputNormalizer;
import com.kitchenai.support.FakeRunwareServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class RunwareTextFallbackAdviceServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AiOutputNormalizer normalizer = mock(AiOutputNormalizer.class);
  private FakeRunwareServer runware;
  private RunwareTextFallbackAdviceService service;

  @BeforeEach void start() throws Exception {
    runware = new FakeRunwareServer();
    service = new RunwareTextFallbackAdviceService(json, runware.environment(), normalizer);
  }
  @AfterEach void stop() { runware.close(); }

  @Test
  void parsesAStrictAdviceObject() throws Exception {
    runware.respondText("```json\n{\"advice\":\"  Hamuru 10 dk dinlendir. \",\"caution\":\"\"}\n```");
    var advice = service.suggest("Kek", "Hamur civik");
    assertEquals("Hamuru 10 dk dinlendir.", advice.advice());
    assertEquals("", advice.caution());
    String prompt = json.readTree(runware.requests().get(0).body()).get(0).path("messages").get(0).path("content").asText();
    assertTrue(prompt.contains("Recipe name: Kek"));
    verifyNoInteractions(normalizer);
  }

  @Test
  void contractViolationIsRepairedThroughTheNormalizer() {
    runware.respondText("{\"advice\":\"Dinlendir\",\"caution\":\"\",\"safeToEat\":true}");
    when(normalizer.available()).thenReturn(true);
    when(normalizer.normalize(eq("text-recovery-fallback"), anyString(), anyString(), anyString()))
        .thenReturn("{\"advice\":\"Dinlendir\",\"caution\":\"\"}");
    assertEquals("Dinlendir", service.suggest("Kek", "x").advice());
  }

  @Test
  void contractViolationWithoutNormalizerFails() {
    runware.respondText("{\"advice\":\"\",\"caution\":\"\"}");
    when(normalizer.available()).thenReturn(false);
    assertThrows(IllegalStateException.class, () -> service.suggest("Kek", "x"));
  }

  @Test
  void overlongAdviceIsRejectedEvenAfterNormalization() {
    String tooLong = "{\"advice\":\"" + "a".repeat(701) + "\",\"caution\":\"\"}";
    runware.respondText(tooLong);
    when(normalizer.available()).thenReturn(true);
    when(normalizer.normalize(any(), any(), any(), any())).thenReturn(tooLong);
    assertThrows(IllegalStateException.class, () -> service.suggest("Kek", "x"));
  }

  @Test
  void providerFailureAndEmptyTextAreErrors() {
    runware.respond(503, "{}");
    assertThrows(IllegalStateException.class, () -> service.suggest("Kek", "x"));
    runware.respondText("");
    assertThrows(IllegalStateException.class, () -> service.suggest("Kek", "x"));
  }

  @Test
  void unconfiguredServiceRefusesWithoutNetworkCalls() {
    var unconfigured = new RunwareTextFallbackAdviceService(json, new MockEnvironment(), normalizer);
    assertThrows(IllegalStateException.class, () -> unconfigured.suggest("Kek", "x"));
    assertTrue(runware.requests().isEmpty());
  }
}
