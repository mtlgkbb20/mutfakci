package com.kitchenai.mealplan;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.pantry.PantryItem;
import com.kitchenai.recipe.RecipeCatalogService;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import com.kitchenai.recipe.RecipeCatalogService.Ingredient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MealPlanServiceTest {
  private MealPlanRepository plans;
  private MealPlanEntryRepository entries;
  private MealPlanPantryRepository pantry;
  private RecipeCatalogService catalog;
  private MealPlanAiSelector aiSelector;
  private MealPlanService service;
  private final UUID userId = UUID.randomUUID();
  private CatalogRecipe carrotCake;

  @BeforeEach
  void setUp() {
    plans = mock(MealPlanRepository.class);
    entries = mock(MealPlanEntryRepository.class);
    pantry = mock(MealPlanPantryRepository.class);
    aiSelector = mock(MealPlanAiSelector.class);
    carrotCake = new CatalogRecipe(1, "carrot-cake", "Havuçlu Kek", List.of(), "tr-TR",
        "CAKE", List.of(), 2, 45,
        List.of(new Ingredient("Un", 100, "g", "")), List.of(), "Test recipe");
    catalog = new RecipeCatalogService(new ObjectMapper()) {
      @Override public List<CatalogRecipe> all() { return List.of(carrotCake); }
    };
    service = new MealPlanService(plans, entries, pantry, catalog, new ObjectMapper(), aiSelector);
    when(plans.save(any(MealPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    when(entries.save(any(MealPlanEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    when(pantry.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId)).thenReturn(List.of());
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(any())).thenReturn(new ArrayList<>());
    when(aiSelector.select(any(), any(), any())).thenReturn(Optional.empty());
  }

  @Test
  void createsSevenDayPlanAndUsesDeterministicFallbackWhenAiUnavailable() {
    List<MealPlanEntry> savedEntries = new ArrayList<>();
    when(entries.save(any(MealPlanEntry.class))).thenAnswer(inv -> {
      MealPlanEntry entry = inv.getArgument(0);
      savedEntries.add(entry);
      return entry;
    });
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(any())).thenAnswer(inv -> List.copyOf(savedEntries));

    var result = service.create(request(List.of("LUNCH", "DINNER")), userId);

    assertEquals(14, result.entries().size());
    assertEquals(LocalDate.of(2026, 11, 9), result.weekStart());
    assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("AI_SEÇİMİ_KULLANILAMADI")));
    assertTrue(result.entries().stream().allMatch(e -> e.recipeCatalogId().equals("carrot-cake")));
    verify(aiSelector).select(any(), any(), any());
  }

  @Test
  void rejectsNonMondayWeekStart() {
    var request = new MealPlanContracts.CreateRequest(LocalDate.of(2026, 11, 10), 2,
        List.of("LUNCH"), 60, null, "TRY", List.of(), null, null);
    assertThrows(RuntimeException.class, () -> service.create(request, userId));
    verify(plans, never()).save(any());
  }

  @Test
  void replacingOneEntryDoesNotChangeOtherEntries() {
    MealPlan plan = new MealPlan(userId, LocalDate.of(2026, 11, 9), 2, "[\"LUNCH\"]",
        60, null, "TRY", "[]", null, null);
    MealPlanEntry target = new MealPlanEntry(plan.getId(), plan.getWeekStart(), "LUNCH", "old-id", 2, null);
    MealPlanEntry untouched = new MealPlanEntry(plan.getId(), plan.getWeekStart().plusDays(1),
        "LUNCH", "carrot-cake", 2, "keep me");
    when(plans.findByIdAndUserId(plan.getId(), userId)).thenReturn(Optional.of(plan));
    when(entries.findByIdAndPlanId(target.getId(), plan.getId())).thenReturn(Optional.of(target));
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(plan.getId())).thenReturn(List.of(target, untouched));

    var result = service.replaceEntry(plan.getId(), target.getId(),
        new MealPlanContracts.ReplaceEntryRequest("carrot-cake", 4, "new note"), userId);

    assertEquals("carrot-cake", target.getRecipeCatalogId());
    assertEquals(4, target.getServings());
    assertEquals("new note", target.getNote());
    assertEquals("carrot-cake", untouched.getRecipeCatalogId());
    assertEquals("keep me", untouched.getNote());
    assertEquals(2, result.entries().size());
    verify(entries).save(target);
    verify(entries, never()).save(untouched);
  }

  @Test
  void shoppingListScalesIngredientsAndSubtractsPantryWithoutMutatingIt() {
    MealPlan plan = new MealPlan(userId, LocalDate.of(2026, 11, 9), 4, "[\"LUNCH\"]",
        60, null, "TRY", "[]", null, null);
    MealPlanEntry entry = new MealPlanEntry(plan.getId(), plan.getWeekStart(), "LUNCH", "carrot-cake", 4, null);
    PantryItem flour = new PantryItem(userId, "Un", "un", "GRAINS", new BigDecimal("50"), "g", null);
    when(plans.findByIdAndUserId(plan.getId(), userId)).thenReturn(Optional.of(plan));
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(plan.getId())).thenReturn(List.of(entry));
    when(pantry.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId)).thenReturn(List.of(flour));

    var result = service.shoppingList(plan.getId(), userId);

    assertEquals(1, result.items().size());
    assertEquals(new BigDecimal("200.000"), result.items().get(0).requiredQuantity());
    assertEquals(new BigDecimal("50.000"), result.items().get(0).pantryCoveredQuantity());
    assertEquals(new BigDecimal("150.000"), result.items().get(0).toBuyQuantity());
    assertEquals(new BigDecimal("50"), flour.getQuantity());
  }

  @Test
  void doesNotExposePlansOwnedByAnotherUser() {
    UUID planId = UUID.randomUUID();
    when(plans.findByIdAndUserId(planId, userId)).thenReturn(Optional.empty());
    assertThrows(RuntimeException.class, () -> service.get(planId, userId));
  }

  @Test
  void regeneratesOnlyTheSelectedUsersPlanEntries() {
    MealPlan plan = new MealPlan(userId, LocalDate.of(2026, 11, 9), 2, "[\"LUNCH\"]",
        60, null, "TRY", "[]", null, null);
    MealPlanEntry oldEntry = new MealPlanEntry(plan.getId(), plan.getWeekStart(), "LUNCH", "carrot-cake", 2, "old");
    when(plans.findByIdAndUserId(plan.getId(), userId)).thenReturn(Optional.of(plan));
    List<MealPlanEntry> generated = new ArrayList<>();
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(plan.getId()))
        .thenAnswer(inv -> generated.isEmpty() ? List.of(oldEntry) : List.copyOf(generated));
    when(entries.save(any(MealPlanEntry.class))).thenAnswer(inv -> {
      MealPlanEntry saved = inv.getArgument(0);
      generated.add(saved);
      return saved;
    });

    var result = service.regenerate(plan.getId(), userId);

    assertEquals(plan.getId(), result.id());
    assertEquals(7, result.entries().size());
    verify(entries).deleteAllInBatch(List.of(oldEntry));
  }

  @Test
  void rejectsUnsupportedDietaryRestrictionInsteadOfPretendingItIsVerified() {
    var request = new MealPlanContracts.CreateRequest(LocalDate.of(2026, 11, 9), 2,
        List.of("LUNCH"), 60, null, "TRY", List.of("fıstık alerjisi"), null, null);
    assertThrows(RuntimeException.class, () -> service.create(request, userId));
    verify(plans, never()).save(any());
  }

  @Test
  void rejectsUnknownAiRecipeIdsAndFallsBackToVerifiedCatalog() {
    Map<String, String> invalid = new HashMap<>();
    for (int day = 0; day < 7; day++) invalid.put(day + ":LUNCH", "invented-recipe");
    when(aiSelector.select(any(), any(), any())).thenReturn(Optional.of(invalid));
    List<MealPlanEntry> savedEntries = new ArrayList<>();
    when(entries.save(any(MealPlanEntry.class))).thenAnswer(inv -> {
      MealPlanEntry entry = inv.getArgument(0);
      savedEntries.add(entry);
      return entry;
    });
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(any())).thenAnswer(inv -> List.copyOf(savedEntries));

    var result = service.create(request(List.of("LUNCH")), userId);

    assertEquals(7, result.entries().size());
    assertTrue(result.entries().stream().allMatch(e -> e.recipeCatalogId().equals("carrot-cake")));
    assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("AI_SEÇİMİ_KULLANILAMADI")));
  }

  @Test
  void acceptsOnlyCompleteAiSelectionForEveryRequestedSlot() {
    Map<String, String> valid = new HashMap<>();
    for (int day = 0; day < 7; day++) valid.put(day + ":LUNCH", "carrot-cake");
    when(aiSelector.select(any(), any(), any())).thenReturn(Optional.of(valid));
    List<MealPlanEntry> savedEntries = new ArrayList<>();
    when(entries.save(any(MealPlanEntry.class))).thenAnswer(inv -> {
      MealPlanEntry entry = inv.getArgument(0);
      savedEntries.add(entry);
      return entry;
    });
    when(entries.findByPlanIdOrderByMealDateAscMealSlotAsc(any())).thenAnswer(inv -> List.copyOf(savedEntries));

    var result = service.create(request(List.of("LUNCH")), userId);

    assertEquals(7, result.entries().size());
    assertFalse(result.warnings().stream().anyMatch(w -> w.startsWith("AI_SEÇİMİ_KULLANILAMADI")));
  }

  private MealPlanContracts.CreateRequest request(List<String> slots) {
    return new MealPlanContracts.CreateRequest(LocalDate.of(2026, 11, 9), 2, slots,
        60, new BigDecimal("5000"), "TRY", List.of(), "Dengeli beslenme", null);
  }
}
