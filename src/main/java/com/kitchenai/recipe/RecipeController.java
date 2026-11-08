package com.kitchenai.recipe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.ai.RecipeGenerationService;
import com.kitchenai.ai.RecipeReviewService;
import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.gamification.UserProgress;
import com.kitchenai.gamification.UserProgressRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecipeController {
  private static final Logger log = LoggerFactory.getLogger(RecipeController.class);
  private final RecipeRepository recipes;
  private final CookingSessionRepository sessions;
  private final RecipeGenerationService ai;
  private final ObjectMapper json;
  private final UserProgressRepository progress;
  private final RecipeCatalogService catalog;
  private final RecipeGenerationRepository generations;
  private final RecipeReviewService reviewer;

  public RecipeController(
      RecipeRepository recipes,
      CookingSessionRepository sessions,
      RecipeGenerationService ai,
      ObjectMapper json,
      UserProgressRepository progress,
      RecipeCatalogService catalog,
      RecipeGenerationRepository generations,
      RecipeReviewService reviewer) {
    this.recipes = recipes;
    this.sessions = sessions;
    this.ai = ai;
    this.json = json;
    this.progress = progress;
    this.catalog = catalog;
    this.generations = generations;
    this.reviewer = reviewer;
  }

  public record GenerateRequest(
      @NotBlank String desiredFood,
      String conditions,
      String dietaryRestrictions,
      String availableIngredients,
      @Min(1) @Max(30) Integer servings) {}

  public record SessionRequest(UUID recipeId, String desiredOutcome) {}

  @PostMapping("/recipes/generate")
  public RecipeView generate(
      @Valid @RequestBody GenerateRequest request, @AuthenticationPrincipal UUID userId) {
    RecipeGeneration generation = null;
    try {
      var match = catalog.find(request.desiredFood());
      if (match.isPresent()) {
        log.info("Recipe generation source=CURATED catalogId={}", match.get().id());
        return curatedRecipe(match.get(), request.servings());
      }

      String normalizedRequest = RecipeText.normalize(request.desiredFood());
      var cached =
          generations.findTop1ByUserIdAndNormalizedDesiredFoodAndStatusOrderByCreatedAtDesc(
              userId, normalizedRequest, "COMPLETED");
      if (cached.isPresent() && cached.get().getRecipeId() != null) {
        var cachedRecipe =
            recipes
                .findById(cached.get().getRecipeId())
                .filter(recipe -> recipe.getUserId() != null && recipe.getUserId().equals(userId));
        if (cachedRecipe.isPresent()) {
          log.info(
              "Recipe generation source=AI_CACHE generationId={} recipeId={}",
              cached.get().getId(),
              cached.get().getRecipeId());
          return view(cachedRecipe.get());
        }
      }

      log.info("Recipe generation source=AI_GENERATED provider=runware");
      generation =
          generations.save(
              new RecipeGeneration(
                  userId, request.desiredFood(), normalizedRequest, "AI_GENERATED", "runware"));
      var trace =
          ai.generateWithTrace(
              new RecipeGenerationService.RecipeRequest(
                  request.desiredFood(),
                  request.conditions(),
                  request.dietaryRestrictions(),
                  request.availableIngredients(),
                  request.servings()));
      var result = reviewer.review(trace.recipe());
      log.info(
          "Recipe generation chef review completed title={} stageCount={}",
          result.title(),
          result.stages().size());
      var saved =
          recipes.save(
              new Recipe(
                  userId,
                  null,
                  "AI_GENERATED",
                  result.title(),
                  result.description(),
                  result.productType(),
                  result.servings(),
                  result.preparationTime(),
                  result.cookingTime(),
                  json.writeValueAsString(result.ingredients()),
                  json.writeValueAsString(result.stages())));
      RecipeView response = view(saved);
      generation.complete(
          saved.getId(),
          json.writeValueAsString(trace.decomposition()),
          json.writeValueAsString(trace.components()),
          json.writeValueAsString(response));
      generations.save(generation);
      return response;
    } catch (JsonProcessingException e) {
      markGenerationFailed(generation, e);
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Recipe serialization failed");
    } catch (ApiException e) {
      markGenerationFailed(generation, e);
      throw e;
    } catch (Exception e) {
      markGenerationFailed(generation, e);
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Recipe generation failed");
    }
  }

  private void markGenerationFailed(RecipeGeneration generation, Exception exception) {
    if (generation == null) {
      return;
    }
    try {
      generation.fail(
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage());
      generations.save(generation);
    } catch (Exception auditException) {
      log.error("Could not persist failed recipe generation audit", auditException);
    }
  }

  private RecipeView curatedRecipe(
      RecipeCatalogService.CatalogRecipe catalogRecipe, Integer requestedServings)
      throws JsonProcessingException {
    Optional<Recipe> existing = recipes.findByCatalogId(catalogRecipe.id());
    if (existing.isPresent()) {
      return view(existing.get());
    }

    var stages =
        catalogRecipe.steps().stream()
            .map(
                step ->
                    new RecipeStage(
                        step.order(),
                        step.title(),
                        step.instruction(),
                        step.completionCue(),
                        step.temperature(),
                        step.durationSeconds()))
            .toList();
    var saved =
        recipes.save(
            new Recipe(
                null,
                catalogRecipe.id(),
                "CURATED",
                catalogRecipe.title(),
                catalogRecipe.description(),
                catalogRecipe.category(),
                requestedServings == null ? catalogRecipe.defaultServings() : requestedServings,
                Math.max(1, catalogRecipe.estimatedTotalMinutes() / 3),
                Math.max(1, catalogRecipe.estimatedTotalMinutes() * 2 / 3),
                json.writeValueAsString(catalogRecipe.ingredients()),
                json.writeValueAsString(stages)));
    return view(saved);
  }

  @GetMapping("/recipes")
  public List<RecipeView> list(@AuthenticationPrincipal UUID userId) {
    return recipes.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
  }

  @GetMapping("/recipes/{id}")
  public RecipeView one(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    return view(owned(id, userId));
  }

  @PostMapping("/cooking-sessions")
  public CookingSession start(
      @Valid @RequestBody SessionRequest request, @AuthenticationPrincipal UUID userId) {
    if (request.recipeId() != null) {
      owned(request.recipeId(), userId);
    }
    return sessions.save(
        new CookingSession(
            userId,
            request.recipeId(),
            request.recipeId() == null ? "RECOVERY" : "RECIPE",
            request.desiredOutcome()));
  }

  @GetMapping("/cooking-sessions")
  public List<CookingSession> allSessions(@AuthenticationPrincipal UUID userId) {
    return sessions.findTop10ByUserIdOrderByStartedAtDesc(userId);
  }

  @GetMapping("/cooking-sessions/{id}")
  public CookingSession detail(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    return session(id, userId);
  }

  @PostMapping("/cooking-sessions/{id}/complete")
  public Map<String, Object> complete(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    var cookingSession = session(id, userId);
    cookingSession.complete();
    sessions.save(cookingSession);
    var userProgress =
        progress.findByUserId(userId).orElseGet(() -> progress.save(new UserProgress(userId)));
    userProgress.add(10);
    progress.save(userProgress);
    return Map.of("status", "COMPLETED", "xpAwarded", 10);
  }

  @PostMapping("/cooking-sessions/{id}/abandon")
  public Map<String, Object> abandon(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    var cookingSession = session(id, userId);
    cookingSession.abandon();
    sessions.save(cookingSession);
    return Map.of("status", "ABANDONED");
  }

  private Recipe owned(UUID id, UUID userId) {
    return recipes
        .findById(id)
        .filter(recipe -> recipe.getUserId() == null || recipe.getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"));
  }

  private CookingSession session(UUID id, UUID userId) {
    return sessions
        .findById(id)
        .filter(session -> session.getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
  }

  private RecipeView view(Recipe recipe) {
    return new RecipeView(
        recipe.getId(),
        recipe.getTitle(),
        recipe.getDescription(),
        recipe.getProductType(),
        recipe.getServings(),
        recipe.getPreparationTime(),
        recipe.getCookingTime(),
        recipe.getIngredientsJson(),
        recipe.getStagesJson(),
        recipe.getSourceType(),
        recipe.getCatalogId());
  }

  public record RecipeStage(
      int order,
      String title,
      String instructions,
      String targetState,
      Integer temperature,
      Integer durationSeconds) {}

  public record RecipeView(
      UUID id,
      String title,
      String description,
      String productType,
      int servings,
      int preparationTime,
      int cookingTime,
      String ingredients,
      String stages,
      String sourceType,
      String catalogId) {}
}
