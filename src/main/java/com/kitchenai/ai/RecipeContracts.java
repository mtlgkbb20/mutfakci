package com.kitchenai.ai;

import java.util.List;

/** Typed boundaries used by providers and kept out of the public recipe response. */
public final class RecipeContracts {
  private RecipeContracts() {}

  public record ComponentPlan(
      String key, String name, String purpose, int order, List<String> dependsOn) {}

  public record RecipeDecompositionResponse(
      String dishTitle, int servings, List<ComponentPlan> components, boolean assemblyRequired) {}

  public record ComponentIngredient(
      String name, double quantity, String unit, String preparation) {}

  public record ComponentInstruction(
      int order,
      String instruction,
      String completionCue,
      Integer temperature,
      Integer durationSeconds) {}

  public record RecipeComponentGenerationResponse(
      String componentKey,
      String title,
      List<ComponentIngredient> ingredients,
      List<ComponentInstruction> instructions) {}
}
