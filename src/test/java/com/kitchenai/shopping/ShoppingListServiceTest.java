package com.kitchenai.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchenai.mealplan.MealPlanService;
import com.kitchenai.pantry.PantryInventoryService;
import com.kitchenai.recipe.RecipeCatalogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {
  @Mock ShoppingListRepository lists;
  @Mock ShoppingListItemRepository items;
  @Mock RecipeCatalogService catalog;
  @Mock MealPlanService mealPlans;
  @Mock PantryInventoryService pantry;
  @InjectMocks ShoppingListService service;

  @Test
  void createsPersistedRecipeListWithScaledAmountsAndPantryCoverage() {
    UUID userId = UUID.randomUUID();
    var recipe = new RecipeCatalogService.CatalogRecipe(1, "pancake", "Pancake", List.of(), "tr",
        "BREAKFAST", List.of(), 2, 20,
        List.of(new RecipeCatalogService.Ingredient("Süt", 200, "ml", ""),
            new RecipeCatalogService.Ingredient("Un", 100, "g", "")),
        List.of(), "");
    when(catalog.all()).thenReturn(List.of(recipe));
    when(pantry.activeStock(userId)).thenReturn(List.of(
        new PantryInventoryService.StockLine("sut", new BigDecimal("100"), "ml", null)));
    when(lists.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(items.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.create(new ShoppingListContracts.CreateRequest("Kahvaltı", null,
        List.of(new ShoppingListContracts.RecipeSelection("pancake", 4))), userId);

    assertEquals("ACTIVE", result.status());
    assertEquals(2, result.items().size());
    var milk = result.items().stream().filter(item -> item.name().equals("Süt")).findFirst().orElseThrow();
    assertEquals(new BigDecimal("400.000"), milk.requiredQuantity());
    assertEquals(new BigDecimal("100.000"), milk.pantryCoveredQuantity());
    assertEquals(new BigDecimal("300.000"), milk.toBuyQuantity());
    assertEquals(false, result.costEstimateAvailable());
    assertNotNull(result.estimatedTotal());
  }

  @Test
  void completesOnlyCheckedPurchasedLinesAndUsesIdempotentPantryTransfer() {
    UUID userId = UUID.randomUUID();
    UUID listId = UUID.randomUUID();
    ShoppingList list = new ShoppingList(userId, "Haftalık alışveriş", "RECIPE", null, "TRY");
    ShoppingListItem item = new ShoppingListItem(listId, "Un", "un", "BAKING_PANTRY",
        new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500"), "g");
    item.update("Un", "un", "BAKING_PANTRY", new BigDecimal("500"), new BigDecimal("300"), true);
    when(lists.lockOwned(listId, userId)).thenReturn(Optional.of(list));
    when(items.findByListIdOrderByCategoryAscNameAsc(listId)).thenReturn(List.of(item));
    when(pantry.applyPurchase(eq(userId), any(String.class), anyList())).thenReturn(true);
    when(lists.save(any(ShoppingList.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.complete(listId, new ShoppingListContracts.CompleteRequest("purchase-123"), userId);

    assertEquals("APPLIED", result.status());
    assertEquals(1, result.pantryLinesAdded());
    assertEquals("COMPLETED", list.getStatus());
    verify(pantry).applyPurchase(eq(userId), any(String.class), anyList());
    verify(lists).save(list);
  }
}
