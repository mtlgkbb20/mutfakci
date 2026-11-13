package com.kitchenai.recipe;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RecipeCatalogServiceTest {
  private final RecipeCatalogService catalog = new RecipeCatalogService(new ObjectMapper());

  @Test
  void exactAndAliasMatchTheCuratedRecipe() {
    assertEquals("kisnisli-mercimek-corbasi", catalog.find("Havuçlu Tarçınlı Kek").orElseThrow().id());
    assertEquals("kisnisli-mercimek-corbasi", catalog.find("  HAVUÇLU kek! ").orElseThrow().id());
  }

  @Test
  void everyFeaturedRecipeHasARealSourceAndDoesNotInventServingsOrTime() {
    assertEquals(4, catalog.findAll().size());
    catalog.findAll().forEach(recipe -> {
      assertTrue(recipe.sourceUrl().startsWith("https://"));
      assertFalse(recipe.sourceName().isBlank());
      assertFalse(recipe.sourceAuthor().isBlank());
      assertEquals(0, recipe.defaultServings());
      assertEquals(0, recipe.estimatedTotalMinutes());
    });
  }

  @Test
  void weakRequestDoesNotInventARecipe() {
    assertTrue(catalog.find("kek yapmak istiyorum").isEmpty());
  }
}
