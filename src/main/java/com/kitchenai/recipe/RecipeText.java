package com.kitchenai.recipe;

import java.text.Normalizer;
import java.util.Locale;

/** One normalization rule shared by catalog lookup and import. */
public final class RecipeText {
  private RecipeText() {}

  public static String normalize(String value) {
    if (value == null) return "";
    String s = value.toLowerCase(Locale.forLanguageTag("tr-TR")).replace('ı', 'i');
    s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return s.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
  }
}
