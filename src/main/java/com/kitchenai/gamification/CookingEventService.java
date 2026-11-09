package com.kitchenai.gamification;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CookingEventService {

  private final JdbcTemplate jdbc;
  private final GamificationService gamification;

  public CookingEventService(
      JdbcTemplate jdbc, GamificationService gamification) {
    this.jdbc = jdbc;
    this.gamification = gamification;
  }

  @Transactional
  public UUID create(UUID adminId, Map<String, Object> body) {
    Instant startsAt = Instant.parse(required(body, "startsAt"));
    Instant submissionOpensAt = Instant.parse(required(body, "submissionOpensAt"));
    Instant submissionClosesAt = Instant.parse(required(body, "submissionClosesAt"));
    Instant votingOpensAt = Instant.parse(required(body, "votingOpensAt"));
    Instant votingClosesAt = Instant.parse(required(body, "votingClosesAt"));

    boolean invalidTimeline =
        startsAt.isAfter(submissionOpensAt)
            || submissionOpensAt.isAfter(submissionClosesAt)
            || submissionClosesAt.isAfter(votingOpensAt)
            || votingOpensAt.isAfter(votingClosesAt);

    if (invalidTimeline) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Event timeline must be start ≤ submission open ≤ submission close ≤ voting open ≤ voting close");
    }

    UUID eventId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO cooking_event (
          id, title, description, theme, status, starts_at,
          submission_opens_at, submission_closes_at,
          voting_opens_at, voting_closes_at, max_images, created_by
        )
        VALUES (?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, ?, ?, ?, ?)
        """,
        eventId,
        required(body, "title"),
        required(body, "description"),
        required(body, "theme"),
        startsAt,
        submissionOpensAt,
        submissionClosesAt,
        votingOpensAt,
        votingClosesAt,
        number(body.getOrDefault("maxImages", 4)),
        adminId);

    return eventId;
  }

  public List<Map<String, Object>> list() {
    return jdbc.queryForList(
        """
        SELECT id, title, description, theme, status,
               starts_at AS startsAt,
               submission_opens_at AS submissionOpensAt,
               submission_closes_at AS submissionClosesAt,
               voting_opens_at AS votingOpensAt,
               voting_closes_at AS votingClosesAt,
               max_images AS maxImages
        FROM cooking_event
        WHERE status NOT IN ('DRAFT', 'CANCELLED')
        ORDER BY starts_at DESC
        """);
  }

  public Map<String, Object> detail(UUID eventId, UUID userId) {
    var rows = jdbc.queryForList(
        """
        SELECT id, title, description, theme, status,
               starts_at AS startsAt,
               submission_opens_at AS submissionOpensAt,
               submission_closes_at AS submissionClosesAt,
               voting_opens_at AS votingOpensAt,
               voting_closes_at AS votingClosesAt,
               max_images AS maxImages
        FROM cooking_event
        WHERE id = ?
        """,
        eventId);

    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
    result.put(
        "joined",
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM event_participation WHERE event_id = ? AND user_id = ?",
            Boolean.class,
            eventId,
            userId));
    result.put(
        "submission",
        jdbc.queryForList(
            """
            SELECT id, caption, status, submitted_at AS submittedAt
            FROM event_submission
            WHERE event_id = ? AND user_id = ?
            """,
            eventId,
            userId));
    result.put(
        "canVote",
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM event_voter_snapshot WHERE event_id = ? AND user_id = ?",
            Boolean.class,
            eventId,
            userId));

    return result;
  }

  @Transactional
  public void join(UUID eventId, UUID userId) {
    Map<String, Object> event = eventForUpdate(eventId);
    Instant now = Instant.now();

    boolean closed =
        !"SCHEDULED".equals(event.get("status"))
            || now.isBefore(instant(event.get("submission_opens_at")))
            || !now.isBefore(instant(event.get("submission_closes_at")));

    if (closed) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Participation window is closed");
    }

    jdbc.update(
        "INSERT INTO event_participation(event_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        eventId,
        userId);
  }

  @Transactional
  public UUID submit(
      UUID eventId,
      UUID userId,
      String caption,
      List<String> contentTypes,
      List<byte[]> images) {

    Map<String, Object> event = eventForUpdate(eventId);
    Instant now = Instant.now();

    boolean closed =
        !"SCHEDULED".equals(event.get("status"))
            || now.isBefore(instant(event.get("submission_opens_at")))
            || !now.isBefore(instant(event.get("submission_closes_at")));

    if (closed) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Submission window is closed");
    }

    int maxImages = ((Number) event.get("max_images")).intValue();
    if (images.isEmpty() || images.size() > maxImages) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid image count");
    }

    UUID submissionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_participation(event_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        eventId,
        userId);
    jdbc.update(
        """
        INSERT INTO event_submission(id, event_id, user_id, caption, status)
        VALUES (?, ?, ?, ?, 'PENDING')
        """,
        submissionId,
        eventId,
        userId,
        caption);

    for (int index = 0; index < images.size(); index++) {
      jdbc.update(
          """
          INSERT INTO event_submission_image(id, submission_id, content_type, image_data)
          VALUES (?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          submissionId,
          contentTypes.get(index),
          images.get(index));
    }

    return submissionId;
  }

  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void advanceScheduledEvents() {
    Instant now = Instant.now();

    List<UUID> eventsToOpen = jdbc.query(
        """
        SELECT id
        FROM cooking_event
        WHERE status = 'SCHEDULED'
          AND voting_opens_at <= ?
          AND voting_closes_at > ?
        """,
        (rs, row) -> rs.getObject(1, UUID.class),
        now,
        now);

    for (UUID eventId : eventsToOpen) {
      openVoting(eventId);
    }

    List<UUID> eventsToClose = jdbc.query(
        """
        SELECT id
        FROM cooking_event
        WHERE status = 'VOTING'
          AND voting_closes_at <= ?
        """,
        (rs, row) -> rs.getObject(1, UUID.class),
        now);

    for (UUID eventId : eventsToClose) {
      finalizeEvent(eventId);
    }
  }

  @Transactional
  public void openVoting(UUID eventId) {
    Map<String, Object> event = eventForUpdate(eventId);

    if ("VOTING".equals(event.get("status"))) {
      return;
    }
    if (!"SCHEDULED".equals(event.get("status"))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Event is not ready for voting");
    }

    Instant now = Instant.now();
    if (now.isBefore(instant(event.get("voting_opens_at")))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Voting has not opened");
    }

    LocalDate weekStart =
        LocalDate.now(GamificationService.ZONE)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    List<Map<String, Object>> topVoters = jdbc.queryForList(
        """
        WITH weekly_xp AS (
          SELECT user_id, SUM(xp_delta)::int AS xp
          FROM xp_ledger_entry
          WHERE created_at >= ? AND created_at < ?
          GROUP BY user_id
          HAVING SUM(xp_delta) > 0
        )
        SELECT weekly_xp.user_id, app_user.created_at
        FROM weekly_xp
        JOIN app_user ON app_user.id = weekly_xp.user_id
        ORDER BY weekly_xp.xp DESC, app_user.created_at, app_user.id
        LIMIT 5
        """,
        weekStart.atStartOfDay(GamificationService.ZONE).toInstant(),
        weekStart.plusWeeks(1).atStartOfDay(GamificationService.ZONE).toInstant());

    int rank = 0;
    for (Map<String, Object> voter : topVoters) {
      jdbc.update(
          """
          INSERT INTO event_voter_snapshot(event_id, user_id, rank_position, source_week_start)
          VALUES (?, ?, ?, ?)
          ON CONFLICT DO NOTHING
          """,
          eventId,
          voter.get("user_id"),
          ++rank,
          weekStart);
    }

    jdbc.update(
        "UPDATE cooking_event SET status = 'VOTING', updated_at = now() WHERE id = ?",
        eventId);
  }

  @Transactional
  public void vote(UUID eventId, UUID voterId, UUID submissionId, int score) {
    if (score < 1 || score > 10) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Score must be 1..10");
    }

    Map<String, Object> event = eventForUpdate(eventId);
    Instant now = Instant.now();
    boolean closed =
        !"VOTING".equals(event.get("status"))
            || now.isBefore(instant(event.get("voting_opens_at")))
            || !now.isBefore(instant(event.get("voting_closes_at")));

    if (closed) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting is closed");
    }

    Boolean eligible = jdbc.queryForObject(
        """
        SELECT COUNT(*) > 0
        FROM event_voter_snapshot
        WHERE event_id = ? AND user_id = ?
        """,
        Boolean.class,
        eventId,
        voterId);
    UUID ownerId = jdbc.queryForObject(
        """
        SELECT user_id
        FROM event_submission
        WHERE id = ? AND event_id = ? AND status = 'APPROVED'
        """,
        UUID.class,
        submissionId,
        eventId);

    if (!Boolean.TRUE.equals(eligible)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You are not an event voter");
    }
    if (ownerId.equals(voterId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Self-voting is not allowed");
    }

    jdbc.update(
        """
        INSERT INTO event_vote(id, event_id, submission_id, voter_user_id, score)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(submission_id, voter_user_id)
        DO UPDATE SET score = EXCLUDED.score, updated_at = now()
        """,
        UUID.randomUUID(),
        eventId,
        submissionId,
        voterId,
        score);
  }

  @Transactional
  public Map<String, Object> finalizeEvent(UUID eventId) {
    Map<String, Object> event = eventForUpdate(eventId);

    if ("COMPLETED".equals(event.get("status"))) {
      Integer count = jdbc.queryForObject(
          "SELECT COUNT(*)::int FROM event_result WHERE event_id = ?",
          Integer.class,
          eventId);
      return Map.of(
          "status", "ALREADY_COMPLETED",
          "scoredSubmissions", count == null ? 0 : count);
    }

    if (!"VOTING".equals(event.get("status"))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Event is not in voting state");
    }

    Instant now = Instant.now();
    if (now.isBefore(instant(event.get("voting_closes_at")))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Voting is not closed");
    }

    var results = jdbc.queryForList(
        """
        SELECT
          submission.id,
          submission.user_id,
          AVG(vote.score)::numeric(5, 3) AS avg_score,
          COUNT(vote.id)::int AS vote_count,
          MIN(submission.submitted_at) AS submitted_at
        FROM event_submission submission
        JOIN event_vote vote ON vote.submission_id = submission.id
        WHERE submission.event_id = ?
          AND submission.status = 'APPROVED'
        GROUP BY submission.id, submission.user_id
        HAVING COUNT(vote.id) > 0
        ORDER BY
          AVG(vote.score) DESC,
          COUNT(vote.id) DESC,
          MIN(submission.submitted_at),
          submission.id
        """,
        eventId);

    int rank = 0;
    for (Map<String, Object> result : results) {
      UUID submissionId = (UUID) result.get("id");
      UUID ownerId = (UUID) result.get("user_id");
      BigDecimalValue averageScore =
          new BigDecimalValue((java.math.BigDecimal) result.get("avg_score"));
      int voteCount = ((Number) result.get("vote_count")).intValue();

      jdbc.update(
          """
          INSERT INTO event_result(
            submission_id, event_id, average_score, vote_count, rank_position
          )
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT(submission_id) DO NOTHING
          """,
          submissionId,
          eventId,
          averageScore.value(),
          voteCount,
          ++rank);

      if (rank <= 3) {
        int xp = rank == 1 ? 30 : rank == 2 ? 20 : 10;
        gamification.grantXp(
            ownerId,
            xp,
            "EVENT_PLACEMENT",
            eventId.toString(),
            "event-placement:" + eventId + ":" + ownerId,
            "Etkinlik sıralaması " + rank + ".");

        if (rank == 1) {
          gamification.awardBadge(
              ownerId, "PEOPLES_CHOICE", "event:" + eventId);
        }
      }
    }

    jdbc.update(
        "UPDATE cooking_event SET status = 'COMPLETED', updated_at = now() WHERE id = ?",
        eventId);

    return Map.of("status", "COMPLETED", "scoredSubmissions", results.size());
  }

  @Transactional
  public void moderate(
      UUID eventId, UUID submissionId, String status, String reason) {
    if (!List.of("APPROVED", "REJECTED", "HIDDEN").contains(status)
        || reason == null
        || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A valid moderation status and reason are required");
    }

    UUID userId = jdbc.queryForObject(
        "SELECT user_id FROM event_submission WHERE id = ? AND event_id = ?",
        UUID.class,
        submissionId,
        eventId);
    int changed = jdbc.update(
        """
        UPDATE event_submission
        SET status = ?, moderated_at = now(), moderation_reason = ?
        WHERE id = ? AND event_id = ? AND status = 'PENDING'
        """,
        status,
        reason,
        submissionId,
        eventId);

    if (changed == 1 && "APPROVED".equals(status)) {
      gamification.grantXp(
          userId,
          15,
          "EVENT_SUBMISSION",
          eventId.toString(),
          "event-submission:" + eventId + ":" + userId,
          "Geçerli etkinlik katılımı");

      Integer completedEvents = jdbc.queryForObject(
          """
          SELECT COUNT(DISTINCT event_id)::int
          FROM event_submission
          WHERE user_id = ? AND status = 'APPROVED'
          """,
          Integer.class,
          userId);

      if (completedEvents != null && completedEvents >= 3) {
        gamification.awardBadge(
            userId, "COMMUNITY_CHEF", "community-chef");
      }
    }
  }

  public List<Map<String, Object>> submissions(UUID eventId) {
    return jdbc.queryForList(
        """
        SELECT
          submission.id,
          submission.user_id AS userId,
          app_user.display_name AS displayName,
          submission.caption,
          submission.status,
          submission.submitted_at AS submittedAt,
          (
            SELECT array_agg(image.id)
            FROM event_submission_image image
            WHERE image.submission_id = submission.id
          ) AS imageIds
        FROM event_submission submission
        JOIN app_user ON app_user.id = submission.user_id
        WHERE submission.event_id = ?
          AND submission.status = 'APPROVED'
        ORDER BY submission.submitted_at
        """,
        eventId);
  }

  public List<Map<String, Object>> adminSubmissions(UUID eventId) {
    return jdbc.queryForList(
        """
        SELECT
          submission.id,
          submission.user_id AS userId,
          app_user.display_name AS displayName,
          submission.caption,
          submission.status,
          submission.submitted_at AS submittedAt,
          (
            SELECT array_agg(image.id::text)
            FROM event_submission_image image
            WHERE image.submission_id = submission.id
          ) AS imageIds
        FROM event_submission submission
        JOIN app_user ON app_user.id = submission.user_id
        WHERE submission.event_id = ?
        ORDER BY submission.submitted_at
        """,
        eventId);
  }

  public Map<String, Object> adminImage(UUID eventId, UUID imageId) {
    var rows = jdbc.queryForList(
        """
        SELECT image.content_type, image.image_data
        FROM event_submission_image image
        JOIN event_submission submission
          ON submission.id = image.submission_id
        WHERE image.id = ? AND submission.event_id = ?
        """,
        imageId,
        eventId);

    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    return rows.get(0);
  }

  public Map<String, Object> image(UUID eventId, UUID imageId) {
    var rows = jdbc.queryForList(
        """
        SELECT image.content_type, image.image_data
        FROM event_submission_image image
        JOIN event_submission submission
          ON submission.id = image.submission_id
        WHERE image.id = ?
          AND submission.event_id = ?
          AND submission.status = 'APPROVED'
        """,
        imageId,
        eventId);

    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    return rows.get(0);
  }

  public List<Map<String, Object>> results(UUID eventId) {
    return jdbc.queryForList(
        """
        SELECT
          result.rank_position AS rank,
          submission.id AS submissionId,
          app_user.display_name AS displayName,
          result.average_score AS averageScore,
          result.vote_count AS voteCount,
          submission.caption
        FROM event_result result
        JOIN event_submission submission
          ON submission.id = result.submission_id
        JOIN app_user ON app_user.id = submission.user_id
        WHERE result.event_id = ?
        ORDER BY result.rank_position
        """,
        eventId);
  }

  private Instant instant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toInstant();
    }

    throw new IllegalStateException("Unexpected timestamp value");
  }

  private Map<String, Object> eventForUpdate(UUID eventId) {
    var rows = jdbc.queryForList(
        "SELECT * FROM cooking_event WHERE id = ? FOR UPDATE", eventId);

    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    return rows.get(0);
  }

  private Map<String, Object> event(UUID eventId) {
    var rows = jdbc.queryForList(
        "SELECT * FROM cooking_event WHERE id = ?", eventId);

    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    return rows.get(0);
  }

  private static String required(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || value.toString().isBlank()) {
      throw new IllegalArgumentException(key + " is required");
    }

    return value.toString();
  }

  private static int number(Object value) {
    return Integer.parseInt(value.toString());
  }

  private record BigDecimalValue(java.math.BigDecimal value) {}
}
