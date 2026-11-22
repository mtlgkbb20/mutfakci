package com.kitchenai.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.ai.RecipeGenerationService.*;
import com.kitchenai.common.ApiException;
import com.kitchenai.support.FakeRunwareServer;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class RunwareRecipeGenerationServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AiOutputNormalizer normalizer = mock(AiOutputNormalizer.class);
  private FakeRunwareServer runware;
  private RunwareRecipeGenerationService service;
  private final RecipeRequest request = new RecipeRequest("Su böreği", "hazır yufka", null, null, 4);

  static final String PLAN = """
      {"dishTitle":"Su Böreği","servings":4,"assemblyRequired":true,"components":[
        {"key":"assembly","name":"Birleştirme","purpose":"p","order":2,"dependsOn":["filling"]},
        {"key":"filling","name":"İç harç","purpose":"p","order":1,"dependsOn":[]}]}
      """;
  static String component(String key, String title, int durationSeconds) {
    return """
        {"componentKey":"%s","title":"%s",
         "ingredients":[{"name":"Peynir","quantity":200,"unit":"g","preparation":null}],
         "instructions":[{"order":1,"instruction":"Karıştır","completionCue":"Homojen","temperature":null,"durationSeconds":%d},
                         {"order":2,"instruction":"Dinlendir","completionCue":"Oturmuş","temperature":180,"durationSeconds":null}]}
        """.formatted(key, title, durationSeconds);
  }

  @BeforeEach void start() throws Exception {
    runware = new FakeRunwareServer();
    service = new RunwareRecipeGenerationService(json, runware.environment(), normalizer);
  }
  @AfterEach void stop() { runware.close(); }

  private static int status(Runnable call) { return assertThrows(ApiException.class, call::run).status.value(); }

  @Test
  void unconfiguredProviderIs503() {
    var off = new RunwareRecipeGenerationService(json, new MockEnvironment(), normalizer);
    assertEquals(503, status(() -> off.generate(request)));
  }

  @Test
  void generatesComponentsInPlanOrderAndAssemblesContinuousStages() throws Exception {
    runware.respondChat(PLAN).respondChat(component("filling", "İç harç", 600)).respondChat(component("assembly", "Birleştirme", 2400));

    GenerationTrace trace = service.generateWithTrace(request);
    RecipeResult recipe = trace.recipe();

    assertEquals("Su Böreği", recipe.title());
    assertEquals(4, recipe.servings());
    assertEquals(List.of(1, 2, 3, 4), recipe.stages().stream().map(Stage::order).toList());
    assertEquals(List.of("İç harç", "İç harç", "Birleştirme", "Birleştirme"), recipe.stages().stream().map(Stage::title).toList());
    assertEquals(2, recipe.ingredients().size());
    assertEquals(2, trace.components().size());
    assertEquals(3, runware.requests().size());

    String componentPrompt = json.readTree(runware.requests().get(1).body()).path("messages").get(1).path("content").asText();
    assertTrue(componentPrompt.contains("The componentKey must match 'filling' exactly."));
    verifyNoInteractions(normalizer);
  }

  @Test
  void bareArrayDecompositionIsWrappedWithRequestDefaults() {
    runware.respondChat("[{\"key\":\"filling\",\"name\":\"İç\",\"purpose\":\"p\",\"order\":1,\"dependsOn\":[]}]")
        .respondChat(component("filling", "İç", 60));
    RecipeResult recipe = service.generate(request);
    assertEquals("Su böreği", recipe.title());
    assertEquals(4, recipe.servings());
  }

  @Test
  void cyclicPlanIsRejectedWithoutGeneratingComponents() {
    runware.respondChat(PLAN.replace("\"dependsOn\":[]", "\"dependsOn\":[\"assembly\"]"));
    when(normalizer.available()).thenReturn(false);
    assertEquals(502, status(() -> service.generate(request)));
    assertEquals(1, runware.requests().size());
  }

  @Test
  void componentWithWrongKeyIsRejected() {
    runware.respondChat(PLAN).respondChat(component("something-else", "X", 60));
    when(normalizer.available()).thenReturn(false);
    assertEquals(502, status(() -> service.generate(request)));
  }

  @Test
  void invalidComponentIsRepairedOnceThroughTheNormalizer() {
    runware.respondChat(PLAN).respondChat(component("wrong", "İç harç", 60)).respondChat(component("assembly", "B", 60));
    when(normalizer.available()).thenReturn(true);
    when(normalizer.normalize(eq("component-filling"), anyString(), anyString(), anyString()))
        .thenReturn(component("filling", "İç harç", 60));
    assertEquals(4, service.generate(request).stages().size());
  }

  @Test
  void nonJsonContentGoesThroughTheNormalizerAndIsStillValidated() {
    runware.respondChat("Tabii! İşte plan: ...");
    when(normalizer.available()).thenReturn(true);
    when(normalizer.normalize(eq("decomposition"), anyString(), anyString(), anyString()))
        .thenReturn("{\"dishTitle\":\"\",\"servings\":4,\"components\":[],\"assemblyRequired\":true}");
    assertEquals(502, status(() -> service.generate(request)));
    verify(normalizer, times(1)).normalize(any(), any(), any(), any());
  }

  @Test
  void rateLimitIs429AndOtherProviderErrorsAre502WithoutRetrying() {
    runware.respond(429, "{\"error\":{\"message\":\"slow down\"}}");
    assertEquals(429, status(() -> service.generate(request)));
    runware.respond(500, "{\"error\":{\"message\":\"boom\"}}");
    assertEquals(502, status(() -> service.generate(request)));
    assertEquals(2, runware.requests().size(), "billable provider calls must never be retried automatically");
  }

  @Test
  void emptyOrRefusedContentIs502() {
    runware.respond(200, "{\"choices\":[{\"message\":{\"content\":\"\",\"refusal\":\"no\"},\"finish_reason\":\"stop\"}]}");
    assertEquals(502, status(() -> service.generate(request)));
  }

  @Test
  void contentPartsArrayIsAccepted() {
    String parts = json.createObjectNode().set("choices", json.createArrayNode().add(json.createObjectNode().set("message",
        json.createObjectNode().set("content", json.createArrayNode()
            .add(json.createObjectNode().put("type", "text").put("text", "[{\"key\":\"filling\",\"name\":\"İç\",\"purpose\":\"p\","))
            .add(json.createObjectNode().put("type", "text").put("text", "\"order\":1,\"dependsOn\":[]}]")))))).toString();
    runware.respond(200, parts).respondChat(component("filling", "İç", 60));
    assertEquals(2, service.generate(request).stages().size());
  }

  // ---- review ----
  private static RecipeResult draft() {
    return new RecipeResult("Börek", "d", "AI_GENERATED", 4, 15, 40,
        List.of(new Ingredient("Yufka", 3, "adet", false)),
        List.of(new Stage(1, "Yufkalandırma", "Yufkayı aç", "İnce", null, null)));
  }

  @Test
  void reviewReturnsTheValidatedRecipe() throws Exception {
    runware.respondChat(json.writeValueAsString(new RecipeResult("Börek", "d", "AI_GENERATED", 4, 15, 40,
        List.of(new Ingredient("Yufka", 3, "adet", false)), List.of(new Stage(1, "Yufkayı açma", "Yufkayı aç", "İnce", null, null)))));
    assertEquals("Yufkayı açma", service.review(draft()).stages().get(0).title());
  }

  @Test
  void reviewWithBrokenStageOrderOrMissingIngredientsIs502() throws Exception {
    when(normalizer.available()).thenReturn(false);
    runware.respondChat(json.writeValueAsString(new RecipeResult("Börek", "d", "X", 4, 15, 40,
        List.of(new Ingredient("Yufka", 3, "adet", false)), List.of(new Stage(2, "a", "b", "c", null, null)))));
    assertEquals(502, status(() -> service.review(draft())));
    runware.respondChat(json.writeValueAsString(new RecipeResult("Börek", "d", "X", 4, 15, 40, List.of(), List.of(new Stage(1, "a", "b", "c", null, null)))));
    assertEquals(502, status(() -> service.review(draft())));
  }
}
