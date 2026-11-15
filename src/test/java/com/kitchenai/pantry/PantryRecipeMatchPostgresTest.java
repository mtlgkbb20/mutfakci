package com.kitchenai.pantry;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class PantryRecipeMatchPostgresTest {
  @Autowired private PantryRecipeMatchRepository recipeMatches;
  @Autowired private JdbcTemplate jdbc;

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("DATABASE_URL"));
    registry.add("spring.datasource.username", () -> System.getenv("DATABASE_USERNAME"));
    registry.add("spring.datasource.password", () -> System.getenv("DATABASE_PASSWORD"));
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Test
  void flywaySeedsAllDiscoveryCategoriesAndPostgresQueryExecutes() {
    assertEquals(12, jdbc.queryForObject("SELECT COUNT(*) FROM recipe_catalog", Integer.class));
    assertEquals(2, jdbc.queryForObject(
        "SELECT COUNT(*) FROM recipe_catalog WHERE category = 'DESSERT'", Integer.class));
    assertEquals(2, jdbc.queryForObject(
        "SELECT COUNT(*) FROM recipe_catalog WHERE category = 'DRINK'", Integer.class));

    var desserts = recipeMatches.findMatches(UUID.randomUUID(), "DESSERT", 4, 0);
    assertEquals(2, desserts.size());
    assertEquals("[]", desserts.get(0).getAvailableJson());
    assertTrue(desserts.get(0).getMissingJson().startsWith("["));
  }

  @Test
  void curatedAliasesAndUnitConversionsProduceAccurateCoverage() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
        "INSERT INTO app_user(id,email,password_hash,display_name,created_at,updated_at) VALUES (?,?,?,?,?,?)",
        userId, userId + "@integration.test", "test-hash", "Recipe Match Test", now, now);

    pantryItem(userId, "Tam yağlı süt", "tam yagli sut", new BigDecimal("1"), "l");
    pantryItem(userId, "Baldo pirinç", "baldo pirinc", new BigDecimal("50"), "g");
    pantryItem(userId, "Toz şeker", "toz seker", new BigDecimal("150"), "g");
    pantryItem(userId, "Su", "su", new BigDecimal("500"), "ml");

    var partial = recipeMatches.findMatches(userId, "DESSERT", 4, 0).get(0);
    assertEquals("sutlac", partial.getRecipeId());
    assertEquals(0.75d, partial.getCoverage(), 0.0001d);
    assertTrue(partial.getMissingJson().contains("Pirinç"));

    jdbc.update(
        "UPDATE pantry_item SET quantity = 100 WHERE user_id = ? AND normalized_name = 'baldo pirinc'",
        userId);
    var complete = recipeMatches.findMatches(userId, "DESSERT", 4, 0).get(0);
    assertEquals("sutlac", complete.getRecipeId());
    assertEquals(1.0d, complete.getCoverage(), 0.0001d);
    assertEquals("[]", complete.getMissingJson());
    assertTrue(complete.getAvailableJson().contains("pantryItemId"));
  }

  private void pantryItem(UUID userId, String name, String normalizedName,
      BigDecimal quantity, String unit) {
    Instant now = Instant.now();
    jdbc.update(
        """
        INSERT INTO pantry_item(
          id,user_id,name,normalized_name,category,quantity,unit,archived,version,created_at,updated_at
        ) VALUES (?,?,?,?,?,?,?,FALSE,0,?,?)
        """,
        UUID.randomUUID(), userId, name, normalizedName, "OTHER", quantity, unit, now, now);
  }
}
