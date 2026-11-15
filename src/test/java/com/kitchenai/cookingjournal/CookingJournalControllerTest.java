package com.kitchenai.cookingjournal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CookingJournalControllerTest {
  @Test
  void journalListReturnsPagedProjectionAndBulkPhotoState() {
    UUID userId = UUID.randomUUID();
    UUID entryId = UUID.randomUUID();
    CookingJournalService service = mock(CookingJournalService.class);
    JournalMediaStorage media = mock(JournalMediaStorage.class);
    CookingJournalController controller =
        new CookingJournalController(service, media, new ObjectMapper());
    CookingJournalSummaryProjection row = mock(CookingJournalSummaryProjection.class);
    when(row.getId()).thenReturn(entryId);
    when(row.getCookingSessionId()).thenReturn(UUID.randomUUID());
    when(row.getRecipeId()).thenReturn(UUID.randomUUID());
    when(row.getRecipeTitle()).thenReturn("Elmalı Kek");
    when(row.getServings()).thenReturn(4);
    when(row.getRating()).thenReturn(5);
    when(row.getCookedAt()).thenReturn(Instant.now());
    when(service.list(userId, 0, 20)).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 25));
    when(service.findPhotoEntryIds(userId, List.of(entryId))).thenReturn(Set.of(entryId));

    var response = controller.list(userId, 0, 20);

    assertEquals(1, response.content().size());
    assertEquals(25, response.totalElements());
    assertEquals(2, response.totalPages());
    assertTrue(response.hasNext());
    assertEquals("Elmalı Kek", response.content().get(0).title());
    assertTrue(response.content().get(0).hasPhoto());
  }

  @Test
  void variationSummaryUsesProjectionWithoutIngredientsOrStages() {
    UUID userId = UUID.randomUUID();
    CookingJournalService service = mock(CookingJournalService.class);
    JournalMediaStorage media = mock(JournalMediaStorage.class);
    CookingJournalController controller =
        new CookingJournalController(service, media, new ObjectMapper());
    RecipeVariationSummaryProjection row = mock(RecipeVariationSummaryProjection.class);
    when(row.getId()).thenReturn(UUID.randomUUID());
    when(row.getTitle()).thenReturn("Baharatlı Elmalı Kek");
    when(row.getDescription()).thenReturn("Tarçın miktarı artırıldı.");
    when(row.getServings()).thenReturn(4);
    when(row.getCreatedAt()).thenReturn(Instant.now());
    when(service.listVariationSummaries(userId, 0, 20))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    var response = controller.listVariationSummaries(userId, 0, 20);

    assertEquals(1, response.content().size());
    assertEquals("Baharatlı Elmalı Kek", response.content().get(0).title());
    assertEquals("Tarçın miktarı artırıldı.", response.content().get(0).description());
  }
}
