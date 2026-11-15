package com.kitchenai.pantry;

import static org.junit.jupiter.api.Assertions.*;

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
}
