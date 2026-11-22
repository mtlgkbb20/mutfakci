package com.kitchenai.pantry;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class PantryItemRepositoryTest {
  @Autowired private PantryItemRepository items;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void lockedLookupReturnsOldestActiveItemWhenSeveralShareTheKey() {
    UUID user = UUID.randomUUID();
    PantryItem newer = items.saveAndFlush(new PantryItem(user, "Domates salçası", "domates salcasi", "OTHER", BigDecimal.ONE, "piece", null));
    PantryItem older = items.saveAndFlush(new PantryItem(user, "Domates-salçası", "domates salcasi", "OTHER", new BigDecimal("200"), "g", null));
    jdbc.update("UPDATE pantry_item SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), older.getId());

    var found = items.lockFirstByUserAndNormalizedName(user, "domates salcasi");

    assertTrue(found.isPresent());
    assertEquals(older.getId(), found.get().getId());
    assertEquals(2, items.lockAllByUserAndNormalizedName(user, "domates salcasi").size());
    assertEquals(older.getId(), items.findFirstByUserIdAndArchivedFalseAndNormalizedNameOrderByCreatedAtAsc(user, "domates salcasi").orElseThrow().getId());
    assertNotEquals(older.getId(), newer.getId());
  }

  @Test
  void lockedLookupIgnoresArchivedItemsAndOtherUsers() {
    UUID user = UUID.randomUUID();
    PantryItem archived = new PantryItem(user, "Süt", "sut", "OTHER", BigDecimal.ONE, "l", null);
    archived.archive();
    items.saveAndFlush(archived);
    items.saveAndFlush(new PantryItem(UUID.randomUUID(), "Süt", "sut", "OTHER", BigDecimal.ONE, "l", null));

    assertTrue(items.lockFirstByUserAndNormalizedName(user, "sut").isEmpty());
  }
}
