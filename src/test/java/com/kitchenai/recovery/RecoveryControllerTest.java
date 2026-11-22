package com.kitchenai.recovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.recipe.RecipeRepository;
import com.kitchenai.recovery.RecoveryContracts.*;
import com.kitchenai.support.WebTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class RecoveryControllerTest {
  @TempDir Path storage;
  private final CookingSessionRepository sessions = mock(CookingSessionRepository.class);
  private final RecipeRepository recipes = mock(RecipeRepository.class);
  private final RecipeRecoveryAttemptRepository attempts = mock(RecipeRecoveryAttemptRepository.class);
  private final RecoveryAiExecutionRepository executions = mock(RecoveryAiExecutionRepository.class);
  private final RecoveryVisionService vision = mock(RecoveryVisionService.class);
  private final TextFallbackAdviceService text = mock(TextFallbackAdviceService.class);
  private final ObjectMapper json = new ObjectMapper();
  private MockMvc mvc;
  private final UUID user = UUID.randomUUID();
  private CookingSession session;

  @BeforeEach
  void setUp() {
    mvc = WebTestSupport.mvc(new RecoveryController(sessions, recipes, attempts, executions, vision, text, json,
        storage.toString(), "vision-model", "text-model"));
    WebTestSupport.authenticate(user);
    session = new CookingSession(user, null, "RECOVERY", null);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(attempts.save(any(RecipeRecoveryAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
    when(attempts.findTopBySessionIdAndComponentKeyOrderByAttemptNumberDesc(any(), any())).thenReturn(Optional.empty());
    when(vision.available()).thenReturn(true);
    when(text.suggest(anyString(), anyString())).thenReturn(new TextFallbackAdviceService.Advice("Biraz dinlendir", "Dikkat"));
  }

  @AfterEach void tearDown() { WebTestSupport.clearAuthentication(); }

  static byte[] image(String format, int w, int h) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, format, out);
    return out.toByteArray();
  }

  private static MockPart part(String name, String value) {
    MockPart part = new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    part.getHeaders().setContentType(MediaType.TEXT_PLAIN);
    return part;
  }

  private MockMultipartHttpServletRequestBuilder initial(UUID sessionId, MockMultipartFile file) {
    return (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/cooking-sessions/" + sessionId + "/recovery-attempts")
        .file(file).part(part("recipeName", "Elmali kek")).part(part("problemDescription", "Hamur cok civik"));
  }

  private ResultActions sendInitial(byte[] bytes, String type) throws Exception {
    return mvc.perform(initial(session.getId(), new MockMultipartFile("image", "photo", type, bytes)));
  }

  private ResultActions sendPng() throws Exception { return sendInitial(image("png", 200, 200), "image/png"); }

  private static Analysis analysis(int trust, String confidence, int proximity, ActionType action) {
    return new Analysis(trust, new BigDecimal(confidence), "TOO_SOFT", "Cok yumusak", "akiskan", "sert",
        "kivam eksik", "su fazla", proximity, new Action(action, action == ActionType.NONE ? null : "5 dk dinlendir", null, null, 300));
  }

  // ---- input validation & ownership ----
  @Test
  void anotherUsersSessionIs404AndNothingIsStored() throws Exception {
    CookingSession theirs = new CookingSession(UUID.randomUUID(), null, "RECOVERY", null);
    when(sessions.findById(theirs.getId())).thenReturn(Optional.of(theirs));
    mvc.perform(initial(theirs.getId(), new MockMultipartFile("image", "p", "image/png", image("png", 200, 200))))
        .andExpect(status().isNotFound());
    verify(attempts, never()).save(any());
    try (var files = Files.list(storage)) { assertEquals(0, files.count()); }
  }

  @Test
  void inactiveSessionIs409() throws Exception {
    session.complete();
    sendPng().andExpect(status().isConflict());
  }

  @Test
  void unsupportedCategoryIs400() throws Exception {
    mvc.perform(initial(session.getId(), new MockMultipartFile("image", "p", "image/png", image("png", 200, 200)))
            .part(part("problemCategory", "BURNT_TOAST")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Unsupported problem category"));
  }

  @Test
  void hourlyAttemptLimitIs429() throws Exception {
    when(attempts.countBySessionIdAndCreatedAtAfter(eq(session.getId()), any())).thenReturn(20L);
    sendPng().andExpect(status().isTooManyRequests());
    verifyNoInteractions(vision);
  }

  @Test
  void nonImageContentTypeIs400() throws Exception {
    sendInitial(image("png", 200, 200), "image/gif").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Use a JPEG or PNG photo"));
  }

  @Test
  void undecodableBytesAre400() throws Exception {
    sendInitial("definitely not a png".getBytes(), "image/png").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("The image could not be decoded"));
  }

  @Test
  void tooSmallImagesAre400() throws Exception {
    sendInitial(image("png", 159, 400), "image/png").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Image dimensions are not supported"));
  }

  @Test
  void overlongDesiredOutcomeIs400() throws Exception {
    mvc.perform(initial(session.getId(), new MockMultipartFile("image", "p", "image/png", image("png", 200, 200)))
            .part(part("desiredOutcome", "x".repeat(501))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void imageIsReEncodedAndStoredUnderTheOwnersDirectory() throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(90, "0.9", 95, ActionType.NONE));
    sendInitial(image("jpg", 200, 200), "image/jpeg").andExpect(status().isOk());
    try (var files = Files.list(storage.resolve(user.toString()))) {
      List<Path> stored = files.toList();
      assertEquals(1, stored.size());
      assertTrue(stored.get(0).toString().endsWith(".jpg"));
      assertNotNull(ImageIO.read(stored.get(0).toFile()));
    }
  }

  // ---- decision thresholds ----
  @ParameterizedTest(name = "proximity {0} -> {1}")
  @CsvSource({
    "95, READY_TO_CONTINUE, NONE",
    "90, READY_TO_CONTINUE, NONE",
    "80, CORRECTION_PROPOSED, REST",
    "60, OFFER_RECOVERY_OR_REMAKE, MIX",
    "20, REMAKE_COMPONENT, REMAKE_COMPONENT",
  })
  void trustedAnalysisFollowsTheDecisionPolicy(int proximity, Status expected, ActionType action) throws Exception {
    when(vision.analyze(any(), eq("image/png"), anyString())).thenReturn(analysis(90, "0.9", proximity, action));
    sendPng().andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(expected.name()))
        .andExpect(jsonPath("$.targetProximityScore").value(proximity));
    verifyNoInteractions(text);
  }

  @Test
  void minorCorrectionReturnsTheActionAndAFollowUpPrompt() throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(90, "0.9", 80, ActionType.REST));
    sendPng().andExpect(jsonPath("$.action.type").value("REST"))
        .andExpect(jsonPath("$.followUp.requestPhoto").value(true));
  }

  @Test
  void lowImageTrustFallsBackToTextAdvice() throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(40, "0.9", 95, ActionType.NONE));
    sendPng().andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"))
        .andExpect(jsonPath("$.textAdvice.advice").value("Biraz dinlendir"));
  }

  @Test
  void lowConfidenceFallsBackToTextAdvice() throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(90, "0.5", 95, ActionType.NONE));
    sendPng().andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"));
  }

  @Test
  void unsafeActionForARecoverableGapIsRejected() throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(90, "0.9", 60, ActionType.ADJUST_TEMPERATURE));
    sendPng().andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"));
  }

  @ParameterizedTest
  @CsvSource({"150, 90, 0.9", "-5, 90, 0.9", "80, 101, 0.9", "80, 90, 1.5"})
  void outOfRangeModelScoresFallBackToTextInsteadOf500(int proximity, int trust, String confidence) throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(trust, confidence, proximity, ActionType.REST));
    sendPng().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"));
  }

  @Test
  void incompleteAnalysisFallsBackToText() throws Exception {
    when(vision.analyze(any(), any(), any())).thenReturn(new Analysis(90, new BigDecimal("0.9"), null, null, null,
        null, null, null, 80, new Action(ActionType.REST, "x", null, null, null)));
    sendPng().andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"));
  }

  @Test
  void unavailableVisionProviderUsesTextAndRecordsTheExecution() throws Exception {
    when(vision.available()).thenReturn(false);
    sendPng().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"));
    verify(vision, never()).analyze(any(), any(), any());
    verify(executions, atLeast(2)).save(any());
  }

  @Test
  void unparseableVisionOutputUsesText() throws Exception {
    when(vision.analyze(any(), any(), any())).thenThrow(new RecoveryAnalysisFormatException("bad json", new RuntimeException()));
    sendPng().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TEXT_FALLBACK_COMPLETE"));
  }

  @Test
  void textFallbackFailureStillAnswers200WithUnavailableStatus() throws Exception {
    when(vision.available()).thenReturn(false);
    when(text.suggest(any(), any())).thenThrow(new RuntimeException("down"));
    sendPng().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TEXT_FALLBACK_UNAVAILABLE"));
  }

  @Test
  void auditFailureDoesNotBreakTheResponse() throws Exception {
    when(executions.save(any())).thenThrow(new RuntimeException("audit db down"));
    when(vision.analyze(any(), any(), any())).thenReturn(analysis(90, "0.9", 95, ActionType.NONE));
    sendPng().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_TO_CONTINUE"));
  }

  // ---- confirm / follow-up / decision state machine ----
  private RecipeRecoveryAttempt attempt(String status) {
    RecipeRecoveryAttempt a = new RecipeRecoveryAttempt(null, session.getId(), null, "stage-1", 1, 1, "k",
        null, "Hamur cok civik", null, null);
    a.setFallbackRecipeName("Elmali kek");
    a.applyAnalysis(90, new BigDecimal("0.9"), 60, "{}", "{}", "{}",
        "{\"type\":\"MIX\",\"instruction\":\"Karistir\"}", status);
    when(attempts.findById(a.getId())).thenReturn(Optional.of(a));
    return a;
  }

  private String base() { return "/api/v1/cooking-sessions/" + session.getId() + "/recovery-attempts/"; }

  @Test
  void confirmMovesAProposedCorrectionToAwaitingFollowUp() throws Exception {
    RecipeRecoveryAttempt a = attempt("CORRECTION_PROPOSED");
    mvc.perform(post(base() + a.getId() + "/confirm-action").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_FOLLOW_UP"));
    assertEquals("AWAITING_FOLLOW_UP", a.getStatus());
    assertNotNull(a.getUserActionConfirmedAt());
  }

  @Test
  void confirmWithoutConfirmationOrPendingCorrectionIs409() throws Exception {
    RecipeRecoveryAttempt proposed = attempt("CORRECTION_PROPOSED");
    mvc.perform(post(base() + proposed.getId() + "/confirm-action").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":false}"))
        .andExpect(status().isConflict());
    RecipeRecoveryAttempt ready = attempt("READY_TO_CONTINUE");
    mvc.perform(post(base() + ready.getId() + "/confirm-action").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("No pending correction for this attempt"));
  }

  @Test
  void attemptFromAnotherSessionIs404() throws Exception {
    RecipeRecoveryAttempt foreign = new RecipeRecoveryAttempt(null, UUID.randomUUID(), null, "c", 1, 1, "k", null, "d", null, null);
    when(attempts.findById(foreign.getId())).thenReturn(Optional.of(foreign));
    mvc.perform(post(base() + foreign.getId() + "/confirm-action").contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void followUpRequiresAConfirmedCorrection() throws Exception {
    RecipeRecoveryAttempt a = attempt("CORRECTION_PROPOSED");
    mvc.perform(multipart(base() + a.getId() + "/follow-up")
            .file(new MockMultipartFile("image", "p", "image/png", image("png", 200, 200)))
            .part(part("statusText", "biraz daha iyi")))
        .andExpect(status().isConflict());
  }

  @Test
  void followUpAfterConfirmationCreatesAChildAttemptAndClosesTheParent() throws Exception {
    RecipeRecoveryAttempt parent = attempt("CORRECTION_PROPOSED");
    parent.confirmAction();
    when(vision.analyze(any(), any(), contains("previousAttempts"))).thenReturn(analysis(90, "0.9", 95, ActionType.NONE));
    mvc.perform(multipart(base() + parent.getId() + "/follow-up")
            .file(new MockMultipartFile("image", "p", "image/png", image("png", 200, 200)))
            .part(part("statusText", "simdi daha iyi")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_TO_CONTINUE"));
    assertEquals("FOLLOW_UP_RECEIVED", parent.getStatus());
  }

  @Test
  void followUpRequiresAStatusText() throws Exception {
    RecipeRecoveryAttempt parent = attempt("CORRECTION_PROPOSED");
    parent.confirmAction();
    mvc.perform(multipart(base() + parent.getId() + "/follow-up")
            .file(new MockMultipartFile("image", "p", "image/png", image("png", 200, 200)))
            .part(part("statusText", " ")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void decisionRemakeOrRecoveryOnlyWhenOffered() throws Exception {
    RecipeRecoveryAttempt remake = attempt("RECOVERY_OFFERED");
    mvc.perform(post(base() + remake.getId() + "/decision").contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"REMAKE_COMPONENT\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REMAKE_COMPONENT"));
    assertEquals("REMAKE_RECOMMENDED", remake.getStatus());

    RecipeRecoveryAttempt recover = attempt("RECOVERY_OFFERED");
    mvc.perform(post(base() + recover.getId() + "/decision").contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"TRY_RECOVERY\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CORRECTION_PROPOSED"))
        .andExpect(jsonPath("$.action.type").value("MIX"));
    assertEquals("CORRECTION_PROPOSED", recover.getStatus());

    RecipeRecoveryAttempt bogus = attempt("RECOVERY_OFFERED");
    mvc.perform(post(base() + bogus.getId() + "/decision").contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"PANIC\"}"))
        .andExpect(status().isBadRequest());

    RecipeRecoveryAttempt done = attempt("READY_TO_CONTINUE");
    mvc.perform(post(base() + done.getId() + "/decision").contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"TRY_RECOVERY\"}"))
        .andExpect(status().isConflict());
  }
}
