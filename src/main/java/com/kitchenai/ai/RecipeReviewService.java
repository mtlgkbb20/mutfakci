package com.kitchenai.ai;

public interface RecipeReviewService {
  RecipeGenerationService.RecipeResult review(RecipeGenerationService.RecipeResult recipe);
}
