package com.kitchenai.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShoppingListCalculatorTest {
  @Test
  void aggregatesNormalizedNamesAndScalesCompatibleUnits() {
    var lines = ShoppingListCalculator.calculate(List.of(
        new ShoppingListCalculator.IngredientInput("Süt", new BigDecimal("0.5"), "l"),
        new ShoppingListCalculator.IngredientInput("sut", new BigDecimal("250"), "ml"),
        new ShoppingListCalculator.IngredientInput("Un", new BigDecimal("200"), "g")
    ), List.of());
    var milk = lines.stream().filter(line -> line.normalizedName().equals("sut")).findFirst().orElseThrow();
    assertEquals(new BigDecimal("750.000"), milk.requiredQuantity());
    assertEquals("ml", milk.unit());
  }

  @Test
  void subtractsOnlyUnexpiredStockAndNeverGoesNegative() {
    var lines = ShoppingListCalculator.calculate(List.of(
        new ShoppingListCalculator.IngredientInput("Un", new BigDecimal("500"), "g")
    ), List.of(
        new ShoppingListCalculator.StockInput("un", new BigDecimal("200"), "g", false),
        new ShoppingListCalculator.StockInput("un", new BigDecimal("900"), "g", true)
    ));
    assertEquals(new BigDecimal("200.000"), lines.get(0).pantryCoveredQuantity());
    assertEquals(new BigDecimal("300.000"), lines.get(0).toBuyQuantity());
  }

  @Test
  void doesNotMergeDifferentDimensions() {
    var lines = ShoppingListCalculator.calculate(List.of(
        new ShoppingListCalculator.IngredientInput("Süt", new BigDecimal("250"), "ml"),
        new ShoppingListCalculator.IngredientInput("Süt", new BigDecimal("1"), "piece")
    ), List.of());
    assertEquals(2, lines.size());
  }

  @Test
  void capsPantryCoverageAtRequiredAmount() {
    var lines = ShoppingListCalculator.calculate(List.of(
        new ShoppingListCalculator.IngredientInput("Yumurta", new BigDecimal("2"), "adet")
    ), List.of(
        new ShoppingListCalculator.StockInput("Yumurta", new BigDecimal("5"), "piece", false)
    ));
    assertEquals(BigDecimal.ZERO.setScale(3), lines.get(0).toBuyQuantity());
    assertEquals(new BigDecimal("2.000"), lines.get(0).pantryCoveredQuantity());
  }
}
