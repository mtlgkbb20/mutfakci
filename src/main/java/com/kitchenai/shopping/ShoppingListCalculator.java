package com.kitchenai.shopping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShoppingListCalculator {
  public record IngredientInput(String name, BigDecimal quantity, String unit) {}
  public record StockInput(String name, BigDecimal quantity, String unit, boolean expired) {}
  public record CalculatedLine(String name, String normalizedName, String category,
      BigDecimal requiredQuantity, BigDecimal pantryCoveredQuantity, BigDecimal toBuyQuantity, String unit) {}

  private ShoppingListCalculator() {}

  public static List<CalculatedLine> calculate(List<IngredientInput> ingredients, List<StockInput> stock) {
    Map<String, Accumulator> totals = new LinkedHashMap<>();
    for (IngredientInput ingredient : ingredients) {
      if (ingredient.quantity() == null || ingredient.quantity().signum() <= 0) continue;
      String normalized = normalize(ingredient.name());
      String unit = canonicalUnit(ingredient.unit());
      String key = normalized + "|" + unit;
      Accumulator acc = totals.computeIfAbsent(key,
          ignored -> new Accumulator(ingredient.name(), normalized, GroceryCategory.forIngredient(normalized), unit));
      acc.required = acc.required.add(toCanonical(ingredient.quantity(), ingredient.unit()));
    }

    Map<String, BigDecimal> available = new LinkedHashMap<>();
    for (StockInput item : stock) {
      if (item.expired() || item.quantity() == null || item.quantity().signum() <= 0) continue;
      String key = normalize(item.name()) + "|" + canonicalUnit(item.unit());
      available.merge(key, toCanonical(item.quantity(), item.unit()), BigDecimal::add);
    }

    List<CalculatedLine> result = new ArrayList<>();
    for (Map.Entry<String, Accumulator> entry : totals.entrySet()) {
      Accumulator acc = entry.getValue();
      BigDecimal required = acc.required.setScale(3, RoundingMode.HALF_UP);
      BigDecimal covered = available.getOrDefault(entry.getKey(), BigDecimal.ZERO)
          .min(required).setScale(3, RoundingMode.HALF_UP);
      BigDecimal toBuy = required.subtract(covered).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
      result.add(new CalculatedLine(acc.name, acc.normalized, acc.category, required, covered, toBuy,
          displayUnit(acc.unit)));
    }
    return result.stream().sorted(java.util.Comparator.comparing(CalculatedLine::category)
        .thenComparing(CalculatedLine::name, String.CASE_INSENSITIVE_ORDER)).toList();
  }

  public static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ");
  }

  private static String canonicalUnit(String value) {
    String unit = normalize(value).replace(" ", "");
    return switch (unit) {
      case "kg" -> "g";
      case "l" -> "ml";
      case "adet", "pcs", "piece" -> "piece";
      default -> unit;
    };
  }

  private static BigDecimal toCanonical(BigDecimal quantity, String unit) {
    return switch (normalize(unit).replace(" ", "")) {
      case "kg", "l" -> quantity.multiply(BigDecimal.valueOf(1000));
      default -> quantity;
    };
  }

  private static String displayUnit(String unit) {
    return switch (unit) {
      case "piece" -> "adet";
      case "g" -> "g";
      case "ml" -> "ml";
      case "subardagi" -> "su bardağı";
      case "yemekkasigi" -> "yemek kaşığı";
      case "caykasigi" -> "çay kaşığı";
      case "t atlikasigi", "tatlikasigi" -> "tatlı kaşığı";
      default -> unit;
    };
  }

  private static final class Accumulator {
    final String name;
    final String normalized;
    final String category;
    final String unit;
    BigDecimal required = BigDecimal.ZERO;
    Accumulator(String name, String normalized, String category, String unit) {
      this.name = name; this.normalized = normalized; this.category = category; this.unit = unit;
    }
  }
}
