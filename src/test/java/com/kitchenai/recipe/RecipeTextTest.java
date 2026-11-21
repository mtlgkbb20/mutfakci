package com.kitchenai.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecipeTextTest {
  @ParameterizedTest
  @CsvSource(value = {
    "Elmalı Kek|elmali kek",
    "  ÇİKOLATALI   SUFLE!! |cikolatali sufle",
    "Şöbiyet & Güllaç|sobiyet gullac",
    "IĞDIR usulü|igdir usulu",
    "kek-2 kişilik|kek 2 kisilik",
    "!!!|''",
  }, delimiter = '|')
  void normalizesTurkishTextForMatching(String input, String expected) {
    assertEquals(expected, RecipeText.normalize(input));
  }

  @org.junit.jupiter.api.Test
  void nullBecomesEmpty() {
    assertEquals("", RecipeText.normalize(null));
  }
}
