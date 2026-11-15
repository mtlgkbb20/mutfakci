package com.kitchenai.cooking;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CookingSessionRepository extends JpaRepository<CookingSession, UUID> {
  List<CookingSession> findTop10ByUserIdOrderByStartedAtDesc(UUID userId);
  List<CookingSession> findByUserIdOrderByStartedAtDesc(UUID userId);

  @Query("select s.id as id, s.recipeId as recipeId, s.status as status, "
      + "s.sessionType as sessionType, s.startedAt as startedAt, "
      + "s.desiredOutcome as desiredOutcome, r.title as recipeTitle "
      + "from CookingSession s left join Recipe r on r.id = s.recipeId "
      + "where s.userId = :userId order by s.startedAt desc")
  List<CookingSessionSummaryProjection> findSessionSummariesByUserId(@Param("userId") UUID userId);
}
