package com.kitchenai.cookingjournal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CookingJournalControllerTest {
  @Test
  void journalListReturnsSummaryWithoutSnapshotOrNotes() {
    UUID userId = UUID.randomUUID();
    CookingJournalService service = mock(CookingJournalService.class);
    JournalMediaStorage media = mock(JournalMediaStorage.class);
    CookingJournalController controller =
        new CookingJournalController(service, media, new ObjectMapper());
    CookingJournalEntry entry = new CookingJournalEntry(userId, UUID.randomUUID(), UUID.randomUUID(),
        "{\"title\":\"Elmalı Kek\",\"ingredients\":\"large payload\"}",
        4, 5, "private note", null);
    when(service.list(userId)).thenReturn(List.of(entry));
    when(service.hasPhoto(userId, entry.getId())).thenReturn(true);

    List<CookingJournalController.EntrySummary> summaries = controller.list(userId);

    assertEquals(1, summaries.size());
    assertEquals(entry.getId(), summaries.get(0).id());
    assertEquals("Elmalı Kek", summaries.get(0).title());
    assertEquals(4, summaries.get(0).servings());
    assertEquals(5, summaries.get(0).rating());
    assertTrue(summaries.get(0).hasPhoto());
  }
}
