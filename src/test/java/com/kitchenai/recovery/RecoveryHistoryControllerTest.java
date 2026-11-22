package com.kitchenai.recovery;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.support.WebTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

class RecoveryHistoryControllerTest {
  @TempDir Path root;
  private Path storage;
  private final CookingSessionRepository sessions = mock(CookingSessionRepository.class);
  private final RecipeRecoveryAttemptRepository attempts = mock(RecipeRecoveryAttemptRepository.class);
  private final RecoveryAiExecutionRepository executions = mock(RecoveryAiExecutionRepository.class);
  private MockMvc mvc;
  private final UUID user = UUID.randomUUID();
  private final CookingSession mine = new CookingSession(user, null, "RECOVERY", null);
  private final CookingSession theirs = new CookingSession(UUID.randomUUID(), null, "RECOVERY", null);

  @BeforeEach
  void setUp() throws Exception {
    storage = Files.createDirectories(root.resolve("uploads"));
    mvc = WebTestSupport.mvc(
        new RecoveryHistoryController(sessions, attempts, executions, storage.toString()),
        new RecoveryHistoryPageController(sessions, attempts));
    WebTestSupport.authenticate(user);
    when(sessions.findById(mine.getId())).thenReturn(Optional.of(mine));
    when(sessions.findById(theirs.getId())).thenReturn(Optional.of(theirs));
    when(sessions.findByUserIdOrderByStartedAtDesc(user)).thenReturn(List.of(mine));
  }

  @AfterEach void tearDown() { WebTestSupport.clearAuthentication(); }

  private RecipeRecoveryAttempt attempt(CookingSession session, String imageKey) {
    RecipeRecoveryAttempt a = new RecipeRecoveryAttempt(null, session.getId(), null, "stage-1", 1, 1, imageKey,
        "TOO_SOFT", "Hamur civik", null, null);
    a.setFallbackRecipeName("Kek");
    when(attempts.findById(a.getId())).thenReturn(Optional.of(a));
    return a;
  }

  @Test
  void listIsEmptyForAUserWithoutSessions() throws Exception {
    when(sessions.findByUserIdOrderByStartedAtDesc(user)).thenReturn(List.of());
    mvc.perform(get("/api/v1/recovery-history")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    verifyNoInteractions(attempts);
  }

  @Test
  void listOnlyQueriesTheCallersSessions() throws Exception {
    RecipeRecoveryAttempt a = attempt(mine, "k.png");
    when(attempts.findBySessionIdInOrderByCreatedAtDesc(List.of(mine.getId()))).thenReturn(List.of(a));
    mvc.perform(get("/api/v1/recovery-history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(a.getId().toString()))
        .andExpect(jsonPath("$[0].hasImage").value(true))
        .andExpect(jsonPath("$[0].imageObjectKey").doesNotExist());
  }

  @Test
  void detailIncludesExecutionsAndImageUrl() throws Exception {
    RecipeRecoveryAttempt a = attempt(mine, "k.png");
    when(executions.findByAttemptIdOrderByCreatedAtAsc(a.getId())).thenReturn(List.of(
        new RecoveryAiExecution(a.getId(), "VISION_ANALYSIS", "RUNWARE", null, "FAILED", "{}", null, "X", null, 12L)));
    mvc.perform(get("/api/v1/recovery-history/" + a.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executions[0].operation").value("VISION_ANALYSIS"))
        .andExpect(jsonPath("$.executions[0].model").value(""))
        .andExpect(jsonPath("$.imageUrl").value("/api/v1/recovery-history/" + a.getId() + "/image"));
  }

  @Test
  void anotherUsersAttemptIsIndistinguishableFromAMissingOne() throws Exception {
    RecipeRecoveryAttempt foreign = attempt(theirs, "k.png");
    for (String path : List.of("", "/image"))
      mvc.perform(get("/api/v1/recovery-history/" + foreign.getId() + path))
          .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Recovery attempt not found"));
    mvc.perform(post("/api/v1/recovery-history/" + foreign.getId() + "/reuse")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/recovery-history/" + UUID.randomUUID())).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Recovery attempt not found"));
  }

  @Test
  void reuseIsMarkedReferenceOnly() throws Exception {
    RecipeRecoveryAttempt a = attempt(mine, null);
    mvc.perform(post("/api/v1/recovery-history/" + a.getId() + "/reuse"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.reuseMode").value("REFERENCE_ONLY"));
  }

  @Test
  void imageIsServedWithNoStoreCaching() throws Exception {
    Files.createDirectories(storage.resolve(user.toString()));
    byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
    Files.write(storage.resolve(user + "/a.png"), png);
    RecipeRecoveryAttempt a = attempt(mine, user + "/a.png");
    mvc.perform(get("/api/v1/recovery-history/" + a.getId() + "/image"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(png));
  }

  @Test
  void imageKeyCannotEscapeTheStorageDirectory() throws Exception {
    Files.writeString(root.resolve("secret.jpg"), "top secret");
    RecipeRecoveryAttempt a = attempt(mine, "../secret.jpg");
    mvc.perform(get("/api/v1/recovery-history/" + a.getId() + "/image")).andExpect(status().isNotFound());
  }

  @Test
  void missingImageIs404() throws Exception {
    mvc.perform(get("/api/v1/recovery-history/" + attempt(mine, null).getId() + "/image")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/recovery-history/" + attempt(mine, "gone.jpg").getId() + "/image")).andExpect(status().isNotFound());
  }

  // ---- paginated history ----
  @Test
  void pageReturnsSummariesForTheCallersSessions() throws Exception {
    RecipeRecoveryAttempt a = attempt(mine, null);
    when(attempts.findBySessionIdInOrderByCreatedAtDesc(List.of(mine.getId()), PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1));
    mvc.perform(get("/api/v1/recovery-history/page"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].recipeName").value("Kek"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void pageForAUserWithoutSessionsIsEmpty() throws Exception {
    when(sessions.findByUserIdOrderByStartedAtDesc(user)).thenReturn(List.of());
    mvc.perform(get("/api/v1/recovery-history/page").param("page", "2").param("size", "5"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void pageWithDateRangeUsesTheRangeQuery() throws Exception {
    Instant from = Instant.parse("2026-11-01T00:00:00Z"), to = Instant.parse("2026-11-02T00:00:00Z");
    when(attempts.findBySessionIdInAndCreatedAtBetweenOrderByCreatedAtDesc(List.of(mine.getId()), from, to, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of()));
    mvc.perform(get("/api/v1/recovery-history/page").param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk());
  }

  @Test
  void pageRejectsInvalidPaginationAndRanges() throws Exception {
    mvc.perform(get("/api/v1/recovery-history/page").param("size", "51")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/recovery-history/page").param("page", "-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/recovery-history/page").param("to", "2026-11-01T00:00:00Z")).andExpect(status().isBadRequest());
    verifyNoInteractions(attempts);
  }
}
