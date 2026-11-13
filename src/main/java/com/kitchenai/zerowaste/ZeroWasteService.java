package com.kitchenai.zerowaste;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.cookingjournal.CookingJournalRepository;
import com.kitchenai.pantry.PantryRemainderTransferService;
import com.kitchenai.recipe.Recipe;
import com.kitchenai.recipe.RecipeCatalogService;
import com.kitchenai.recipe.RecipeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZeroWasteService {
  private final IngredientRemainderRepository remainders;
  private final IngredientRemainderOperationRepository operations;
  private final RecipeCatalogService catalog;
  private final RecipeRepository recipes;
  private final CookingSessionRepository sessions;
  private final CookingJournalRepository journal;
  private final PantryRemainderTransferService pantryTransfer;
  private final ObjectMapper json;

  public ZeroWasteService(IngredientRemainderRepository remainders,
      IngredientRemainderOperationRepository operations, RecipeCatalogService catalog,
      RecipeRepository recipes, CookingSessionRepository sessions,
      CookingJournalRepository journal, PantryRemainderTransferService pantryTransfer,
      ObjectMapper json) {
    this.remainders = remainders; this.operations = operations; this.catalog = catalog;
    this.recipes = recipes; this.sessions = sessions; this.journal = journal;
    this.pantryTransfer = pantryTransfer; this.json = json;
  }

  public record IngredientLine(String name, BigDecimal quantity, String unit, String certainty) {}
  public record Preview(String sourceType, String recipeId, String title, int servings,
      List<IngredientLine> ingredients, List<IngredientLine> byProducts, String catalogNote) {}
  public record RemainderView(UUID id, String ingredientName, BigDecimal quantity, String unit,
      String certainty, String status, String sourceCatalogRecipeId, UUID sourceRecipeId,
      UUID cookingSessionId, UUID journalEntryId, LocalDate useByDate, long version) {}
  public record Suggestion(String recipeId, String title, int servings, BigDecimal requiredQuantity,
      String unit, String quantityCertainty) {}

  public Preview preview(String catalogRecipeId, UUID recipeId, int servings, UUID userId) {
    if (servings < 1 || servings > 30) throw bad("Servis sayısı 1-30 arasında olmalı.");
    if ((catalogRecipeId == null || catalogRecipeId.isBlank()) == (recipeId == null))
      throw bad("Yalnızca catalogRecipeId veya recipeId gönderilmelidir.");

    if (catalogRecipeId != null) {
      var recipe = catalog.all().stream().filter(r -> r.id().equals(catalogRecipeId)).findFirst()
          .orElseThrow(() -> notFound("Recipe not found"));
      List<IngredientLine> ingredients = recipe.ingredients().stream().map(i -> {
        var scaled = ZeroWasteQuantityRules.scale(BigDecimal.valueOf(i.quantity()), servings, recipe.defaultServings());
        return new IngredientLine(i.name(), scaled.quantity(), ZeroWasteQuantityRules.validateUnit(i.unit()), scaled.certainty());
      }).toList();
      List<IngredientLine> byProducts = deriveByProducts(recipe, servings);
      String note = byProducts.isEmpty()
          ? "Bu tarif için henüz doğrulanmış yan ürün kuralı tanımlanmamış."
          : "Yan ürünler sürümlenmiş tarif kurallarından hesaplandı; pişirme sonrası kullanıcı onayı gerekir.";
      return new Preview("CURATED", recipe.id(), recipe.title(), servings, ingredients, byProducts, note);
    }

    Recipe recipe = ownedRecipe(recipeId, userId);
    if (recipe.getServings() < 1) throw bad("Tarifin porsiyon bilgisi geçersiz.");
    List<Map<String,Object>> raw;
    try { raw = json.readValue(recipe.getIngredientsJson(), new TypeReference<>() {}); }
    catch (Exception e) { throw bad("Tarif malzemeleri okunamadı."); }
    List<IngredientLine> ingredients = new ArrayList<>();
    for (Map<String,Object> item : raw) {
      Object nameValue = item.get("name"), quantityValue = item.get("quantity"), unitValue = item.get("unit");
      if (!(nameValue instanceof String name) || !(quantityValue instanceof Number quantity)
          || !(unitValue instanceof String unit)) continue;
      var scaled = ZeroWasteQuantityRules.scale(new BigDecimal(quantity.toString()), servings, recipe.getServings());
      ingredients.add(new IngredientLine(name, scaled.quantity(), ZeroWasteQuantityRules.validateUnit(unit), scaled.certainty()));
    }
    return new Preview(recipe.getSourceType(), recipeId.toString(), recipe.getTitle(), servings,
        List.copyOf(ingredients), List.of(), "Kullanıcı tarifinde yan ürün kuralı tanımlı değil.");
  }

  @Transactional
  public RemainderView confirm(UUID userId, String ingredientName, BigDecimal quantity, String unit,
      String catalogRecipeId, UUID recipeId, UUID sessionId, UUID journalEntryId,
      LocalDate useByDate, String operationKey) {
    validateOperationKey(operationKey);
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) return duplicate(operationKey, userId);
    validateQuantity(quantity);
    String normalized = ZeroWasteQuantityRules.normalize(ingredientName);
    if (normalized.isBlank() || ingredientName.trim().length() > 160) throw bad("Geçersiz malzeme adı.");
    String normalizedUnit = ZeroWasteQuantityRules.validateUnit(unit);
    validateSources(userId, catalogRecipeId, recipeId, sessionId, journalEntryId);
    IngredientRemainder saved = remainders.save(new IngredientRemainder(userId, catalogRecipeId,
        recipeId, sessionId, journalEntryId, ingredientName.trim(), normalized, quantity,
        normalizedUnit, "USER_CONFIRMED", useByDate));
    operations.save(new IngredientRemainderOperation(userId, saved.getId(), operationKey,
        "CONFIRM", null, quantity));
    return view(saved);
  }

  public List<RemainderView> list(UUID userId) {
    return remainders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(ZeroWasteService::view).toList();
  }

  public RemainderView get(UUID userId, UUID id) { return view(owned(id, userId)); }

  @Transactional
  public RemainderView correct(UUID userId, UUID id, BigDecimal quantity, String unit,
      LocalDate useByDate, String operationKey) {
    validateOperationKey(operationKey);
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) return duplicate(operationKey, userId);
    IngredientRemainder r = lockAvailable(id, userId);
    validateQuantity(quantity);
    String nextUnit = ZeroWasteQuantityRules.validateUnit(unit);
    if (!ZeroWasteQuantityRules.compatible(r.getUnit(), nextUnit) || !r.getUnit().equals(nextUnit))
      throw bad("Birim değişikliği desteklenmiyor; mevcut birim korunmalı.");
    BigDecimal before = r.getQuantity();
    r.correct(quantity, nextUnit, useByDate);
    remainders.save(r);
    operations.save(new IngredientRemainderOperation(userId, id, operationKey, "CORRECT", before, quantity));
    return view(r);
  }

  @Transactional
  public RemainderView consume(UUID userId, UUID id, BigDecimal quantity, String operationKey) {
    validateOperationKey(operationKey);
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) return duplicate(operationKey, userId);
    IngredientRemainder r = lockAvailable(id, userId);
    validateQuantity(quantity);
    BigDecimal before = r.getQuantity();
    try { r.consume(quantity); }
    catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
    remainders.save(r);
    operations.save(new IngredientRemainderOperation(userId, id, operationKey, "CONSUME", before, r.getQuantity()));
    return view(r);
  }

  @Transactional
  public RemainderView dismiss(UUID userId, UUID id, String operationKey) {
    validateOperationKey(operationKey);
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) return duplicate(operationKey, userId);
    IngredientRemainder r = lockAvailable(id, userId);
    BigDecimal before = r.getQuantity();
    r.transition("DISCARDED");
    remainders.save(r);
    operations.save(new IngredientRemainderOperation(userId, id, operationKey, "DISCARD", before, before));
    return view(r);
  }

  @Transactional
  public RemainderView transfer(UUID userId, UUID id, String operationKey) {
    validateOperationKey(operationKey);
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) return duplicate(operationKey, userId);
    IngredientRemainder r = lockAvailable(id, userId);
    BigDecimal before = r.getQuantity();
    pantryTransfer.transfer(userId, r.getIngredientName(), r.getNormalizedName(), r.getQuantity(),
        r.getUnit(), r.getUseByDate(), "zero-waste:" + operationKey);
    r.transition("TRANSFERRED");
    remainders.save(r);
    operations.save(new IngredientRemainderOperation(userId, id, operationKey, "TRANSFER", before, BigDecimal.ZERO));
    return view(r);
  }

  public List<Suggestion> suggestions(UUID userId, UUID id, Integer requestedServings) {
    IngredientRemainder r = owned(id, userId);
    if (!"AVAILABLE".equals(r.getStatus())) throw bad("Yalnızca kullanılabilir artan malzemeler için öneri alınabilir.");
    List<Suggestion> result = new ArrayList<>();
    for (var recipe : catalog.all()) {
      for (var ingredient : recipe.ingredients()) {
        if (!r.getNormalizedName().equals(ZeroWasteQuantityRules.normalize(ingredient.name()))) continue;
        int servings = requestedServings == null ? recipe.defaultServings() : requestedServings;
        if (servings < 1 || servings > 30) throw bad("Servis sayısı 1-30 arasında olmalı.");
        String unit = ZeroWasteQuantityRules.validateUnit(ingredient.unit());
        if (!ZeroWasteQuantityRules.compatible(r.getUnit(), unit)) continue;
        var scaled = ZeroWasteQuantityRules.scale(BigDecimal.valueOf(ingredient.quantity()), servings, recipe.defaultServings());
        BigDecimal required;
        try { required = ZeroWasteQuantityRules.convert(scaled.quantity(), unit, r.getUnit()); }
        catch (ApiException ignored) { continue; }
        if (required.compareTo(r.getQuantity()) <= 0)
          result.add(new Suggestion(recipe.id(), recipe.title(), servings, required, r.getUnit(), scaled.certainty()));
        break;
      }
    }
    result.sort(Comparator.comparing(Suggestion::requiredQuantity).reversed().thenComparing(Suggestion::title));
    return List.copyOf(result);
  }

  private List<IngredientLine> deriveByProducts(RecipeCatalogService.CatalogRecipe recipe, int servings) {
    if (recipe.byProducts() == null || recipe.byProducts().isEmpty()) return List.of();
    List<IngredientLine> result = new ArrayList<>();
    for (var rule : recipe.byProducts()) {
      if (rule.sourceIngredientName() == null || rule.byProductName() == null
          || rule.ruleVersion() == null || rule.ruleVersion().isBlank())
        throw new IllegalStateException("Invalid by-product rule metadata for catalog recipe " + recipe.id());
      String sourceUnit = ZeroWasteQuantityRules.validateUnit(rule.sourceUnit());
      String outputUnit = ZeroWasteQuantityRules.validateUnit(rule.byProductUnit());
      var source = recipe.ingredients().stream()
          .filter(i -> ZeroWasteQuantityRules.normalize(i.name())
              .equals(ZeroWasteQuantityRules.normalize(rule.sourceIngredientName())))
          .filter(i -> ZeroWasteQuantityRules.validateUnit(i.unit()).equals(sourceUnit))
          .findFirst();
      if (source.isEmpty()) continue;
      var scaledSource = ZeroWasteQuantityRules.scale(BigDecimal.valueOf(source.get().quantity()),
          servings, recipe.defaultServings());
      var derived = ZeroWasteByProductCalculator.derive(scaledSource.quantity(),
          rule.numerator(), rule.denominator(), scaledSource.certainty());
      result.add(new IngredientLine(rule.byProductName(), derived.quantity(), outputUnit, derived.certainty()));
    }
    return List.copyOf(result);
  }

  private void validateSources(UUID userId, String catalogId, UUID recipeId, UUID sessionId, UUID journalId) {
    if (catalogId != null && recipeId != null) throw bad("Yalnızca tek bir kaynak tarif belirtilebilir.");
    if (catalogId != null && catalog.all().stream().noneMatch(r -> r.id().equals(catalogId))) throw notFound("Recipe not found");
    if (recipeId != null) ownedRecipe(recipeId, userId);
    if (sessionId != null) {
      var session = sessions.findById(sessionId).filter(s -> s.getUserId().equals(userId))
          .orElseThrow(() -> notFound("Cooking session not found"));
      if (recipeId != null && session.getRecipeId() != null && !recipeId.equals(session.getRecipeId()))
        throw bad("Cooking session and recipe do not match.");
    }
    if (journalId != null) journal.findByIdAndUserId(journalId, userId)
        .orElseThrow(() -> notFound("Journal entry not found"));
  }
  private Recipe ownedRecipe(UUID id, UUID userId) {
    return recipes.findById(id).filter(r -> r.getUserId() == null || r.getUserId().equals(userId))
        .orElseThrow(() -> notFound("Recipe not found"));
  }
  private IngredientRemainder owned(UUID id, UUID userId) {
    return remainders.findByIdAndUserId(id, userId).orElseThrow(() -> notFound("Remainder not found"));
  }
  private IngredientRemainder lockAvailable(UUID id, UUID userId) {
    IngredientRemainder r = remainders.lockOwned(id, userId).orElseThrow(() -> notFound("Remainder not found"));
    if (!"AVAILABLE".equals(r.getStatus())) throw bad("Remainder is not available.");
    return r;
  }
  private RemainderView duplicate(String key, UUID userId) {
    IngredientRemainderOperation operation = operations.findByUserIdAndOperationKey(userId, key)
        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Idempotency key has already been used."));
    return view(remainders.findByIdAndUserId(operation.getRemainderId(), userId)
        .orElseThrow(() -> notFound("Remainder not found")));
  }
  private static void validateQuantity(BigDecimal q) {
    if (q == null || q.signum() <= 0 || q.scale() > 3 || q.precision() > 12)
      throw bad("Miktar pozitif olmalı ve en fazla 3 ondalık basamak içermeli.");
  }
  private static void validateOperationKey(String key) {
    if (key == null || key.isBlank() || key.length() > 90) throw bad("Geçersiz operationKey.");
  }
  private static RemainderView view(IngredientRemainder r) {
    return new RemainderView(r.getId(), r.getIngredientName(), r.getQuantity(), r.getUnit(),
        r.getCertainty(), r.getStatus(), r.getSourceCatalogRecipeId(), r.getSourceRecipeId(),
        r.getCookingSessionId(), r.getJournalEntryId(), r.getUseByDate(), r.getVersion());
  }
  private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
  private static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
}
