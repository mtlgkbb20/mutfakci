package com.kitchenai.ai;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

class StubRecipeReviewServiceTest {
  @Test
  void stubReviewReturnsTheRecipeUnchanged() {
    var recipe = new RecipeGenerationService.RecipeResult("t", "d", "p", 1, 1, 1, List.of(), List.of());
    assertSame(recipe, new StubRecipeReviewService().review(recipe));
  }
}
