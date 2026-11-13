package com.kitchenai.zerowaste;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.cookingjournal.CookingJournalEntry;
import com.kitchenai.cookingjournal.CookingJournalRepository;
import com.kitchenai.pantry.PantryRemainderTransferService;
import com.kitchenai.pantry.PantryRemainderTransferService.PantryStockLine;
import com.kitchenai.recipe.RecipeCatalogService;
import com.kitchenai.recipe.RecipeCatalogService.ByProductRule;
import com.kitchenai.recipe.RecipeCatalogService.CatalogRecipe;
import com.kitchenai.recipe.RecipeCatalogService.Ingredient;
import com.kitchenai.recipe.RecipeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeroWasteServiceTest {
  private IngredientRemainderRepository remainders;
  private IngredientRemainderOperationRepository operations;
  private RecipeCatalogService catalog;
  private RecipeRepository recipes;
  private CookingSessionRepository sessions;
  private CookingJournalRepository journal;
  private PantryRemainderTransferService pantryTransfer;
  private ZeroWasteService service;
  private final UUID userId = UUID.randomUUID();
  private final UUID remainderId = UUID.randomUUID();
  private CatalogRecipe yolkRecipe;

  @BeforeEach
  void setUp() {
    remainders = mock(IngredientRemainderRepository.class);
    operations = mock(IngredientRemainderOperationRepository.class);
    catalog = mock(RecipeCatalogService.class);
    recipes = mock(RecipeRepository.class);
    sessions = mock(CookingSessionRepository.class);
    journal = mock(CookingJournalRepository.class);
    pantryTransfer = mock(PantryRemainderTransferService.class);
    service = new ZeroWasteService(remainders, operations, catalog, recipes, sessions,
        journal, pantryTransfer, new ObjectMapper());
    yolkRecipe = new CatalogRecipe(1, "yolk-dessert", "Yumurta Sarılı Tatlı", List.of(),
        "tr-TR", "DESSERT", List.of(), 2, 30,
        List.of(new Ingredient("Yumurta sarısı", 4, "adet", "")), List.of(), "Test",
        List.of(new ByProductRule("Yumurta sarısı", "adet", "Yumurta akı", "adet", 1, 1, "egg-separation-v1")));
    when(catalog.all()).thenReturn(List.of(yolkRecipe));
    when(operations.existsByUserIdAndOperationKey(any(), any())).thenReturn(false);
    when(remainders.save(any(IngredientRemainder.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void previewScalesIngredientsAndVersionedByProductsDeterministically() {
    var preview = service.preview("yolk-dessert", null, 4, userId);

    assertEquals(new BigDecimal("8.000"), preview.ingredients().get(0).quantity());
    assertEquals("EXACT", preview.ingredients().get(0).certainty());
    assertEquals("Yumurta akı", preview.byProducts().get(0).name());
    assertEquals(new BigDecimal("8.000"), preview.byProducts().get(0).quantity());
    assertTrue(preview.catalogNote().contains("kullanıcı onayı"));
  }

  @Test
  void previewRejectsAmbiguousRecipeSource() {
    assertThrows(RuntimeException.class, () -> service.preview("yolk-dessert", UUID.randomUUID(), 2, userId));
    assertThrows(RuntimeException.class, () -> service.preview(null, null, 2, userId));
  }

  @Test
  void confirmPersistsOnlyUserConfirmedRemainderAndAudit() {
    var saved = service.confirm(userId, "Yumurta akı", new BigDecimal("4.000"), "adet",
        "yolk-dessert", null, null, null, null, "confirm-1");

    assertEquals("USER_CONFIRMED", saved.certainty());
    assertEquals("AVAILABLE", saved.status());
    assertEquals("Yumurta akı", saved.ingredientName());
    verify(operations).save(any(IngredientRemainderOperation.class));
  }

  @Test
  void partialConsumptionLeavesReducedAvailableRemainder() {
    IngredientRemainder remainder = new IngredientRemainder(userId, null, null, null, null,
        "Yumurta akı", "yumurta aki", new BigDecimal("4.000"), "adet", "USER_CONFIRMED", null);
    when(remainders.lockOwned(remainder.getId(), userId)).thenReturn(Optional.of(remainder));

    var result = service.consume(userId, remainder.getId(), new BigDecimal("1.500"), "consume-1");

    assertEquals(new BigDecimal("2.500"), result.quantity());
    assertEquals("AVAILABLE", result.status());
    verify(operations).save(any(IngredientRemainderOperation.class));
  }

  @Test
  void fullConsumptionTransitionsRemainderToConsumed() {
    IngredientRemainder remainder = new IngredientRemainder(userId, null, null, null, null,
        "Yumurta akı", "yumurta aki", new BigDecimal("4.000"), "adet", "USER_CONFIRMED", null);
    when(remainders.lockOwned(remainder.getId(), userId)).thenReturn(Optional.of(remainder));

    var result = service.consume(userId, remainder.getId(), new BigDecimal("4.000"), "consume-2");

    assertEquals(BigDecimal.ZERO.setScale(3), result.quantity());
    assertEquals("CONSUMED", result.status());
  }

  @Test
  void pantryTransferAndRemainderTransitionShareServiceTransactionBoundary() {
    IngredientRemainder remainder = new IngredientRemainder(userId, null, null, null, null,
        "Yumurta akı", "yumurta aki", new BigDecimal("4.000"), "adet", "USER_CONFIRMED", null);
    when(remainders.lockOwned(remainder.getId(), userId)).thenReturn(Optional.of(remainder));

    var result = service.transfer(userId, remainder.getId(), "transfer-1");

    assertEquals("TRANSFERRED", result.status());
    verify(pantryTransfer).transfer(userId, "Yumurta akı", "yumurta aki",
        new BigDecimal("4.000"), "adet", null, "zw:transfer-1");
  }

  @Test
  void suggestionsPreferPantryCoverageAfterEqualRemainderConsumption() {
    CatalogRecipe flourRecipe = new CatalogRecipe(1, "egg-white-flour", "Yumurta Akı ve Un",
        List.of(), "tr-TR", "DESSERT", List.of(), 2, 25,
        List.of(new Ingredient("Yumurta akı", 4, "adet", ""),
            new Ingredient("Un", 100, "g", "")), List.of(), "Flour candidate",
        List.of(new ByProductRule("Un", "g", "Un artığı", "g", 1, 10, "flour-v1")));
    CatalogRecipe sugarRecipe = new CatalogRecipe(1, "egg-white-sugar", "Yumurta Akı ve Şeker",
        List.of(), "tr-TR", "DESSERT", List.of(), 2, 25,
        List.of(new Ingredient("Yumurta akı", 4, "adet", ""),
            new Ingredient("Şeker", 100, "g", "")), List.of(), "Sugar candidate", List.of());
    when(catalog.all()).thenReturn(List.of(sugarRecipe, flourRecipe));
    when(pantryTransfer.activeStock(userId)).thenReturn(
        List.of(new PantryStockLine("un", new BigDecimal("100.000"), "g")));
    IngredientRemainder remainder = new IngredientRemainder(userId, null, null, null, null,
        "Yumurta akı", "yumurta aki", new BigDecimal("4.000"), "adet", "USER_CONFIRMED", null);
    when(remainders.findByIdAndUserId(remainder.getId(), userId)).thenReturn(Optional.of(remainder));

    var suggestions = service.suggestions(userId, remainder.getId(), 2);

    assertEquals("egg-white-flour", suggestions.get(0).recipeId());
    assertEquals(1, suggestions.get(0).pantryCoveredIngredientCount());
    assertEquals(0, suggestions.get(0).missingIngredientCount());
    assertEquals(1, suggestions.get(0).newByProductCount());
    assertEquals("egg-white-sugar", suggestions.get(1).recipeId());
    assertEquals(1, suggestions.get(1).missingIngredientCount());
  }

  @Test
  void rejectsJournalEntryThatBelongsToAnotherCookingSession() {
    CookingSession session = new CookingSession(userId, null, "RECIPE", null);
    CookingJournalEntry entry = new CookingJournalEntry(userId, UUID.randomUUID(), null,
        "{}", null, null, null, null);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(journal.findByIdAndUserId(entry.getId(), userId)).thenReturn(Optional.of(entry));

    assertThrows(RuntimeException.class, () -> service.confirm(userId, "Yumurta akı",
        new BigDecimal("4.000"), "adet", null, null, session.getId(), entry.getId(),
        null, "mismatched-source"));
    verify(remainders, never()).save(any(IngredientRemainder.class));
  }

  @Test
  void repeatedIdempotencyKeyReturnsExistingRemainderWithoutConsumingAgain() {
    IngredientRemainder remainder = new IngredientRemainder(userId, null, null, null, null,
        "Yumurta akı", "yumurta aki", new BigDecimal("4.000"), "adet", "USER_CONFIRMED", null);
    IngredientRemainderOperation operation = new IngredientRemainderOperation(userId,
        remainder.getId(), "replay-1", "CONSUME", new BigDecimal("5.000"), new BigDecimal("4.000"));
    when(operations.existsByUserIdAndOperationKey(userId, "replay-1")).thenReturn(true);
    when(operations.findByUserIdAndOperationKey(userId, "replay-1")).thenReturn(Optional.of(operation));
    when(remainders.findByIdAndUserId(remainder.getId(), userId)).thenReturn(Optional.of(remainder));

    var result = service.consume(userId, remainder.getId(), new BigDecimal("1.000"), "replay-1");

    assertEquals(new BigDecimal("4.000"), result.quantity());
    assertEquals("AVAILABLE", result.status());
    verify(remainders, never()).save(any(IngredientRemainder.class));
  }

  @Test
  void crossUserRemainderLookupReturnsNotFound() {
    when(remainders.findByIdAndUserId(remainderId, userId)).thenReturn(Optional.empty());
    assertThrows(RuntimeException.class, () -> service.get(userId, remainderId));
  }
}
