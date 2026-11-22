package com.kitchenai.mealplan;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import com.kitchenai.support.FakeRunwareServer;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class RunwareMealPlanAiSelectorTest {
  private final ObjectMapper json = new ObjectMapper();
  private FakeRunwareServer runware;
  private RunwareMealPlanAiSelector selector;
  private final MealPlanContracts.CreateRequest request = new MealPlanContracts.CreateRequest(
      LocalDate.of(2026, 11, 23), 2, List.of("DINNER"), 45, List.of("vegan"), "hafif", "not");
  private final List<CatalogRecipe> candidates = List.of(new CatalogRecipe(1, "mercimek", "Mercimek", List.of(),
      "tr", "SOUP", List.of(), 4, 30, List.of(), List.of(), "d"));

  @BeforeEach void start() throws Exception {
    runware = new FakeRunwareServer();
    selector = new RunwareMealPlanAiSelector(json, runware.environment());
  }
  @AfterEach void stop() { runware.close(); }

  @Test
  void withoutApiKeyTheSelectorIsSkipped() {
    assertEquals(Optional.empty(), new RunwareMealPlanAiSelector(json, new MockEnvironment()).select(request, candidates, List.of("DINNER")));
    assertTrue(runware.requests().isEmpty());
  }

  @Test
  void parsesSelectionsKeyedByDayAndSlot() throws Exception {
    runware.respondChat("{\"selections\":[{\"dayOffset\":0,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"mercimek\"},"
        + "{\"dayOffset\":6,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"mercimek\"}]}");
    assertEquals(Map.of("0:DINNER", "mercimek", "6:DINNER", "mercimek"),
        selector.select(request, candidates, List.of("DINNER")).orElseThrow());

    var sent = runware.requests().get(0);
    assertEquals("/v1/chat/completions", sent.path());
    assertEquals("Bearer test-key", sent.authorization());
    var body = json.readTree(sent.body());
    assertEquals("json_object", body.path("response_format").path("type").asText());
    String prompt = body.path("messages").get(1).path("content").asText();
    assertTrue(prompt.contains("\"id\":\"mercimek\""));
    assertTrue(prompt.contains("Maximum preparation minutes: 45"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"selections\":[{\"dayOffset\":7,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"x\"}]}",
    "{\"selections\":[{\"dayOffset\":-1,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"x\"}]}",
    "{\"selections\":[{\"dayOffset\":0,\"mealSlot\":\"\",\"recipeCatalogId\":\"x\"}]}",
    "{\"selections\":[{\"dayOffset\":0,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"\"}]}",
    "{\"selections\":[{\"dayOffset\":0,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"a\"},{\"dayOffset\":0,\"mealSlot\":\"DINNER\",\"recipeCatalogId\":\"b\"}]}",
    "{\"selections\":{}}",
    "not json",
  })
  void invalidOrAmbiguousOutputFallsBackToDeterministicPlanning(String content) {
    runware.respondChat(content);
    assertEquals(Optional.empty(), selector.select(request, candidates, List.of("DINNER")));
  }

  @Test
  void providerErrorFallsBack() {
    runware.respond(429, "{}");
    assertEquals(Optional.empty(), selector.select(request, candidates, List.of("DINNER")));
  }
}
