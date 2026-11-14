package com.kitchenai.shopping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShoppingListContracts {
  private ShoppingListContracts() {}

  public record RecipeSelection(@NotBlank @Size(max = 120) String recipeCatalogId,
      @Min(1) @Max(30) int servings) {}

  public record CreateRequest(@Size(max = 160) String title, UUID mealPlanId,
      List<@Valid RecipeSelection> recipes) {}

  public record AddItemRequest(@NotBlank @Size(max = 160) String name,
      @NotNull @DecimalMin("0.001") BigDecimal quantity, @NotBlank @Size(max = 20) String unit,
      @Size(max = 60) String category) {}

  public record UpdateListRequest(@NotBlank @Size(max = 160) String title) {}

  public record UpdateItemRequest(@Size(max = 160) String name, @Size(max = 60) String category,
      @DecimalMin("0.001") BigDecimal toBuyQuantity,
      @DecimalMin("0.000") BigDecimal purchasedQuantity, Boolean checked) {}

  public record CompleteRequest(@NotBlank @Size(max = 60) String operationKey) {}

  public record ItemView(UUID id, String name, String category, BigDecimal requiredQuantity,
      BigDecimal pantryCoveredQuantity, BigDecimal toBuyQuantity, BigDecimal purchasedQuantity,
      String unit, boolean checked, long version) {}

  public record ListView(UUID id, String title, String sourceType, UUID sourceMealPlanId,
      String status, String currency, List<ItemView> items, boolean costEstimateAvailable,
      BigDecimal estimatedTotal, List<String> warnings, Instant createdAt, Instant updatedAt) {}

  public record CompletionView(String status, UUID shoppingListId, int pantryLinesAdded,
      String operationKey) {}
}
