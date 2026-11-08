package com.kitchenai.ai;

import java.util.*;

public interface RecipeGenerationService {
  RecipeResult generate(RecipeRequest request);

  default GenerationTrace generateWithTrace(RecipeRequest request) {
    return new GenerationTrace(generate(request), null, List.of());
  }

  record RecipeRequest(
      String desiredFood,
      String conditions,
      String dietaryRestrictions,
      String availableIngredients,
      Integer servings) {}

  record RecipeResult(
      String title,
      String description,
      String productType,
      int servings,
      int preparationTime,
      int cookingTime,
      List<Ingredient> ingredients,
      List<Stage> stages) {}

  record Ingredient(String name, double quantity, String unit, boolean optional) {}

  record Stage(
      int order,
      String title,
      String instructions,
      String targetState,
      Integer temperature,
      Integer durationSeconds) {}

  record GenerationTrace(
      RecipeResult recipe,
      RecipeContracts.RecipeDecompositionResponse decomposition,
      List<RecipeContracts.RecipeComponentGenerationResponse> components) {}
}
