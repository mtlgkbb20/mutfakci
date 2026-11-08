package com.kitchenai.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StubRecipeGenerationServiceTest {
  @Test
  void returnsStructuredRecipeForNaturalLanguageRequest() {
    var r =
        new StubRecipeGenerationService()
            .generate(
                new RecipeGenerationService.RecipeRequest("elmalı kek", "soft", null, null, 4));
    assertFalse(r.title().isBlank());
    assertFalse(r.ingredients().isEmpty());
    assertFalse(r.stages().isEmpty());
    assertEquals(4, r.servings());
  }
}
