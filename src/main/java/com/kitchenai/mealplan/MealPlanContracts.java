package com.kitchenai.mealplan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MealPlanContracts {
  private MealPlanContracts() {}

  public record CreateRequest(
      @NotNull LocalDate weekStart,
      @Min(1) @Max(30) int servings,
      @NotEmpty List<@Pattern(regexp = "BREAKFAST|LUNCH|DINNER") String> mealSlots,
      @Min(1) @Max(1440) int maxPreparationMinutes,
      @DecimalMin("0.01") BigDecimal weeklyBudget,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      List<@NotBlank @Size(max = 80) String> dietaryRestrictions,
      @Size(max = 500) String goal,
      @Size(max = 2000) String notes) {}

  public record ReplaceEntryRequest(
      @NotBlank @Size(max = 120) String recipeCatalogId,
      @Min(1) @Max(30) int servings,
      @Size(max = 500) String note) {}

  public record RecipeSummary(
      String id, String title, String category, int servings,
      int estimatedTotalMinutes, List<IngredientView> ingredients) {}

  public record IngredientView(String name, double quantity, String unit, String preparation) {}

  public record EntryView(
      UUID id, LocalDate date, String mealSlot, String recipeCatalogId,
      int servings, String note, RecipeSummary recipe) {}

  public record PlanView(
      UUID id, LocalDate weekStart, int servings, List<String> mealSlots,
      int maxPreparationMinutes, BigDecimal weeklyBudget, String currency,
      List<String> dietaryRestrictions, String goal, String notes,
      List<EntryView> entries, List<String> warnings, Instant createdAt, Instant updatedAt) {}

  public record ShoppingLine(
      String name, BigDecimal requiredQuantity, BigDecimal pantryCoveredQuantity,
      BigDecimal toBuyQuantity, String unit) {}

  public record ShoppingListView(UUID planId, List<ShoppingLine> items, List<String> warnings) {}
}
