package com.kitchenai.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical ingredient-name normalization shared by pantry, zero-waste and the ingredient alias
 * table. Punctuation is treated as a word separator so "Domates-salçası" and "Domates salçası"
 * collapse to the same key ("domates salcasi").
 */
public final class IngredientNames {
  private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

  private IngredientNames() {}

  public static String normalize(String value) {
    if (value == null) return "";
    String lower = value.trim().toLowerCase(TURKISH).replace('ı', 'i');
    return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
  }
}
