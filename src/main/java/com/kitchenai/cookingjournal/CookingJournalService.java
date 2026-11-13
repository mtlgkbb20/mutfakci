package com.kitchenai.cookingjournal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.recipe.Recipe;
import com.kitchenai.recipe.RecipeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CookingJournalService {
  private final CookingJournalRepository journal;
  private final CookingSessionRepository sessions;
  private final RecipeRepository recipes;
  private final ObjectMapper mapper;

  public CookingJournalService(
      CookingJournalRepository journal, CookingSessionRepository sessions,
      RecipeRepository recipes, ObjectMapper mapper) {
    this.journal = journal;
    this.sessions = sessions;
    this.recipes = recipes;
    this.mapper = mapper;
  }

  @Transactional
  public CookingJournalEntry create(UUID userId, CreateRequest request) {
    CookingSession session = sessions.findById(request.cookingSessionId())
        .filter(item -> item.getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cooking session not found"));
    if (!"COMPLETED".equals(session.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Only completed cooking sessions can be journaled");
    }
    if (journal.findByUserIdAndCookingSessionId(userId, session.getId()).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "This cooking session is already in your journal");
    }

    Recipe recipe = null;
    if (session.getRecipeId() != null) {
      recipe = recipes.findById(session.getRecipeId())
          .filter(item -> item.getUserId() == null || item.getUserId().equals(userId))
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"));
    }

    try {
      RecipeSnapshot snapshot = recipe == null
          ? new RecipeSnapshot("Tarifsiz pişirme", null, null, request.servings(), null, null, null, null)
          : new RecipeSnapshot(recipe.getTitle(), recipe.getDescription(), recipe.getProductType(),
              request.servings() == null ? recipe.getServings() : request.servings(),
              recipe.getPreparationTime(), recipe.getCookingTime(),
              recipe.getIngredientsJson(), recipe.getStagesJson());
      String snapshotJson = mapper.writeValueAsString(snapshot);
      return journal.save(new CookingJournalEntry(
          userId, session.getId(), recipe == null ? null : recipe.getId(), snapshotJson,
          snapshot.servings(), request.rating(), normalizeNotes(request.notes()), Instant.now()));
    } catch (JsonProcessingException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not snapshot recipe for journal");
    }
  }

  @Transactional(readOnly = true)
  public List<CookingJournalEntry> list(UUID userId) {
    return journal.findTop100ByUserIdOrderByCookedAtDesc(userId);
  }

  @Transactional(readOnly = true)
  public CookingJournalEntry get(UUID userId, UUID entryId) {
    return owned(userId, entryId);
  }

  @Transactional(readOnly = true)
  public List<CookingJournalEntry> attempts(UUID userId, UUID recipeId) {
    recipes.findById(recipeId)
        .filter(item -> item.getUserId() == null || item.getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"));
    return journal.findByUserIdAndRecipeIdOrderByCookedAtDesc(userId, recipeId);
  }

  @Transactional
  public CookingJournalEntry update(UUID userId, UUID entryId, UpdateRequest request) {
    CookingJournalEntry entry = owned(userId, entryId);
    if (request.rating() != null && (request.rating() < 1 || request.rating() > 5)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
    }
    if (request.servings() != null && (request.servings() < 1 || request.servings() > 30)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Servings must be between 1 and 30");
    }
    if (request.notes() != null && request.notes().length() > 2000) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Notes must be at most 2000 characters");
    }
    entry.update(request.servings(), request.rating(), request.notes());
    return journal.save(entry);
  }

  private CookingJournalEntry owned(UUID userId, UUID entryId) {
    return journal.findByIdAndUserId(entryId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Journal entry not found"));
  }

  private String normalizeNotes(String notes) {
    if (notes == null) return null;
    String trimmed = notes.trim();
    if (trimmed.length() > 2000) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Notes must be at most 2000 characters");
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  public record CreateRequest(UUID cookingSessionId, Integer servings, Integer rating, String notes) {}
  public record UpdateRequest(Integer servings, Integer rating, String notes) {}
  public record RecipeSnapshot(String title, String description, String productType, Integer servings,
      Integer preparationTime, Integer cookingTime, String ingredients, String stages) {}
}
