package com.kitchenai.cookingjournal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.recipe.Recipe;
import com.kitchenai.recipe.RecipeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CookingJournalServiceTest {
  private CookingJournalRepository journal;
  private CookingSessionRepository sessions;
  private RecipeRepository recipes;
  private CookingJournalService service;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    journal = mock(CookingJournalRepository.class);
    sessions = mock(CookingSessionRepository.class);
    recipes = mock(RecipeRepository.class);
    service = new CookingJournalService(journal, sessions, recipes, new ObjectMapper());
    when(journal.save(any(CookingJournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createsJournalEntryFromCompletedOwnedSessionAndSnapshotsRecipe() throws Exception {
    Recipe recipe = new Recipe(userId, "Elmalı Kek", "Yumuşak kek", "CAKE", 4, 15, 35,
        "[{\"name\":\"Elma\"}]", "[{\"title\":\"Pişir\"}]");
    CookingSession session = new CookingSession(userId, recipe.getId(), "RECIPE", "Elmalı Kek");
    session.complete();
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(recipes.findById(recipe.getId())).thenReturn(Optional.of(recipe));
    when(journal.findByUserIdAndCookingSessionId(userId, session.getId())).thenReturn(Optional.empty());

    CookingJournalEntry entry = service.create(userId,
        new CookingJournalService.CreateRequest(session.getId(), 3, 5, "İkinci deneme daha iyi oldu."));
    var snapshot = new ObjectMapper().readTree(entry.getRecipeSnapshotJson());

    assertEquals(recipe.getId(), entry.getRecipeId());
    assertEquals("Elmalı Kek", snapshot.get("title").asText());
    assertEquals(3, entry.getServings());
    assertEquals(5, entry.getRating());
    assertEquals("İkinci deneme daha iyi oldu.", entry.getNotes());
    verify(journal).save(any(CookingJournalEntry.class));
  }

  @Test
  void rejectsActiveSession() {
    CookingSession session = new CookingSession(userId, null, "RECOVERY", null);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

    assertThrows(ApiException.class, () -> service.create(userId,
        new CookingJournalService.CreateRequest(session.getId(), 2, null, null)));
    verify(journal, never()).save(any());
  }

  @Test
  void rejectsSessionOwnedByAnotherUser() {
    CookingSession session = new CookingSession(UUID.randomUUID(), null, "RECOVERY", null);
    session.complete();
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

    assertThrows(ApiException.class, () -> service.create(userId,
        new CookingJournalService.CreateRequest(session.getId(), 2, null, null)));
    verify(journal, never()).save(any());
  }

  @Test
  void preventsDuplicateJournalEntriesForSameSession() {
    CookingSession session = new CookingSession(userId, null, "RECOVERY", null);
    session.complete();
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(journal.findByUserIdAndCookingSessionId(userId, session.getId()))
        .thenReturn(Optional.of(mock(CookingJournalEntry.class)));

    assertThrows(ApiException.class, () -> service.create(userId,
        new CookingJournalService.CreateRequest(session.getId(), 2, null, null)));
    verify(journal, never()).save(any());
  }

  @Test
  void updatesOnlyEditableFieldsAndKeepsSnapshotUntouched() {
    String snapshot = "{\"title\":\"Original title\"}";
    CookingJournalEntry entry = new CookingJournalEntry(userId, UUID.randomUUID(), null,
        snapshot, 2, 3, "Old note", null);
    when(journal.findByIdAndUserId(entry.getId(), userId)).thenReturn(Optional.of(entry));

    CookingJournalEntry updated = service.update(userId, entry.getId(),
        new CookingJournalService.UpdateRequest(4, 5, "New note"));

    assertEquals(snapshot, updated.getRecipeSnapshotJson());
    assertEquals(4, updated.getServings());
    assertEquals(5, updated.getRating());
    assertEquals("New note", updated.getNotes());
  }

  @Test
  void hidesEntriesOwnedByAnotherUser() {
    UUID entryId = UUID.randomUUID();
    when(journal.findByIdAndUserId(entryId, userId)).thenReturn(Optional.empty());

    assertThrows(ApiException.class, () -> service.get(userId, entryId));
  }
}
