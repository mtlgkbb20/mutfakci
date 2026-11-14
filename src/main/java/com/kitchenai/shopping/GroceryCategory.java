package com.kitchenai.shopping;

import java.util.Map;

final class GroceryCategory {
  private GroceryCategory() {}
  private static final Map<String, String> EXACT = Map.ofEntries(
      Map.entry("sut", "DAIRY"), Map.entry("yumurta", "DAIRY"), Map.entry("tereyagi", "DAIRY"),
      Map.entry("peynir", "DAIRY"), Map.entry("krema", "DAIRY"), Map.entry("labne", "DAIRY"),
      Map.entry("yogurt", "DAIRY"), Map.entry("tavuk", "MEAT_FISH"), Map.entry("et", "MEAT_FISH"),
      Map.entry("kiyma", "MEAT_FISH"), Map.entry("balik", "MEAT_FISH"), Map.entry("somon", "MEAT_FISH"),
      Map.entry("domates", "FRUITS_VEGETABLES"), Map.entry("sogan", "FRUITS_VEGETABLES"),
      Map.entry("patates", "FRUITS_VEGETABLES"), Map.entry("havuç", "FRUITS_VEGETABLES"),
      Map.entry("havuc", "FRUITS_VEGETABLES"), Map.entry("elma", "FRUITS_VEGETABLES"),
      Map.entry("limon", "FRUITS_VEGETABLES"), Map.entry("maydanoz", "FRUITS_VEGETABLES"),
      Map.entry("salatalik", "FRUITS_VEGETABLES"), Map.entry("biber", "FRUITS_VEGETABLES"),
      Map.entry("un", "BAKING_PANTRY"), Map.entry("seker", "BAKING_PANTRY"),
      Map.entry("pirinc", "BAKING_PANTRY"), Map.entry("makarna", "BAKING_PANTRY"),
      Map.entry("yulaf", "BAKING_PANTRY"), Map.entry("zeytinyagi", "OILS_CONDIMENTS"),
      Map.entry("tuz", "OILS_CONDIMENTS"), Map.entry("karabiber", "OILS_CONDIMENTS"),
      Map.entry("tarçın", "OILS_CONDIMENTS"), Map.entry("tar cin", "OILS_CONDIMENTS")
  );

  static String forIngredient(String normalized) {
    String exact = EXACT.get(normalized);
    if (exact != null) return exact;
    if (normalized.contains("tarçin") || normalized.contains("baharat") || normalized.contains("kimyon")) return "OILS_CONDIMENTS";
    return "OTHER";
  }
}
