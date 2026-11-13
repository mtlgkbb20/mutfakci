package com.kitchenai.mealplan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.pantry.PantryItem;
import com.kitchenai.recipe.RecipeCatalogService;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.kitchenai.mealplan.MealPlanContracts.*;

@Service
public class MealPlanService {
  private static final Set<String> ALLOWED_SLOTS = Set.of("BREAKFAST", "LUNCH", "DINNER");
  private final MealPlanRepository plans;
  private final MealPlanEntryRepository entries;
  private final MealPlanPantryRepository pantry;
  private final RecipeCatalogService catalog;
  private final ObjectMapper json;
  private final MealPlanAiSelector aiSelector;

  public MealPlanService(MealPlanRepository plans, MealPlanEntryRepository entries,
      MealPlanPantryRepository pantry, RecipeCatalogService catalog, ObjectMapper json,
      MealPlanAiSelector aiSelector) {
    this.plans = plans;
    this.entries = entries;
    this.pantry = pantry;
    this.catalog = catalog;
    this.json = json;
    this.aiSelector = aiSelector;
  }

  @Transactional
  public PlanView create(CreateRequest request, UUID userId) {
    validateRequest(request);
    if (plans.findByUserIdAndWeekStart(userId, request.weekStart()).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "Bu hafta için zaten bir yemek planın var.");
    }

    List<String> slots = request.mealSlots().stream().map(String::toUpperCase).distinct().sorted().toList();
    List<String> restrictions = request.dietaryRestrictions() == null
        ? List.of() : request.dietaryRestrictions().stream().map(String::trim).filter(s -> !s.isBlank()).distinct().toList();

    List<CatalogRecipe> candidates = eligibleRecipes(request.maxPreparationMinutes(), restrictions);
    if (candidates.isEmpty()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
          "Katalogda süre ve beslenme kısıtlarına uyan doğrulanmış tarif bulunamadı.");
    }

    String slotsJson;
    String restrictionsJson;
    try {
      slotsJson = json.writeValueAsString(slots);
      restrictionsJson = json.writeValueAsString(restrictions);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Meal plan preferences could not be serialized");
    }

    MealPlan plan = plans.save(new MealPlan(userId, request.weekStart(), request.servings(),
        slotsJson, request.maxPreparationMinutes(), request.weeklyBudget(),
        request.currency() == null ? "TRY" : request.currency(), restrictionsJson,
        request.goal(), request.notes()));

    Optional<Map<String, String>> aiSelections = aiSelector.select(request, candidates, slots);
    Map<String, String> selections = aiSelections.filter(result -> validSelection(result, candidates, slots))
        .orElseGet(() -> deterministicSelection(candidates, slots));
    List<String> generationWarnings = aiSelections.isPresent() && validSelection(aiSelections.get(), candidates, slots)
        ? List.of()
        : List.of("AI_SEÇİMİ_KULLANILAMADI: Doğrulanmış tarif kataloğundan deterministik seçim yapıldı.");

    for (int day = 0; day < 7; day++) {
      LocalDate date = request.weekStart().plusDays(day);
      for (String slot : slots) {
        String recipeId = selections.get(day + ":" + slot);
        entries.save(new MealPlanEntry(plan.getId(), date, slot, recipeId, request.servings(), null));
      }
    }
    return view(plan, entries.findByPlanIdOrderByMealDateAscMealSlotAsc(plan.getId()), generationWarnings);
  }

  @Transactional(readOnly = true)
  public List<RecipeSummary> candidates(UUID planId, UUID userId) {
    MealPlan plan = ownedPlan(planId, userId);
    return eligibleRecipes(plan.getMaxPreparationMinutes(), readRestrictions(plan)).stream()
        .map(this::summary)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PlanView> list(UUID userId) {
    return plans.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(plan -> view(plan, entries.findByPlanIdOrderByMealDateAscMealSlotAsc(plan.getId()), List.of()))
        .toList();
  }

  @Transactional(readOnly = true)
  public PlanView get(UUID planId, UUID userId) {
    MealPlan plan = ownedPlan(planId, userId);
    return view(plan, entries.findByPlanIdOrderByMealDateAscMealSlotAsc(planId), List.of());
  }

  @Transactional
  public PlanView replaceEntry(UUID planId, UUID entryId, ReplaceEntryRequest request, UUID userId) {
    MealPlan plan = ownedPlan(planId, userId);
    MealPlanEntry entry = entries.findByIdAndPlanId(entryId, planId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Meal plan entry not found"));
    CatalogRecipe replacement = catalog.all().stream()
        .filter(recipe -> recipe.id().equals(request.recipeCatalogId()))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown recipe catalog ID"));

    if (replacement.estimatedTotalMinutes() > plan.getMaxPreparationMinutes()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Tarif belirlenen hazırlama süresini aşıyor.");
    }
    List<String> restrictions = readRestrictions(plan);
    if (!matchesRestrictions(replacement, restrictions)) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Tarif belirtilen beslenme kısıtlarıyla uyumlu değil.");
    }
    entry.replace(replacement.id(), request.servings(), request.note());
    entries.save(entry);
    return view(plan, entries.findByPlanIdOrderByMealDateAscMealSlotAsc(planId), List.of());
  }

  @Transactional(readOnly = true)
  public ShoppingListView shoppingList(UUID planId, UUID userId) {
    MealPlan plan = ownedPlan(planId, userId);
    List<MealPlanEntry> planEntries = entries.findByPlanIdOrderByMealDateAscMealSlotAsc(planId);
    Map<String, IngredientAccumulator> totals = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();

    for (MealPlanEntry entry : planEntries) {
      CatalogRecipe recipe = findRecipe(entry.getRecipeCatalogId());
      BigDecimal scale = BigDecimal.valueOf(entry.getServings())
          .divide(BigDecimal.valueOf(recipe.defaultServings()), 6, RoundingMode.HALF_UP);
      for (var ingredient : recipe.ingredients()) {
        String normalized = normalize(ingredient.name());
        String unit = canonicalUnit(ingredient.unit());
        BigDecimal quantity = toCanonical(BigDecimal.valueOf(ingredient.quantity()).multiply(scale), ingredient.unit())
            .setScale(3, RoundingMode.HALF_UP);
        String key = normalized + "|" + unit;
        IngredientAccumulator accumulator = totals.computeIfAbsent(key,
            ignored -> new IngredientAccumulator(ingredient.name(), normalized, unit));
        accumulator.required = accumulator.required.add(quantity);
      }
    }

    Map<String, BigDecimal> pantryStock = new HashMap<>();
    for (PantryItem item : pantry.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId)) {
      String key = normalize(item.getName()) + "|" + canonicalUnit(item.getUnit());
      pantryStock.merge(key, toCanonical(item.getQuantity(), item.getUnit()), BigDecimal::add);
    }

    Map<String, Set<String>> unitsByName = new HashMap<>();
    totals.values().forEach(line -> unitsByName.computeIfAbsent(line.normalized, ignored -> new HashSet<>()).add(line.unit));
    if (unitsByName.values().stream().anyMatch(units -> units.size() > 1)) {
      warnings.add("Bazı malzemeler farklı veya dönüştürülemeyen birimlerle listelendi; bu satırlarda stok otomatik düşülmedi.");
    }

    List<ShoppingLine> lines = totals.entrySet().stream().map(entry -> {
      IngredientAccumulator line = entry.getValue();
      BigDecimal stock = pantryStock.getOrDefault(entry.getKey(), BigDecimal.ZERO);
      BigDecimal covered = stock.min(line.required);
      BigDecimal toBuy = line.required.subtract(covered).max(BigDecimal.ZERO);
      return new ShoppingLine(line.name, line.required.setScale(3, RoundingMode.HALF_UP),
          covered.setScale(3, RoundingMode.HALF_UP), toBuy.setScale(3, RoundingMode.HALF_UP), line.unit);
    }).sorted(Comparator.comparing(ShoppingLine::name, String.CASE_INSENSITIVE_ORDER)).toList();

    return new ShoppingListView(planId, lines, warnings);
  }

  private boolean validSelection(Map<String, String> selections, List<CatalogRecipe> candidates, List<String> slots) {
    if (selections.size() != 7 * slots.size()) return false;
    Set<String> candidateIds = candidates.stream().map(CatalogRecipe::id).collect(Collectors.toSet());
    for (int day = 0; day < 7; day++) {
      for (String slot : slots) {
        String selected = selections.get(day + ":" + slot);
        if (selected == null || !candidateIds.contains(selected)) return false;
      }
    }
    return true;
  }

  private Map<String, String> deterministicSelection(List<CatalogRecipe> candidates, List<String> slots) {
    Map<String, String> result = new LinkedHashMap<>();
    Map<String, Integer> usage = new HashMap<>();
    for (int day = 0; day < 7; day++) {
      for (String slot : slots) {
        CatalogRecipe selected = candidates.stream()
            .min(Comparator.comparingInt((CatalogRecipe recipe) -> usage.getOrDefault(recipe.id(), 0))
                .thenComparingInt(CatalogRecipe::estimatedTotalMinutes)
                .thenComparing(CatalogRecipe::id))
            .orElseThrow();
        usage.merge(selected.id(), 1, Integer::sum);
        result.put(day + ":" + slot, selected.id());
      }
    }
    return result;
  }

  private List<CatalogRecipe> eligibleRecipes(int maxMinutes, List<String> restrictions) {
    return catalog.all().stream()
        .filter(recipe -> recipe.estimatedTotalMinutes() <= maxMinutes)
        .filter(recipe -> matchesRestrictions(recipe, restrictions))
        .sorted(Comparator.comparingInt(CatalogRecipe::estimatedTotalMinutes).thenComparing(CatalogRecipe::id))
        .toList();
  }

  private boolean matchesRestrictions(CatalogRecipe recipe, List<String> restrictions) {
    Set<String> ingredients = recipe.ingredients().stream()
        .map(i -> normalize(i.name())).collect(Collectors.toSet());
    for (String restriction : restrictions) {
      String key = normalize(restriction);
      Set<String> excluded = switch (key) {
        case "vegan" -> Set.of("yumurta", "sut", "tereyagi", "peynir", "yogurt", "bal", "et", "tavuk", "balik", "krema");
        case "vegetarian", "vejetaryen" -> Set.of("et", "tavuk", "balik", "kiyma", "sucuk", "salam");
        case "glutensiz", "gluten free" -> Set.of("un", "bugday", "arpa", "cavdar", "irmik", "bulgur", "makarna", "ekmek");
        case "laktozsuz", "lactose free" -> Set.of("sut", "tereyagi", "peynir", "yogurt", "krema", "labne");
        default -> Set.of(key);
      };
      if (ingredients.stream().anyMatch(name -> excluded.stream().anyMatch(name::contains))) return false;
    }
    return true;
  }

  private void validateRequest(CreateRequest request) {
    if (request.weekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Hafta başlangıcı pazartesi olmalıdır.");
    }
    if (request.mealSlots() == null || request.mealSlots().isEmpty()
        || request.mealSlots().stream().anyMatch(slot -> slot == null || !ALLOWED_SLOTS.contains(slot.toUpperCase()))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz öğün türü.");
    }
    if (request.currency() != null && !request.currency().matches("[A-Z]{3}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Para birimi ISO 4217 formatında olmalıdır.");
    }
  }

  private MealPlan ownedPlan(UUID id, UUID userId) {
    return plans.findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Meal plan not found"));
  }

  private CatalogRecipe findRecipe(String id) {
    return catalog.all().stream().filter(recipe -> recipe.id().equals(id)).findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A planned catalog recipe is no longer available"));
  }

  private PlanView view(MealPlan plan, List<MealPlanEntry> planEntries, List<String> extraWarnings) {
    List<String> warnings = new ArrayList<>(extraWarnings);
    if (plan.getWeeklyBudget() != null) warnings.add("BÜTÇE_DOĞRULANAMADI: Tarif kataloğunda güvenilir maliyet verisi bulunmuyor.");
    if (catalog.all().size() < planEntries.size()) warnings.add("KATALOG ÇEŞİTLİLİĞİ SINIRLI: Bazı tarifler hafta içinde tekrar ediyor.");
    List<EntryView> entryViews = planEntries.stream().map(entry -> {
      CatalogRecipe recipe = findRecipe(entry.getRecipeCatalogId());
      return new EntryView(entry.getId(), entry.getMealDate(), entry.getMealSlot(),
          entry.getRecipeCatalogId(), entry.getServings(), entry.getNote(), summary(recipe));
    }).toList();
    return new PlanView(plan.getId(), plan.getWeekStart(), plan.getServings(),
        readStringList(plan.getMealSlots()), plan.getMaxPreparationMinutes(),
        plan.getWeeklyBudget(), plan.getCurrency(), readRestrictions(plan), plan.getGoal(),
        plan.getNotes(), entryViews, warnings, plan.getCreatedAt(), plan.getUpdatedAt());
  }

  private RecipeSummary summary(CatalogRecipe recipe) {
    return new RecipeSummary(recipe.id(), recipe.title(), recipe.category(),
        recipe.defaultServings(), recipe.estimatedTotalMinutes(),
        recipe.ingredients().stream().map(i -> new IngredientView(i.name(), i.quantity(), i.unit(), i.preparation())).toList());
  }

  private List<String> readRestrictions(MealPlan plan) { return readStringList(plan.getDietaryRestrictions()); }
  private List<String> readStringList(String value) {
    try { return json.readValue(value, new TypeReference<List<String>>() {}); }
    catch (Exception e) { return List.of(); }
  }

  private static String canonicalUnit(String unit) {
    String value = normalize(unit).replace(" ", "");
    return switch (value) {
      case "kg" -> "g";
      case "l" -> "ml";
      case "adet", "pcs", "piece" -> "piece";
      default -> value;
    };
  }

  private static BigDecimal toCanonical(BigDecimal quantity, String unit) {
    String normalized = normalize(unit).replace(" ", "");
    return switch (normalized) {
      case "kg" -> quantity.multiply(BigDecimal.valueOf(1000));
      case "l" -> quantity.multiply(BigDecimal.valueOf(1000));
      default -> quantity;
    };
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").replace('ı', 'i')
        .replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ");
  }

  private static final class IngredientAccumulator {
    private final String name;
    private final String normalized;
    private final String unit;
    private BigDecimal required = BigDecimal.ZERO;
    private IngredientAccumulator(String name, String normalized, String unit) {
      this.name = name; this.normalized = normalized; this.unit = unit;
    }
  }
}
