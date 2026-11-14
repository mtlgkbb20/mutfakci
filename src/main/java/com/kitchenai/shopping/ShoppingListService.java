package com.kitchenai.shopping;

import com.kitchenai.common.ApiException;
import com.kitchenai.mealplan.MealPlanContracts.EntryView;
import com.kitchenai.mealplan.MealPlanContracts.PlanView;
import com.kitchenai.mealplan.MealPlanService;
import com.kitchenai.pantry.PantryInventoryService;
import com.kitchenai.pantry.PantryInventoryService.PurchaseLine;
import com.kitchenai.pantry.PantryInventoryService.StockLine;
import com.kitchenai.recipe.RecipeCatalogService;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.kitchenai.shopping.ShoppingListContracts.*;

@Service
public class ShoppingListService {
  private final ShoppingListRepository lists;
  private final ShoppingListItemRepository items;
  private final RecipeCatalogService catalog;
  private final MealPlanService mealPlans;
  private final PantryInventoryService pantry;

  public ShoppingListService(ShoppingListRepository lists, ShoppingListItemRepository items,
      RecipeCatalogService catalog, MealPlanService mealPlans, PantryInventoryService pantry) {
    this.lists = lists; this.items = items; this.catalog = catalog; this.mealPlans = mealPlans; this.pantry = pantry;
  }

  @Transactional
  public ListView create(CreateRequest request, UUID userId) {
    boolean fromPlan = request.mealPlanId() != null;
    boolean fromRecipes = request.recipes() != null && !request.recipes().isEmpty();
    if (fromPlan == fromRecipes) throw bad("Bir yemek planı veya en az bir tarif seçmelisin.");

    List<ShoppingListCalculator.IngredientInput> ingredients = new ArrayList<>();
    String sourceType;
    UUID sourcePlanId = null;
    if (fromPlan) {
      PlanView plan = mealPlans.get(request.mealPlanId(), userId);
      sourcePlanId = plan.id();
      sourceType = "MEAL_PLAN";
      for (EntryView entry : plan.entries()) {
        addRecipeIngredients(ingredients, findRecipe(entry.recipeCatalogId()), entry.servings());
      }
    } else {
      if (request.recipes().size() > 30) throw bad("Bir listede en fazla 30 tarif seçilebilir.");
      sourceType = request.recipes().size() == 1 ? "RECIPE" : "RECIPES";
      for (var selection : request.recipes()) {
        addRecipeIngredients(ingredients, findRecipe(selection.recipeCatalogId()), selection.servings());
      }
    }

    List<ShoppingListCalculator.StockInput> stock = pantry.activeStock(userId).stream()
        .map(line -> new ShoppingListCalculator.StockInput(line.name(), line.quantity(), line.unit(),
            line.expiryDate() != null && line.expiryDate().isBefore(LocalDate.now())))
        .toList();
    List<ShoppingListCalculator.CalculatedLine> calculated = ShoppingListCalculator.calculate(ingredients, stock);
    String title = request.title() == null || request.title().isBlank()
        ? "Alışveriş listem" : request.title().trim();
    ShoppingList list = lists.save(new ShoppingList(userId, title, sourceType, sourcePlanId, "TRY"));
    List<ShoppingListItem> rows = calculated.stream().map(line -> new ShoppingListItem(list.getId(),
        line.name(), line.normalizedName(), line.category(), line.requiredQuantity(),
        line.pantryCoveredQuantity(), line.toBuyQuantity(), line.unit())).toList();
    items.saveAll(rows);
    return view(list, rows);
  }

  @Transactional(readOnly = true)
  public List<ListView> list(UUID userId) {
    return lists.findTop50ByUserIdOrderByUpdatedAtDesc(userId).stream()
        .map(list -> view(list, items.findByListIdOrderByCategoryAscNameAsc(list.getId()))).toList();
  }

  @Transactional(readOnly = true)
  public ListView get(UUID listId, UUID userId) {
    ShoppingList list = owned(listId, userId);
    return view(list, items.findByListIdOrderByCategoryAscNameAsc(listId));
  }

  @Transactional
  public ListView rename(UUID listId, UpdateListRequest request, UUID userId) {
    ShoppingList list = activeOwned(listId, userId);
    list.rename(request.title().trim());
    lists.save(list);
    return get(listId, userId);
  }

  @Transactional
  public ListView addItem(UUID listId, AddItemRequest request, UUID userId) {
    ShoppingList list = activeOwned(listId, userId);
    String unit = normalizeUnit(request.unit());
    BigDecimal quantity = request.quantity().setScale(3, RoundingMode.HALF_UP);
    String normalized = ShoppingListCalculator.normalize(request.name());
    String category = normalizeCategory(request.category());
    ShoppingListItem item = items.save(new ShoppingListItem(listId, request.name().trim(), normalized,
        category, quantity, BigDecimal.ZERO.setScale(3), quantity, unit));
    return get(listId, userId);
  }

  @Transactional
  public ListView updateItem(UUID listId, UUID itemId, UpdateItemRequest request, UUID userId) {
    activeOwned(listId, userId);
    ShoppingListItem item = items.findByIdAndListId(itemId, listId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shopping list item not found"));
    String name = request.name() == null || request.name().isBlank() ? item.getName() : request.name().trim();
    String normalized = ShoppingListCalculator.normalize(name);
    String category = request.category() == null ? item.getCategory() : normalizeCategory(request.category());
    BigDecimal toBuy = request.toBuyQuantity() == null ? item.getToBuyQuantity()
        : request.toBuyQuantity().setScale(3, RoundingMode.HALF_UP);
    BigDecimal purchased = request.purchasedQuantity() == null ? item.getPurchasedQuantity()
        : request.purchasedQuantity().setScale(3, RoundingMode.HALF_UP);
    if (purchased.compareTo(toBuy) > 0) throw bad("Satın alınan miktar, alınacak miktarı aşamaz.");
    item.update(name, normalized, category, toBuy, purchased, request.checked());
    items.save(item);
    return get(listId, userId);
  }

  @Transactional
  public ListView removeItem(UUID listId, UUID itemId, UUID userId) {
    activeOwned(listId, userId);
    ShoppingListItem item = items.findByIdAndListId(itemId, listId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shopping list item not found"));
    items.delete(item);
    return get(listId, userId);
  }

  @Transactional
  public CompletionView complete(UUID listId, CompleteRequest request, UUID userId) {
    ShoppingList list = lists.lockOwned(listId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shopping list not found"));
    if ("COMPLETED".equals(list.getStatus())) {
      if (request.operationKey().equals(list.getCompletionOperationKey())) {
        return new CompletionView("ALREADY_COMPLETED", listId, 0, request.operationKey());
      }
      throw new ApiException(HttpStatus.CONFLICT, "Bu alışveriş listesi zaten tamamlandı.");
    }

    List<ShoppingListItem> selected = items.findByListIdOrderByCategoryAscNameAsc(listId).stream()
        .filter(item -> item.isChecked() && item.getPurchasedQuantity().signum() > 0).toList();
    if (selected.isEmpty()) throw bad("Kileri güncellemek için satın alınan en az bir ürünü işaretlemelisin.");

    List<PurchaseLine> purchaseLines = selected.stream().map(item -> {
      if (item.getPurchasedQuantity().compareTo(item.getToBuyQuantity()) > 0) {
        throw bad("Satın alınan miktar, alınacak miktarı aşamaz: " + item.getName());
      }
      return new PurchaseLine(item.getName(), item.getCategory(), item.getPurchasedQuantity(), item.getUnit());
    }).toList();
    String pantryOperationKey = listId.toString().replace("-", "") + ":" + request.operationKey();
    boolean applied = pantry.applyPurchase(userId, pantryOperationKey, purchaseLines);
    if (!applied) throw new ApiException(HttpStatus.CONFLICT, "Bu alışveriş işlemi daha önce uygulanmış.");

    list.complete(request.operationKey());
    lists.save(list);
    return new CompletionView("APPLIED", listId, purchaseLines.size(), request.operationKey());
  }

  private void addRecipeIngredients(List<ShoppingListCalculator.IngredientInput> target,
      CatalogRecipe recipe, int servings) {
    if (servings < 1 || servings > 30) throw bad("Porsiyon 1 ile 30 arasında olmalıdır.");
    BigDecimal factor = BigDecimal.valueOf(servings)
        .divide(BigDecimal.valueOf(recipe.defaultServings()), 8, RoundingMode.HALF_UP);
    recipe.ingredients().forEach(ingredient -> target.add(new ShoppingListCalculator.IngredientInput(
        ingredient.name(), BigDecimal.valueOf(ingredient.quantity()).multiply(factor), ingredient.unit())));
  }

  private CatalogRecipe findRecipe(String id) {
    return catalog.all().stream().filter(recipe -> recipe.id().equals(id)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown recipe catalog ID: " + id));
  }

  private ShoppingList owned(UUID id, UUID userId) {
    return lists.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shopping list not found"));
  }
  private ShoppingList activeOwned(UUID id, UUID userId) {
    ShoppingList list = owned(id, userId);
    if (!"ACTIVE".equals(list.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "Completed lists cannot be changed.");
    return list;
  }
  private static String normalizeUnit(String value) {
    String unit = value.trim().toLowerCase(Locale.ROOT);
    if (unit.equals("pcs") || unit.equals("adet")) return "piece";
    if (unit.isBlank() || unit.length() > 20) throw bad("Geçersiz birim.");
    return unit;
  }
  private static String normalizeCategory(String value) {
    if (value == null || value.isBlank()) return "OTHER";
    return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
  }
  private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

  private ListView view(ShoppingList list, List<ShoppingListItem> rows) {
    List<ItemView> itemViews = rows.stream().map(item -> new ItemView(item.getId(), item.getName(),
        item.getCategory(), item.getRequiredQuantity(), item.getPantryCoveredQuantity(),
        item.getToBuyQuantity(), item.getPurchasedQuantity(), item.getUnit(), item.isChecked(), item.getVersion())).toList();
    return new ListView(list.getId(), list.getTitle(), list.getSourceType(), list.getSourceMealPlanId(),
        list.getStatus(), list.getCurrency(), itemViews, false, null,
        List.of("COST_ESTIMATE_UNAVAILABLE: Güvenilir ürün fiyatı kaynağı yapılandırılmadı."),
        list.getCreatedAt(), list.getUpdatedAt());
  }
}
