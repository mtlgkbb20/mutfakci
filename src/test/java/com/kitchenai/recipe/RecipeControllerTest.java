package com.kitchenai.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.ai.RecipeGenerationService;
import com.kitchenai.ai.RecipeGenerationService.*;
import com.kitchenai.ai.RecipeReviewService;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.gamification.GamificationService;
import com.kitchenai.membership.EntitlementService;
import com.kitchenai.membership.EntitlementService.QuotaReservation;
import com.kitchenai.support.WebTestSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class RecipeControllerTest {
  private final RecipeRepository recipes = mock(RecipeRepository.class);
  private final CookingSessionRepository sessions = mock(CookingSessionRepository.class);
  private final RecipeGenerationService ai = mock(RecipeGenerationService.class);
  private final GamificationService gamification = mock(GamificationService.class);
  private final RecipeCatalogService catalog = mock(RecipeCatalogService.class);
  private final RecipeGenerationRepository generations = mock(RecipeGenerationRepository.class);
  private final RecipeReviewService reviewer = mock(RecipeReviewService.class);
  private final EntitlementService entitlements = mock(EntitlementService.class);
  private final MockMvc mvc = WebTestSupport.mvc(new RecipeController(recipes, sessions, ai, new ObjectMapper(),
      gamification, catalog, generations, reviewer, entitlements));
  private final UUID user = UUID.randomUUID();
  private final QuotaReservation reservation = new QuotaReservation(user, "2026-11-22");

  @BeforeEach
  void setUp() {
    WebTestSupport.authenticate(user);
    when(catalog.find(anyString())).thenReturn(Optional.empty());
    when(generations.findTop1ByUserIdAndNormalizedDesiredFoodAndStatusOrderByCreatedAtDesc(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(recipes.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));
    when(generations.save(any(RecipeGeneration.class))).thenAnswer(inv -> inv.getArgument(0));
    when(sessions.save(any(CookingSession.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @AfterEach void tearDown() { WebTestSupport.clearAuthentication(); }

  private ResultActions generate(String body) throws Exception {
    return mvc.perform(post("/api/v1/recipes/generate").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static RecipeResult aiRecipe() {
    return new RecipeResult("Elmalı Kek", "Yumuşak", "CAKE", 4, 15, 40,
        List.of(new Ingredient("Elma", 2, "adet", false)),
        List.of(new Stage(1, "Karıştır", "Karıştır", "Pürüzsüz", null, 300)));
  }

  // ---- generate ----
  @ParameterizedTest
  @ValueSource(strings = {"{\"desiredFood\":\"\"}", "{\"desiredFood\":\"kek\",\"servings\":0}", "{\"desiredFood\":\"kek\",\"servings\":31}"})
  void invalidGenerateRequestsAreRejectedWithoutTouchingQuota(String body) throws Exception {
    generate(body).andExpect(status().isBadRequest());
    verifyNoInteractions(entitlements, ai);
  }

  @Test
  void curatedCatalogMatchIsReturnedWithoutAiOrQuota() throws Exception {
    var step = new RecipeCatalogService.Step(1, "Çırp", "Yumurtaları çırp", "Köpürünce", null, 120);
    var match = new RecipeCatalogService.CatalogRecipe(1, "cat-kek", "Kek", List.of(), "tr", "CAKE", List.of(),
        6, 60, List.of(new RecipeCatalogService.Ingredient("Un", 200, "g", null)), List.of(step), "Klasik");
    when(catalog.find("kek")).thenReturn(Optional.of(match));
    when(recipes.findByCatalogId("cat-kek")).thenReturn(Optional.empty());

    generate("{\"desiredFood\":\"kek\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceType").value("CURATED"))
        .andExpect(jsonPath("$.catalogId").value("cat-kek"))
        .andExpect(jsonPath("$.servings").value(6))
        .andExpect(jsonPath("$.preparationTime").value(20))
        .andExpect(jsonPath("$.cookingTime").value(40));
    verifyNoInteractions(entitlements, ai);
  }

  @Test
  void cachedGenerationOfTheSameUserIsReusedWithoutSpendingQuota() throws Exception {
    Recipe mine = new Recipe(user, null, "AI_GENERATED", "Kek", "d", "CAKE", 4, 10, 20, "[]", "[]");
    RecipeGeneration done = new RecipeGeneration(user, "Kek!", "kek", "AI_GENERATED", "runware");
    done.complete(mine.getId(), null, null, null);
    when(generations.findTop1ByUserIdAndNormalizedDesiredFoodAndStatusOrderByCreatedAtDesc(user, "kek", "COMPLETED"))
        .thenReturn(Optional.of(done));
    when(recipes.findById(mine.getId())).thenReturn(Optional.of(mine));

    generate("{\"desiredFood\":\"Kek!\"}").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(mine.getId().toString()));
    verifyNoInteractions(entitlements, ai);
  }

  @Test
  void cacheNeverServesAnotherUsersRecipe() throws Exception {
    Recipe theirs = new Recipe(UUID.randomUUID(), null, "AI_GENERATED", "Kek", "d", "CAKE", 4, 10, 20, "[]", "[]");
    RecipeGeneration done = new RecipeGeneration(user, "kek", "kek", "AI_GENERATED", "runware");
    done.complete(theirs.getId(), null, null, null);
    when(generations.findTop1ByUserIdAndNormalizedDesiredFoodAndStatusOrderByCreatedAtDesc(user, "kek", "COMPLETED"))
        .thenReturn(Optional.of(done));
    when(recipes.findById(theirs.getId())).thenReturn(Optional.of(theirs));
    when(entitlements.reserve(user)).thenReturn(null);

    generate("{\"desiredFood\":\"kek\"}").andExpect(status().isTooManyRequests());
    verify(entitlements).reserve(user);
  }

  @Test
  void exhaustedQuotaReturns429WithoutCallingAi() throws Exception {
    when(entitlements.reserve(user)).thenReturn(null);
    generate("{\"desiredFood\":\"kek\"}")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("Recipe generation quota exhausted"));
    verifyNoInteractions(ai);
    verify(entitlements, never()).release(any());
  }

  @Test
  void successfulAiGenerationPersistsRecipeCompletesAuditAndCommitsQuota() throws Exception {
    when(entitlements.reserve(user)).thenReturn(reservation);
    when(ai.generateWithTrace(any())).thenReturn(new GenerationTrace(aiRecipe(), null, List.of()));
    when(reviewer.review(any())).thenAnswer(inv -> inv.getArgument(0));

    generate("{\"desiredFood\":\"Elmalı kek\",\"servings\":4}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Elmalı Kek"))
        .andExpect(jsonPath("$.sourceType").value("AI_GENERATED"));

    ArgumentCaptor<RecipeGeneration> audit = ArgumentCaptor.forClass(RecipeGeneration.class);
    verify(generations, atLeastOnce()).save(audit.capture());
    RecipeGeneration last = audit.getValue();
    assertEquals("COMPLETED", last.getStatus());
    assertEquals("elmali kek", last.getNormalizedDesiredFood());
    assertNotNull(last.getRecipeId());
    verify(entitlements).commit(reservation);
    verify(entitlements, never()).release(any());
  }

  @Test
  void aiFailureReleasesQuotaMarksAuditFailedAndHidesTheCause() throws Exception {
    when(entitlements.reserve(user)).thenReturn(reservation);
    when(ai.generateWithTrace(any())).thenThrow(new IllegalStateException("upstream api-key=XYZ rejected"));

    generate("{\"desiredFood\":\"kek\"}")
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Recipe generation failed"))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("XYZ"))));

    ArgumentCaptor<RecipeGeneration> audit = ArgumentCaptor.forClass(RecipeGeneration.class);
    verify(generations, atLeastOnce()).save(audit.capture());
    assertEquals("FAILED", audit.getValue().getStatus());
    verify(entitlements).release(reservation);
    verify(entitlements, never()).commit(any());
    verify(recipes, never()).save(any());
  }

  @Test
  void failingQuotaReleaseDoesNotMaskTheOriginalError() throws Exception {
    when(entitlements.reserve(user)).thenReturn(reservation);
    when(ai.generateWithTrace(any())).thenThrow(new RuntimeException("boom"));
    doThrow(new RuntimeException("db down")).when(entitlements).release(reservation);
    generate("{\"desiredFood\":\"kek\"}").andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Recipe generation failed"));
  }

  // ---- ownership ----
  @Test
  void recipeOfAnotherUserIs404() throws Exception {
    Recipe theirs = new Recipe(UUID.randomUUID(), null, "AI_GENERATED", "Kek", "d", "CAKE", 4, 10, 20, "[]", "[]");
    when(recipes.findById(theirs.getId())).thenReturn(Optional.of(theirs));
    mvc.perform(get("/api/v1/recipes/" + theirs.getId())).andExpect(status().isNotFound());
  }

  @Test
  void curatedRecipesWithoutOwnerAreVisibleToEveryone() throws Exception {
    Recipe curated = new Recipe(null, "cat-1", "CURATED", "Kek", "d", "CAKE", 4, 10, 20, "[]", "[]");
    when(recipes.findById(curated.getId())).thenReturn(Optional.of(curated));
    mvc.perform(get("/api/v1/recipes/" + curated.getId())).andExpect(status().isOk());
  }

  @Test
  void listOnlyReturnsTheCallersRecipes() throws Exception {
    when(recipes.findTop10ByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of());
    mvc.perform(get("/api/v1/recipes")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    verify(recipes).findTop10ByUserIdOrderByCreatedAtDesc(user);
  }

  // ---- cooking sessions ----
  @Test
  void startingASessionForAnotherUsersRecipeIs404() throws Exception {
    Recipe theirs = new Recipe(UUID.randomUUID(), null, "AI_GENERATED", "Kek", "d", "CAKE", 4, 10, 20, "[]", "[]");
    when(recipes.findById(theirs.getId())).thenReturn(Optional.of(theirs));
    mvc.perform(post("/api/v1/cooking-sessions").contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipeId\":\"" + theirs.getId() + "\"}"))
        .andExpect(status().isNotFound());
    verify(sessions, never()).save(any());
  }

  @Test
  void sessionWithoutRecipeIsARecoverySession() throws Exception {
    mvc.perform(post("/api/v1/cooking-sessions").contentType(MediaType.APPLICATION_JSON).content("{\"desiredOutcome\":\"kurtar\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionType").value("RECOVERY"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.userId").value(user.toString()));
  }

  @Test
  void completingASessionAwardsIdempotentXpAndBadges() throws Exception {
    CookingSession session = new CookingSession(user, null, "RECOVERY", null);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(gamification.grantXp(eq(user), eq(10), eq("COOKING_SESSION"), anyString(),
        eq("cooking-session-complete:" + session.getId()), anyString())).thenReturn(true, false);

    mvc.perform(post("/api/v1/cooking-sessions/" + session.getId() + "/complete"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.xpAwarded").value(10));
    mvc.perform(post("/api/v1/cooking-sessions/" + session.getId() + "/complete"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.xpAwarded").value(0));
    assertEquals("COMPLETED", session.getStatus());
    verify(gamification, times(2)).awardBadge(user, "FIRST_DISH", "first-completed-session");
    verify(gamification, times(2)).evaluateCookingBadges(user);
  }

  @Test
  void anotherUsersSessionCannotBeReadCompletedOrAbandoned() throws Exception {
    CookingSession theirs = new CookingSession(UUID.randomUUID(), null, "RECOVERY", null);
    when(sessions.findById(theirs.getId())).thenReturn(Optional.of(theirs));
    mvc.perform(get("/api/v1/cooking-sessions/" + theirs.getId())).andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/cooking-sessions/" + theirs.getId() + "/complete")).andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/cooking-sessions/" + theirs.getId() + "/abandon")).andExpect(status().isNotFound());
    verifyNoInteractions(gamification);
    assertEquals("ACTIVE", theirs.getStatus());
  }

  @Test
  void abandoningMarksTheSessionAbandoned() throws Exception {
    CookingSession session = new CookingSession(user, null, "RECOVERY", null);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    mvc.perform(post("/api/v1/cooking-sessions/" + session.getId() + "/abandon"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABANDONED"));
    assertEquals("ABANDONED", session.getStatus());
    assertNotNull(session.getCompletedAt());
  }
}
