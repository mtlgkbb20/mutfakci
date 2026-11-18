package com.kitchenai.mealplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchenai.mealplan.MealPlanContracts.RecipeSummary;
import com.kitchenai.mealplan.MealPlanContracts.ShoppingListView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealPlanControllerTest {
  @Test
  void recipeAlternativesAreScopedToAuthenticatedPlanAndMealSlot() {
    MealPlanService service = mock(MealPlanService.class);
    MealPlanController controller = new MealPlanController(service);
    UUID planId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    List<RecipeSummary> expected = List.of();

    when(service.candidates(planId, userId, "DINNER")).thenReturn(expected);

    assertEquals(expected, controller.candidates(planId, "DINNER", userId));
    verify(service).candidates(planId, userId, "DINNER");
  }

  @Test
  void shoppingListIsRequestedForAuthenticatedOwner() {
    MealPlanService service = mock(MealPlanService.class);
    MealPlanController controller = new MealPlanController(service);
    UUID planId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    ShoppingListView expected = new ShoppingListView(planId, List.of(), List.of());

    when(service.shoppingList(planId, userId)).thenReturn(expected);

    assertEquals(expected, controller.shoppingList(planId, userId));
    verify(service).shoppingList(planId, userId);
  }
}
