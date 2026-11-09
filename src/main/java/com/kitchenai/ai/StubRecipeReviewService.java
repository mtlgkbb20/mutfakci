package com.kitchenai.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!runware")
public class StubRecipeReviewService implements RecipeReviewService {
  @Override
  public RecipeGenerationService.RecipeResult review(RecipeGenerationService.RecipeResult recipe) {
    return recipe;
  }
}
