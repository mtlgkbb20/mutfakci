package com.kitchenai.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.kitchenai.common.ApiException;
import com.kitchenai.mealplan.MealPlanService;
import com.kitchenai.pantry.PantryInventoryService;
import com.kitchenai.recipe.RecipeCatalogService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceValidationTest {
  @Mock ShoppingListRepository lists;
  @Mock ShoppingListItemRepository items;
  @Mock RecipeCatalogService catalog;
  @Mock MealPlanService mealPlans;
  @Mock PantryInventoryService pantry;
  @InjectMocks ShoppingListService service;

  @Test
  void rejectsCreateRequestWithoutAnySource() {
    assertThrows(ApiException.class,
        () -> service.create(new ShoppingListContracts.CreateRequest("Liste", null, null), UUID.randomUUID()));
    verifyNoInteractions(catalog, mealPlans, pantry, lists, items);
  }

  @Test
  void rejectsCreateRequestWithBothMealPlanAndRecipeSources() {
    var request = new ShoppingListContracts.CreateRequest("Liste", UUID.randomUUID(),
        List.of(new ShoppingListContracts.RecipeSelection("recipe-1", 2)));

    assertThrows(ApiException.class, () -> service.create(request, UUID.randomUUID()));
    verifyNoInteractions(catalog, mealPlans, pantry, lists, items);
  }

  @Test
  void rejectsMoreThanThirtyRecipesBeforeCatalogOrPantryAccess() {
    List<ShoppingListContracts.RecipeSelection> selections =
        java.util.stream.IntStream.range(0, 31)
            .mapToObj(i -> new ShoppingListContracts.RecipeSelection("recipe-" + i, 2)).toList();

    assertThrows(ApiException.class,
        () -> service.create(new ShoppingListContracts.CreateRequest("Liste", null, selections), UUID.randomUUID()));
    verifyNoInteractions(catalog, mealPlans, pantry, lists, items);
  }

  @Test
  void calculatorNormalizesTurkishDiacriticsAndPunctuationDeterministically() {
    assertEquals("sut", ShoppingListCalculator.normalize("  SÜT! "));
    assertEquals("kirmizi biber", ShoppingListCalculator.normalize("Kırmızı   Biber"));
  }

  @Test
  void calculatorDoesNotDeductIncompatibleUnitsWithSameIngredientName() {
    var result = ShoppingListCalculator.calculate(
        List.of(new ShoppingListCalculator.IngredientInput("Süt", new java.math.BigDecimal("1"), "l")),
        List.of(new ShoppingListCalculator.StockInput("Süt", new java.math.BigDecimal("1"), "piece", false)));

    assertEquals(new java.math.BigDecimal("1000.000"), result.get(0).requiredQuantity());
    assertEquals(java.math.BigDecimal.ZERO.setScale(3), result.get(0).pantryCoveredQuantity());
    assertEquals(new java.math.BigDecimal("1000.000"), result.get(0).toBuyQuantity());
  }
}
