package com.kitchenai.common;

import static org.junit.jupiter.api.Assertions.*;

import com.kitchenai.gamification.CookingEventService;
import com.kitchenai.gamification.GamificationService;
import com.kitchenai.membership.EntitlementService;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs the gamification, event and entitlement queries that bind java.time.Instant against real
 * PostgreSQL. H2 accepts Instant parameters, so only this test reproduces the production failure.
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JavaTimeJdbcPostgresTest {
  private static final String SCHEMA = "java_time_jdbc_check";
  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbc;
  private UUID ada;
  private UUID bob;

  @BeforeAll
  void migrate() {
    String url = System.getenv("DATABASE_URL");
    Flyway flyway = Flyway.configure()
        .dataSource(url, System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"))
        .schemas(SCHEMA).cleanDisabled(false).load();
    flyway.clean();
    flyway.migrate();
    dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA,
        System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"));
    jdbc = new JavaTimeJdbcTemplate(dataSource);
  }

  @AfterAll
  void clean() {
    Flyway.configure().dataSource(System.getenv("DATABASE_URL"), System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"))
        .schemas(SCHEMA).cleanDisabled(false).load().clean();
  }

  @BeforeEach
  void users() {
    jdbc.execute("TRUNCATE app_user CASCADE");
    ada = user("ada@example.com", "Ada", true);
    bob = user("bob@example.com", "Bob", false);
  }

  private UUID user(String email, String name, boolean admin) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,created_at,updated_at,is_admin) VALUES (?,?,'x',?,now(),now(),?)",
        id, email, name, admin);
    return id;
  }

  @Test
  void plainJdbcTemplateReproducesTheProductionFailure() {
    JdbcTemplate plain = new JdbcTemplate(dataSource);
    var error = assertThrows(BadSqlGrammarException.class,
        () -> plain.update("UPDATE gamification_reward_grant SET status='AVAILABLE' WHERE reserved_at < ?", Instant.now()));
    assertTrue(error.getMostSpecificCause().getMessage().contains("java.time.Instant"), error.getMostSpecificCause().getMessage());
  }

  @Test
  void releaseStalePromoReservationsReleasesOnlyReservationsOlderThanAnHour() {
    UUID stale = reward(bob, "RESERVED", Instant.now().minus(Duration.ofHours(2)));
    UUID fresh = reward(bob, "RESERVED", Instant.now().minus(Duration.ofMinutes(10)));

    new EntitlementService(jdbc).releaseStalePromoReservations();

    assertEquals("AVAILABLE", status(stale));
    assertEquals("RESERVED", status(fresh));
  }

  @Test
  void quotaReservationAndSummaryWorkOnPostgres() {
    EntitlementService entitlements = new EntitlementService(jdbc);
    entitlements.ensureMembership(bob);
    var reservation = entitlements.reserve(bob);
    assertNotNull(reservation);
    entitlements.commit(reservation);
    assertEquals(2, ((Number) entitlements.summary(bob).get("remaining")).intValue());
  }

  @Test
  void createdEventMovesToVotingWhenTheVotingWindowOpens() {
    var gamification = new GamificationService(jdbc);
    var events = new CookingEventService(jdbc, gamification);
    Instant now = Instant.now();
    UUID eventId = events.create(ada, Map.of(
        "title", "Tiramisu haftası", "description", "Kurallar", "theme", "Tiramisu",
        "startsAt", now.minus(Duration.ofDays(3)).toString(),
        "submissionOpensAt", now.minus(Duration.ofDays(2)).toString(),
        "submissionClosesAt", now.minus(Duration.ofDays(1)).toString(),
        "votingOpensAt", now.minus(Duration.ofHours(1)).toString(),
        "votingClosesAt", now.plus(Duration.ofDays(1)).toString()));
    gamification.grantXp(bob, 30, "TEST", "t", "pg-test-xp-" + UUID.randomUUID(), "test");

    events.advanceScheduledEvents();

    assertEquals("VOTING", jdbc.queryForObject("SELECT status FROM cooking_event WHERE id=?", String.class, eventId));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM event_voter_snapshot WHERE event_id=?", Integer.class, eventId));
  }

  @Test
  void weeklyXpRankAndLeaderboardWorkOnPostgres() {
    var gamification = new GamificationService(jdbc);
    gamification.grantXp(bob, 40, "TEST", "t", "pg-bob-" + UUID.randomUUID(), "test");
    gamification.grantXp(ada, 10, "TEST", "t", "pg-ada-" + UUID.randomUUID(), "test");

    var me = gamification.me(bob);
    assertEquals(40, me.get("weeklyXp"));
    assertEquals(1, me.get("rank"));
    var board = gamification.leaderboard(0, 10);
    assertEquals(2, board.get("total"));
    @SuppressWarnings("unchecked") var entries = (List<Map<String, Object>>) board.get("entries");
    assertEquals(bob, entries.get(0).get("userid"));
    assertDoesNotThrow(() -> gamification.settleWeek(gamification.currentWeek().start().minusWeeks(1)));
  }

  private UUID reward(UUID userId, String status, Instant reservedAt) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO gamification_reward_grant(id,user_id,reward_type,source_key,status,issued_at,expires_at,reserved_at) VALUES (?,?,'FREE_RECIPE',?,?,?,?,?)",
        id, userId, "pg-test:" + id, status, Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(29)), reservedAt);
    return id;
  }

  private String status(UUID rewardId) {
    return jdbc.queryForObject("SELECT status FROM gamification_reward_grant WHERE id=?", String.class, rewardId);
  }
}
