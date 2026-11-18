package com.kitchenai.recovery;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRecoveryAttemptRepository extends JpaRepository<RecipeRecoveryAttempt, UUID> {
  List<RecipeRecoveryAttempt> findBySessionIdInOrderByCreatedAtDesc(List<UUID> sessionIds);
  Page<RecipeRecoveryAttempt> findBySessionIdInOrderByCreatedAtDesc(List<UUID> sessionIds, Pageable pageable);
  Page<RecipeRecoveryAttempt> findBySessionIdInAndCreatedAtBetweenOrderByCreatedAtDesc(
      List<UUID> sessionIds, Instant from, Instant to, Pageable pageable);
  List<RecipeRecoveryAttempt> findBySessionIdAndComponentKeyOrderByAttemptNumberAsc(UUID sessionId, String componentKey);
  Optional<RecipeRecoveryAttempt> findTopBySessionIdAndComponentKeyOrderByAttemptNumberDesc(UUID sessionId, String componentKey);
  long countBySessionIdAndCreatedAtAfter(UUID sessionId, java.time.Instant after);
}
