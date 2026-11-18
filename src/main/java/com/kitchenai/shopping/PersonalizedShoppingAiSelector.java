package com.kitchenai.shopping;

import com.kitchenai.pantry.PantryInventoryService.StockLine;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import java.util.List;

public interface PersonalizedShoppingAiSelector {
  record PastDish(String title, Integer rating, String notes, String cookedAt) {}
  record Recommendation(String recipeCatalogId, String reason) {}

  boolean available();
  List<Recommendation> recommend(List<PastDish> history, List<StockLine> pantry,
      List<CatalogRecipe> candidates);
}
