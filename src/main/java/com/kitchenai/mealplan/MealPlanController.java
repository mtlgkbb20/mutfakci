package com.kitchenai.mealplan;

import com.kitchenai.mealplan.MealPlanContracts.CreateRequest;
import com.kitchenai.mealplan.MealPlanContracts.PlanView;
import com.kitchenai.mealplan.MealPlanContracts.ReplaceEntryRequest;
import com.kitchenai.mealplan.MealPlanContracts.ShoppingListView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meal-plans")
public class MealPlanController {
  private final MealPlanService service;

  public MealPlanController(MealPlanService service) {
    this.service = service;
  }

  @PostMapping
  public PlanView create(@Valid @RequestBody CreateRequest request,
      @AuthenticationPrincipal UUID userId) {
    return service.create(request, userId);
  }

  @GetMapping("/recipes")
  public List<com.kitchenai.mealplan.MealPlanContracts.RecipeSummary> candidates(
      @RequestParam(defaultValue = "60") int maxPreparationMinutes) {
    return service.candidates(Math.max(1, Math.min(1440, maxPreparationMinutes)));
  }

  @GetMapping
  public List<PlanView> list(@AuthenticationPrincipal UUID userId) {
    return service.list(userId);
  }

  @GetMapping("/{planId}")
  public PlanView get(@PathVariable UUID planId, @AuthenticationPrincipal UUID userId) {
    return service.get(planId, userId);
  }

  @PatchMapping("/{planId}/entries/{entryId}")
  public PlanView replaceEntry(@PathVariable UUID planId, @PathVariable UUID entryId,
      @Valid @RequestBody ReplaceEntryRequest request, @AuthenticationPrincipal UUID userId) {
    return service.replaceEntry(planId, entryId, request, userId);
  }

  @GetMapping("/{planId}/shopping-list")
  public ShoppingListView shoppingList(@PathVariable UUID planId,
      @AuthenticationPrincipal UUID userId) {
    return service.shoppingList(planId, userId);
  }
}
