package com.kitchenai.recipe;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RecipeCatalogServiceTest {
  private final RecipeCatalogService catalog = new RecipeCatalogService(new ObjectMapper());

  @Test
  void exactAndAliasMatchTheCuratedRecipe() {
    assertEquals("carrot-cinnamon-cake", catalog.find("Havuçlu Tarçınlı Kek").orElseThrow().id());
    assertEquals("carrot-cinnamon-cake", catalog.find("  HAVUÇLU kek! ").orElseThrow().id());
  }

  @Test
  void weakRequestDoesNotInventARecipe() {
    assertTrue(catalog.find("kek yapmak istiyorum").isEmpty());
  }
}
