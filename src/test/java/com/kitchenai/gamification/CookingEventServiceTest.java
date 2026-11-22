package com.kitchenai.gamification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class CookingEventServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final GamificationService gamification = mock(GamificationService.class);
  private final CookingEventService service = new CookingEventService(jdbc, gamification);
  private final UUID eventId = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final Instant now = Instant.now();

  private static Timestamp at(Instant i) { return Timestamp.from(i); }

  /** Event whose submission window is open now and whose voting is in the future. */
  private Map<String, Object> event(String status, Duration submissionOffset, Duration votingOffset) {
    Map<String, Object> row = new HashMap<>();
    row.put("status", status);
    row.put("submission_opens_at", at(now.plus(submissionOffset).minusSeconds(3600)));
    row.put("submission_closes_at", at(now.plus(submissionOffset).plusSeconds(3600)));
    row.put("voting_opens_at", at(now.plus(votingOffset).minusSeconds(3600)));
    row.put("voting_closes_at", at(now.plus(votingOffset).plusSeconds(3600)));
    row.put("max_images", 2);
    return row;
  }

  private void stubEvent(Map<String, Object> row) {
    when(jdbc.queryForList(startsWith("SELECT * FROM cooking_event"), eq(eventId))).thenReturn(List.of(row));
  }

  private static HttpStatus status(Executable call) {
    return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
  }
  interface Executable { void run() throws Throwable; }

  // ---- create ----
  private Map<String, Object> createBody() {
    Map<String, Object> body = new HashMap<>();
    body.put("title", "Kek yarışması"); body.put("description", "Açıklama"); body.put("theme", "Tatlı");
    body.put("startsAt", "2026-12-01T09:00:00Z");
    body.put("submissionOpensAt", "2026-12-01T10:00:00Z");
    body.put("submissionClosesAt", "2026-12-03T10:00:00Z");
    body.put("votingOpensAt", "2026-12-03T10:00:00Z");
    body.put("votingClosesAt", "2026-12-05T10:00:00Z");
    return body;
  }

  @Test
  void createPersistsAScheduledEventWithDefaultMaxImages() {
    UUID id = service.create(user, createBody());
    assertNotNull(id);
    verify(jdbc).update(contains("INSERT INTO cooking_event"), eq(id), eq("Kek yarışması"), eq("Açıklama"), eq("Tatlı"),
        any(), any(), any(), any(), any(), eq(4), eq(user));
  }

  @Test
  void createRejectsAnOutOfOrderTimeline() {
    Map<String, Object> body = createBody();
    body.put("votingOpensAt", "2026-12-02T10:00:00Z"); // before submission closes
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(user, body)));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"title", "description", "theme", "startsAt", "votingClosesAt"})
  void createRejectsMissingRequiredFieldsWith400(String field) {
    Map<String, Object> body = createBody();
    body.remove(field);
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(user, body)));
  }

  @Test
  void createRejectsMalformedTimestampsAndMaxImagesWith400() {
    Map<String, Object> badDate = createBody();
    badDate.put("startsAt", "tomorrow");
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(user, badDate)));
    Map<String, Object> badMax = createBody();
    badMax.put("maxImages", "many");
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(user, badMax)));
  }

  // ---- join / submit ----
  @Test
  void joinIsIdempotentInsideTheSubmissionWindow() {
    stubEvent(event("SCHEDULED", Duration.ZERO, Duration.ofDays(3)));
    service.join(eventId, user);
    verify(jdbc).update(contains("ON CONFLICT DO NOTHING"), eq(eventId), eq(user));
  }

  @Test
  void joinIsRejectedBeforeOrAfterTheWindowOrForNonScheduledEvents() {
    stubEvent(event("SCHEDULED", Duration.ofDays(1), Duration.ofDays(3)));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.join(eventId, user)));
    stubEvent(event("SCHEDULED", Duration.ofDays(-1), Duration.ofDays(3)));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.join(eventId, user)));
    stubEvent(event("VOTING", Duration.ZERO, Duration.ofDays(3)));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.join(eventId, user)));
  }

  @Test
  void unknownEventIs404() {
    when(jdbc.queryForList(startsWith("SELECT * FROM cooking_event"), eq(eventId))).thenReturn(List.of());
    assertEquals(HttpStatus.NOT_FOUND, status(() -> service.join(eventId, user)));
  }

  @Test
  void submitStoresEveryImageForTheSubmitter() {
    stubEvent(event("SCHEDULED", Duration.ZERO, Duration.ofDays(3)));
    UUID id = service.submit(eventId, user, "Afiyet", List.of("image/png", "image/jpeg"), List.of(new byte[] {1}, new byte[] {2}));
    verify(jdbc).update(contains("INSERT INTO event_submission("), eq(id), eq(eventId), eq(user), eq("Afiyet"));
    verify(jdbc, times(2)).update(contains("INSERT INTO event_submission_image"), any(), eq(id), anyString(), any(byte[].class));
  }

  @Test
  void submitEnforcesTheEventsMaxImages() {
    stubEvent(event("SCHEDULED", Duration.ZERO, Duration.ofDays(3)));
    List<byte[]> three = List.of(new byte[] {1}, new byte[] {2}, new byte[] {3});
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.submit(eventId, user, null, List.of("a", "b", "c"), three)));
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.submit(eventId, user, null, List.of(), List.of())));
  }

  // ---- vote ----
  private void votingEvent() { stubEvent(event("VOTING", Duration.ofDays(-3), Duration.ZERO)); }

  private void voterEligible(boolean eligible) {
    when(jdbc.queryForObject(contains("event_voter_snapshot"), eq(Boolean.class), eq(eventId), eq(user))).thenReturn(eligible);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 11, -1})
  void voteScoreMustBeBetween1And10(int score) {
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.vote(eventId, user, UUID.randomUUID(), score)));
    verifyNoInteractions(jdbc);
  }

  @Test
  void eligibleVoterCanUpsertAScore() {
    votingEvent(); voterEligible(true);
    UUID submission = UUID.randomUUID();
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), eq(submission), eq(eventId)))
        .thenReturn(List.of(UUID.randomUUID()));
    service.vote(eventId, user, submission, 10);
    verify(jdbc).update(contains("INSERT INTO event_vote"), any(), eq(eventId), eq(submission), eq(user), eq(10));
  }

  @Test
  void nonEligibleVoterGets403() {
    votingEvent(); voterEligible(false);
    assertEquals(HttpStatus.FORBIDDEN, status(() -> service.vote(eventId, user, UUID.randomUUID(), 5)));
  }

  @Test
  void selfVotingIsRejected() {
    votingEvent(); voterEligible(true);
    UUID submission = UUID.randomUUID();
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), eq(submission), eq(eventId)))
        .thenReturn(List.of(user));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.vote(eventId, user, submission, 7)));
  }

  @Test
  void votingOnAMissingOrUnapprovedSubmissionIs404NotA500() {
    votingEvent(); voterEligible(true);
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), any(), eq(eventId))).thenReturn(List.of());
    assertEquals(HttpStatus.NOT_FOUND, status(() -> service.vote(eventId, user, UUID.randomUUID(), 7)));
  }

  @Test
  void votingOutsideTheVotingWindowIsRejected() {
    stubEvent(event("SCHEDULED", Duration.ZERO, Duration.ZERO));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.vote(eventId, user, UUID.randomUUID(), 5)));
  }

  // ---- openVoting / finalize ----
  @Test
  void openVotingIsIdempotentForAnEventAlreadyInVoting() {
    votingEvent();
    service.openVoting(eventId);
    verify(jdbc, never()).update(contains("status='VOTING'"), any(Object[].class));
  }

  @Test
  void openVotingSnapshotsTopWeeklyUsersAsVoters() {
    stubEvent(event("SCHEDULED", Duration.ofDays(-3), Duration.ZERO));
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    when(jdbc.queryForList(contains("xp_ledger_entry"), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(Map.of("user_id", a), Map.of("user_id", b)));
    service.openVoting(eventId);
    verify(jdbc).update(contains("event_voter_snapshot"), eq(eventId), eq(a), eq(1), any());
    verify(jdbc).update(contains("event_voter_snapshot"), eq(eventId), eq(b), eq(2), any());
    verify(jdbc).update(contains("status='VOTING'"), eq(eventId));
  }

  @Test
  void openVotingBeforeVotingOpensIsRejected() {
    stubEvent(event("SCHEDULED", Duration.ZERO, Duration.ofDays(3)));
    assertEquals(HttpStatus.CONFLICT, status(() -> service.openVoting(eventId)));
  }

  @Test
  void finalizeRanksSubmissionsAndAwardsPodiumXpAndBadgeOnce() {
    stubEvent(event("VOTING", Duration.ofDays(-5), Duration.ofDays(-2)));
    UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID(), fourth = UUID.randomUUID();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (UUID owner : List.of(first, second, third, fourth))
      rows.add(Map.of("id", UUID.randomUUID(), "user_id", owner, "avg_score", new BigDecimal("8.500"), "vote_count", 3));
    when(jdbc.queryForList(contains("AVG(v.score)"), eq(eventId))).thenReturn(rows);

    Map<String, Object> result = service.finalizeEvent(eventId);

    assertEquals(Map.of("status", "COMPLETED", "scoredSubmissions", 4), result);
    verify(gamification).grantXp(eq(first), eq(30), eq("EVENT_PLACEMENT"), anyString(), eq("event-placement:" + eventId + ":" + first), anyString());
    verify(gamification).grantXp(eq(second), eq(20), anyString(), anyString(), anyString(), anyString());
    verify(gamification).grantXp(eq(third), eq(10), anyString(), anyString(), anyString(), anyString());
    verify(gamification, never()).grantXp(eq(fourth), anyInt(), anyString(), anyString(), anyString(), anyString());
    verify(gamification).awardBadge(first, "PEOPLES_CHOICE", "event:" + eventId);
    verify(jdbc).update(contains("status='COMPLETED'"), eq(eventId));
  }

  @Test
  void finalizingACompletedEventIsANoOp() {
    stubEvent(event("COMPLETED", Duration.ofDays(-5), Duration.ofDays(-2)));
    when(jdbc.queryForObject(contains("event_result"), eq(Integer.class), eq(eventId))).thenReturn(2);
    assertEquals(Map.of("status", "ALREADY_COMPLETED", "scoredSubmissions", 2), service.finalizeEvent(eventId));
    verifyNoInteractions(gamification);
  }

  @Test
  void finalizeBeforeVotingClosesIsRejected() {
    votingEvent();
    assertEquals(HttpStatus.CONFLICT, status(() -> service.finalizeEvent(eventId)));
  }

  // ---- moderate ----
  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "approved", ""})
  void moderationRequiresAKnownStatus(String status) {
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.moderate(eventId, UUID.randomUUID(), status, "neden")));
  }

  @Test
  void moderationRequiresAReason() {
    assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.moderate(eventId, UUID.randomUUID(), "APPROVED", " ")));
  }

  @Test
  void approvingAPendingSubmissionGrantsXpOnceAndCommunityBadgeAtThree() {
    UUID submission = UUID.randomUUID();
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), eq(submission), eq(eventId))).thenReturn(List.of(user));
    when(jdbc.update(contains("UPDATE event_submission"), any(), any(), any(), any())).thenReturn(1);
    when(jdbc.queryForObject(contains("COUNT(DISTINCT event_id)"), eq(Integer.class), eq(user))).thenReturn(3);

    service.moderate(eventId, submission, "APPROVED", "Güzel");

    verify(gamification).grantXp(eq(user), eq(15), eq("EVENT_SUBMISSION"), anyString(), eq("event-submission:" + eventId + ":" + user), anyString());
    verify(gamification).awardBadge(user, "COMMUNITY_CHEF", "community-chef");
  }

  @Test
  void reModeratingAnAlreadyModeratedSubmissionGrantsNothing() {
    UUID submission = UUID.randomUUID();
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), eq(submission), eq(eventId))).thenReturn(List.of(user));
    when(jdbc.update(contains("UPDATE event_submission"), any(), any(), any(), any())).thenReturn(0);
    service.moderate(eventId, submission, "APPROVED", "Tekrar");
    verifyNoInteractions(gamification);
  }

  @Test
  void rejectingDoesNotGrantXp() {
    UUID submission = UUID.randomUUID();
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), eq(submission), eq(eventId))).thenReturn(List.of(user));
    when(jdbc.update(contains("UPDATE event_submission"), any(), any(), any(), any())).thenReturn(1);
    service.moderate(eventId, submission, "REJECTED", "Uygunsuz");
    verifyNoInteractions(gamification);
  }

  @Test
  void moderatingAMissingSubmissionIs404() {
    when(jdbc.queryForList(contains("FROM event_submission WHERE id=?"), eq(UUID.class), any(), eq(eventId))).thenReturn(List.of());
    assertEquals(HttpStatus.NOT_FOUND, status(() -> service.moderate(eventId, UUID.randomUUID(), "APPROVED", "ok")));
  }

  // ---- images ----
  @Test
  void publicImageOfAnUnapprovedSubmissionIs404() {
    when(jdbc.queryForList(contains("s.status='APPROVED'"), any(UUID.class), eq(eventId))).thenReturn(List.of());
    assertEquals(HttpStatus.NOT_FOUND, status(() -> service.image(eventId, UUID.randomUUID())));
  }
}
