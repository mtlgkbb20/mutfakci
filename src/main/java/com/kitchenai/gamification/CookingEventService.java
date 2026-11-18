package com.kitchenai.gamification;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CookingEventService {
  private final JdbcTemplate jdbc;
  private final GamificationService gamification;
  public CookingEventService(JdbcTemplate jdbc, GamificationService gamification) {
    this.jdbc=jdbc; this.gamification=gamification;
  }

  @Transactional
  public UUID create(UUID admin, Map<String,Object> body) {
    Instant startsAt=Instant.parse(required(body,"startsAt"));
    Instant submissionOpensAt=Instant.parse(required(body,"submissionOpensAt"));
    Instant submissionClosesAt=Instant.parse(required(body,"submissionClosesAt"));
    Instant votingOpensAt=Instant.parse(required(body,"votingOpensAt"));
    Instant votingClosesAt=Instant.parse(required(body,"votingClosesAt"));
    if(startsAt.isAfter(submissionOpensAt) || submissionOpensAt.isAfter(submissionClosesAt)
        || submissionClosesAt.isAfter(votingOpensAt) || votingOpensAt.isAfter(votingClosesAt))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Event timeline must be start ≤ submission open ≤ submission close ≤ voting open ≤ voting close");
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO cooking_event(id,title,description,theme,status,starts_at,submission_opens_at,submission_closes_at,voting_opens_at,voting_closes_at,max_images,created_by) VALUES (?,?,?,?,'SCHEDULED',?,?,?,?,?,?,?)",
      id, required(body,"title"), required(body,"description"), required(body,"theme"),
      startsAt, submissionOpensAt, submissionClosesAt, votingOpensAt, votingClosesAt,
      number(body.getOrDefault("maxImages",4)), admin);
    return id;
  }

  public List<Map<String,Object>> list() {
    return jdbc.queryForList("SELECT id,title,description,theme,status,starts_at AS startsAt,submission_opens_at AS submissionOpensAt,submission_closes_at AS submissionClosesAt,voting_opens_at AS votingOpensAt,voting_closes_at AS votingClosesAt,max_images AS maxImages FROM cooking_event WHERE status <> 'DRAFT' AND status <> 'CANCELLED' ORDER BY starts_at DESC");
  }

  public Map<String,Object> detail(UUID eventId, UUID userId) {
    var rows=jdbc.queryForList("SELECT id,title,description,theme,status,starts_at AS startsAt,submission_opens_at AS submissionOpensAt,submission_closes_at AS submissionClosesAt,voting_opens_at AS votingOpensAt,voting_closes_at AS votingClosesAt,max_images AS maxImages FROM cooking_event WHERE id=?",eventId);
    if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    Map<String,Object> result=new LinkedHashMap<>(rows.get(0));
    result.put("joined", jdbc.queryForObject("SELECT COUNT(*)>0 FROM event_participation WHERE event_id=? AND user_id=?",Boolean.class,eventId,userId));
    result.put("submission", jdbc.queryForList("SELECT id,caption,status,submitted_at AS submittedAt FROM event_submission WHERE event_id=? AND user_id=?",eventId,userId));
    result.put("canVote", jdbc.queryForObject("SELECT COUNT(*)>0 FROM event_voter_snapshot WHERE event_id=? AND user_id=?",Boolean.class,eventId,userId));
    return result;
  }

  @Transactional
  public void join(UUID eventId, UUID userId) {
    var event=eventForUpdate(eventId);
    Instant now=Instant.now();
    if(!"SCHEDULED".equals(event.get("status")) || now.isBefore(instant(event.get("submission_opens_at"))) || !now.isBefore(instant(event.get("submission_closes_at"))))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Participation window is closed");
    jdbc.update("INSERT INTO event_participation(event_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",eventId,userId);
  }

  @Transactional
  public UUID submit(UUID eventId, UUID userId, String caption, List<String> contentTypes, List<byte[]> images) {
    var event=eventForUpdate(eventId);
    Instant now=Instant.now();
    if(!"SCHEDULED".equals(event.get("status")) || now.isBefore(instant(event.get("submission_opens_at"))) || !now.isBefore(instant(event.get("submission_closes_at"))))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Submission window is closed");
    int max=((Number)event.get("max_images")).intValue();
    if(images.isEmpty() || images.size()>max) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid image count");
    UUID submission=UUID.randomUUID();
    jdbc.update("INSERT INTO event_participation(event_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",eventId,userId);
    jdbc.update("INSERT INTO event_submission(id,event_id,user_id,caption,status) VALUES (?,?,?,?,'PENDING')",submission,eventId,userId,caption);
    for(int i=0;i<images.size();i++) jdbc.update("INSERT INTO event_submission_image(id,submission_id,content_type,image_data) VALUES (?,?,?,?)",UUID.randomUUID(),submission,contentTypes.get(i),images.get(i));
    return submission;
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void advanceScheduledEvents() {
    Instant now=Instant.now();
    List<UUID> open = jdbc.query(
      "SELECT id FROM cooking_event WHERE status='SCHEDULED' AND voting_opens_at <= ? AND voting_closes_at > ?",
      (rs,n)->rs.getObject(1,UUID.class), now, now);
    for(UUID id:open) openVoting(id);
    List<UUID> close = jdbc.query(
      "SELECT id FROM cooking_event WHERE status='VOTING' AND voting_closes_at <= ?",
      (rs,n)->rs.getObject(1,UUID.class), now);
    for(UUID id:close) finalizeEvent(id);
  }

  @Transactional
  public void openVoting(UUID eventId) {
    var event=eventForUpdate(eventId);
    if ("VOTING".equals(event.get("status"))) return;
    if (!"SCHEDULED".equals(event.get("status"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Event is not ready for voting");
    Instant now=Instant.now();
    if(now.isBefore(instant(event.get("voting_opens_at")))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Voting has not opened");
    LocalDate weekStart=LocalDate.now(GamificationService.ZONE).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    List<Map<String,Object>> top=jdbc.queryForList(
      "WITH w AS (SELECT user_id,SUM(xp_delta)::int xp FROM xp_ledger_entry WHERE created_at >= ? AND created_at < ? GROUP BY user_id HAVING SUM(xp_delta)>0) SELECT w.user_id,u.created_at FROM w JOIN app_user u ON u.id=w.user_id ORDER BY w.xp DESC,u.created_at,u.id LIMIT 5",
      weekStart.atStartOfDay(GamificationService.ZONE).toInstant(),weekStart.plusWeeks(1).atStartOfDay(GamificationService.ZONE).toInstant());
    int rank=0;
    for(var row:top) jdbc.update("INSERT INTO event_voter_snapshot(event_id,user_id,rank_position,source_week_start) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",eventId,row.get("user_id"),++rank,weekStart);
    jdbc.update("UPDATE cooking_event SET status='VOTING',updated_at=now() WHERE id=?",eventId);
  }

  @Transactional
  public void vote(UUID eventId, UUID voter, UUID submission, int score) {
    if(score<1 || score>10) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Score must be 1..10");
    var event=eventForUpdate(eventId);
    Instant now=Instant.now();
    if(!"VOTING".equals(event.get("status")) || now.isBefore(instant(event.get("voting_opens_at"))) || !now.isBefore(instant(event.get("voting_closes_at"))))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Voting is closed");
    Boolean eligible=jdbc.queryForObject("SELECT COUNT(*)>0 FROM event_voter_snapshot WHERE event_id=? AND user_id=?",Boolean.class,eventId,voter);
    UUID owner=jdbc.queryForObject("SELECT user_id FROM event_submission WHERE id=? AND event_id=? AND status='APPROVED'",UUID.class,submission,eventId);
    if(!Boolean.TRUE.equals(eligible)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"You are not an event voter");
    if(owner.equals(voter)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Self-voting is not allowed");
    jdbc.update("INSERT INTO event_vote(id,event_id,submission_id,voter_user_id,score) VALUES (?,?,?,?,?) ON CONFLICT(submission_id,voter_user_id) DO UPDATE SET score=EXCLUDED.score,updated_at=now()",
      UUID.randomUUID(),eventId,submission,voter,score);
  }

  @Transactional
  public Map<String,Object> finalizeEvent(UUID eventId) {
    var event=eventForUpdate(eventId);
    if ("COMPLETED".equals(event.get("status"))) {
      Integer scored=jdbc.queryForObject("SELECT COUNT(*)::int FROM event_result WHERE event_id=?",Integer.class,eventId);
      return Map.of("status","ALREADY_COMPLETED","scoredSubmissions",scored==null?0:scored);
    }
    if (!"VOTING".equals(event.get("status"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Event is not in voting state");
    Instant now=Instant.now();
    if(now.isBefore(instant(event.get("voting_closes_at")))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Voting is not closed");
    var rows=jdbc.queryForList("SELECT s.id,s.user_id,AVG(v.score)::numeric(5,3) avg_score,COUNT(v.id)::int vote_count,MIN(s.submitted_at) submitted_at FROM event_submission s JOIN event_vote v ON v.submission_id=s.id WHERE s.event_id=? AND s.status='APPROVED' GROUP BY s.id,s.user_id HAVING COUNT(v.id)>0 ORDER BY AVG(v.score) DESC,COUNT(v.id) DESC,MIN(s.submitted_at),s.id",eventId);
    int rank=0;
    for(var row:rows) {
      UUID submission=(UUID)row.get("id"); UUID owner=(UUID)row.get("user_id");
      BigDecimalValue score=new BigDecimalValue((java.math.BigDecimal) row.get("avg_score"));
      int votes=((Number)row.get("vote_count")).intValue();
      jdbc.update("INSERT INTO event_result(submission_id,event_id,average_score,vote_count,rank_position) VALUES (?,?,?,?,?) ON CONFLICT(submission_id) DO NOTHING",submission,eventId,score.value,votes,++rank);
      if(rank<=3) {
        int xp=rank==1?30:rank==2?20:10;
        gamification.grantXp(owner,xp,"EVENT_PLACEMENT",eventId.toString(),"event-placement:"+eventId+":"+owner,"Etkinlik sıralaması "+rank+".");
        if(rank==1) gamification.awardBadge(owner,"PEOPLES_CHOICE","event:"+eventId);
      }
    }
    jdbc.update("UPDATE cooking_event SET status='COMPLETED',updated_at=now() WHERE id=?",eventId);
    return Map.of("status","COMPLETED","scoredSubmissions",rows.size());
  }

  @Transactional
  public void moderate(UUID eventId, UUID submissionId, String status, String reason) {
    if (!List.of("APPROVED","REJECTED","HIDDEN").contains(status) || reason == null || reason.isBlank())
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"A valid moderation status and reason are required");
    UUID userId=jdbc.queryForObject("SELECT user_id FROM event_submission WHERE id=? AND event_id=?",UUID.class,submissionId,eventId);
    int changed=jdbc.update("UPDATE event_submission SET status=?,moderated_at=now(),moderation_reason=? WHERE id=? AND event_id=? AND status='PENDING'",
        status,reason,submissionId,eventId);
    if(changed==1 && "APPROVED".equals(status)) {
      gamification.grantXp(userId,15,"EVENT_SUBMISSION",eventId.toString(),"event-submission:"+eventId+":"+userId,"Geçerli etkinlik katılımı");
      Integer completedEvents=jdbc.queryForObject("SELECT COUNT(DISTINCT event_id)::int FROM event_submission WHERE user_id=? AND status='APPROVED'",Integer.class,userId);
      if(completedEvents != null && completedEvents >= 3) gamification.awardBadge(userId,"COMMUNITY_CHEF","community-chef");
    }
  }

  public List<Map<String,Object>> submissions(UUID eventId) {
    return jdbc.queryForList("SELECT s.id,s.user_id AS userId,u.display_name AS displayName,s.caption,s.status,s.submitted_at AS submittedAt,(SELECT array_agg(i.id) FROM event_submission_image i WHERE i.submission_id=s.id) AS imageIds FROM event_submission s JOIN app_user u ON u.id=s.user_id WHERE s.event_id=? AND s.status='APPROVED' ORDER BY s.submitted_at",eventId);
  }

  public List<Map<String,Object>> adminSubmissions(UUID eventId) {
    return jdbc.queryForList("SELECT s.id,s.user_id AS userId,u.display_name AS displayName,s.caption,s.status,s.submitted_at AS submittedAt,(SELECT array_agg(i.id::text) FROM event_submission_image i WHERE i.submission_id=s.id) AS imageIds FROM event_submission s JOIN app_user u ON u.id=s.user_id WHERE s.event_id=? ORDER BY s.submitted_at",eventId);
  }

  public Map<String,Object> adminImage(UUID eventId, UUID imageId) {
    var rows=jdbc.queryForList("SELECT i.content_type,i.image_data FROM event_submission_image i JOIN event_submission s ON s.id=i.submission_id WHERE i.id=? AND s.event_id=?",imageId,eventId);
    if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    return rows.get(0);
  }

  public Map<String,Object> image(UUID eventId, UUID imageId) {
    var rows=jdbc.queryForList("SELECT i.content_type,i.image_data FROM event_submission_image i JOIN event_submission s ON s.id=i.submission_id WHERE i.id=? AND s.event_id=? AND s.status='APPROVED'",imageId,eventId);
    if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    return rows.get(0);
  }

  public List<Map<String,Object>> results(UUID eventId) {
    return jdbc.queryForList("SELECT r.rank_position AS rank,s.id AS submissionId,u.display_name AS displayName,r.average_score AS averageScore,r.vote_count AS voteCount,s.caption FROM event_result r JOIN event_submission s ON s.id=r.submission_id JOIN app_user u ON u.id=s.user_id WHERE r.event_id=? ORDER BY r.rank_position",eventId);
  }

  private Instant instant(Object value) {
    if(value instanceof Instant i) return i;
    if(value instanceof java.sql.Timestamp t) return t.toInstant();
    throw new IllegalStateException("Unexpected timestamp value");
  }

  private Map<String,Object> eventForUpdate(UUID id) {
    var rows=jdbc.queryForList("SELECT * FROM cooking_event WHERE id=? FOR UPDATE",id);
    if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    return rows.get(0);
  }

  private Map<String,Object> event(UUID id) {
    var rows=jdbc.queryForList("SELECT * FROM cooking_event WHERE id=?",id);
    if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    return rows.get(0);
  }
  private static String required(Map<String,Object> b,String key) {
    Object value=b.get(key); if(value==null || value.toString().isBlank()) throw new IllegalArgumentException(key+" is required"); return value.toString();
  }
  private static int number(Object value) { return Integer.parseInt(value.toString()); }
  private record BigDecimalValue(java.math.BigDecimal value) {}
}
