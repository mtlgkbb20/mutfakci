package com.kitchenai.mealplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class RunwareMealPlanAiSelector implements MealPlanAiSelector {
  private static final Logger log = LoggerFactory.getLogger(RunwareMealPlanAiSelector.class);
  private static final String API_URL = "https://api.runware.ai/v1/chat/completions";
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final String apiKey;
  private final String model;

  public RunwareMealPlanAiSelector(ObjectMapper mapper, Environment environment) {
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiKey = environment.getProperty("app.ai.runware.api-key", "");
    this.model = environment.getProperty("app.ai.runware.model", "deepseek:v4.1@flash");
  }

  @Override
  public Optional<Map<String, String>> select(
      MealPlanContracts.CreateRequest request, List<CatalogRecipe> candidates, List<String> mealSlots) {
    if (apiKey.isBlank()) return Optional.empty();
    try {
      String candidateJson = mapper.writeValueAsString(candidates.stream()
          .map(recipe -> Map.of("id", recipe.id(), "title", recipe.title(),
              "category", recipe.category(), "estimatedTotalMinutes", recipe.estimatedTotalMinutes()))
          .toList());
      String prompt = """
          You are selecting recipes for a Turkish user's weekly meal plan.
          Return JSON only in this exact shape:
          {"selections":[{"dayOffset":0,"mealSlot":"BREAKFAST","recipeCatalogId":"existing-id"}]}
          Create one selection for every dayOffset from 0 through 6 and every requested meal slot.
          Only select recipeCatalogId values from the supplied candidate list. Never invent IDs,
          recipes, ingredients, nutrition facts, prices, or cooking instructions.
          Respect the preparation-time limit and dietary restrictions by choosing only from
          the pre-filtered candidates. Avoid repeating the same recipe when alternatives exist.
          Consider the user's goal and notes when ranking candidates.
          Requested servings: %d
          Meal slots: %s
          Maximum preparation minutes: %d
          Weekly budget: %s
          Currency: %s
          Dietary restrictions: %s
          Goal: %s
          Notes: %s
          Candidate recipes: %s
          """.formatted(request.servings(), mealSlots, request.maxPreparationMinutes(),
          request.weeklyBudget() == null ? "not specified" : request.weeklyBudget().toPlainString(),
          request.currency() == null ? "TRY" : request.currency(),
          request.dietaryRestrictions(), request.goal(), request.notes(), candidateJson);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", model);
      body.put("max_completion_tokens", 1600);
      body.put("reasoning_effort", "none");
      body.put("messages", List.of(
          Map.of("role", "system", "content", "Return only valid JSON. Select only supplied recipe IDs."),
          Map.of("role", "user", "content", prompt)));
      body.put("response_format", Map.of("type", "json_object"));

      HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(API_URL))
          .timeout(Duration.ofSeconds(45))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn("Meal planner provider returned status={}", response.statusCode());
        return Optional.empty();
      }
      JsonNode root = mapper.readTree(response.body());
      String modelText = root.path("choices").path(0).path("message").path("content").asText("");
      JsonNode payload = mapper.readTree(modelText);
      JsonNode selections = payload.path("selections");
      if (!selections.isArray()) return Optional.empty();
      Map<String, String> result = new LinkedHashMap<>();
      for (JsonNode selection : selections) {
        int dayOffset = selection.path("dayOffset").asInt(-1);
        String slot = selection.path("mealSlot").asText("");
        String recipeId = selection.path("recipeCatalogId").asText("");
        if (dayOffset < 0 || dayOffset > 6 || slot.isBlank() || recipeId.isBlank()) return Optional.empty();
        String key = dayOffset + ":" + slot;
        if (result.putIfAbsent(key, recipeId) != null) return Optional.empty();
      }
      return Optional.of(result);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Meal planner provider call was interrupted");
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Meal planner provider output unavailable type={}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
