package com.kitchenai.mealplan;

import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MealPlanAiSelector {
  Optional<Map<String, String>> select(
      MealPlanContracts.CreateRequest request, List<CatalogRecipe> candidates, List<String> mealSlots);
}
