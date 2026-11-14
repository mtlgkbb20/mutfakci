package com.kitchenai.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    assertNull(result.estimatedTotal());
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

  @Test
  void hidesListsOwnedByAnotherUser() {
    UUID otherUserId = UUID.randomUUID();
    UUID listId = UUID.randomUUID();
    when(lists.findByIdAndUserId(listId, otherUserId)).thenReturn(Optional.empty());

    assertThrows(com.kitchenai.common.ApiException.class, () -> service.get(listId, otherUserId));

    verify(items, never()).findByListIdOrderByCategoryAscNameAsc(any());
    verify(lists).findByIdAndUserId(listId, otherUserId);
  }

  @Test
  void rejectsMutationsWhenListIsNotOwnedByCaller() {
    UUID listId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    when(lists.lockOwned(listId, otherUserId)).thenReturn(Optional.empty());

    assertThrows(com.kitchenai.common.ApiException.class,
        () -> service.rename(listId, new ShoppingListContracts.UpdateListRequest("Yeni ad"), otherUserId));

    verify(lists, never()).save(any(ShoppingList.class));
    verifyNoInteractions(items);
  }

  @Test
  void completionReplayWithSameOperationKeyDoesNotTouchPantryAgain() {
    UUID userId = UUID.randomUUID();
    UUID listId = UUID.randomUUID();
    ShoppingList list = new ShoppingList(userId, "Alışveriş", "RECIPE", null, "TRY");
    list.complete("purchase-replay");
    when(lists.lockOwned(listId, userId)).thenReturn(Optional.of(list));

    var result = service.complete(listId,
        new ShoppingListContracts.CompleteRequest("purchase-replay"), userId);

    assertEquals("ALREADY_COMPLETED", result.status());
    assertEquals(0, result.pantryLinesAdded());
    verifyNoInteractions(pantry, items);
    verify(lists, never()).save(any(ShoppingList.class));
  }

  @Test
  void rejectsCompletionReplayWithDifferentOperationKey() {
    UUID userId = UUID.randomUUID();
    UUID listId = UUID.randomUUID();
    ShoppingList list = new ShoppingList(userId, "Alışveriş", "RECIPE", null, "TRY");
    list.complete("original-operation");
    when(lists.lockOwned(listId, userId)).thenReturn(Optional.of(list));

    assertThrows(com.kitchenai.common.ApiException.class,
        () -> service.complete(listId,
            new ShoppingListContracts.CompleteRequest("different-operation"), userId));

    verifyNoInteractions(pantry, items);
  }

  @Test
  void doesNotApplyPurchaseWhenNoItemsAreChecked() {
    UUID userId = UUID.randomUUID();
    UUID listId = UUID.randomUUID();
    ShoppingList list = new ShoppingList(userId, "Alışveriş", "RECIPE", null, "TRY");
    ShoppingListItem unchecked = new ShoppingListItem(listId, "Un", "un", "BAKING_PANTRY",
        new BigDecimal("500"), BigDecimal.ZERO, new BigDecimal("500"), "g");
    when(lists.lockOwned(listId, userId)).thenReturn(Optional.of(list));
    when(items.findByListIdOrderByCategoryAscNameAsc(listId)).thenReturn(List.of(unchecked));

    assertThrows(com.kitchenai.common.ApiException.class,
        () -> service.complete(listId,
            new ShoppingListContracts.CompleteRequest("empty-purchase"), userId));

    verifyNoInteractions(pantry);
    verify(lists, never()).save(any(ShoppingList.class));
  }

  @Test
  void rejectsCompletionForAListOwnedByAnotherUserWithoutLoadingItems() {
    UUID listId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    when(lists.lockOwned(listId, otherUserId)).thenReturn(Optional.empty());

    assertThrows(com.kitchenai.common.ApiException.class,
        () -> service.complete(listId,
            new ShoppingListContracts.CompleteRequest("foreign-purchase"), otherUserId));

    verifyNoInteractions(items, pantry);
  }
}
