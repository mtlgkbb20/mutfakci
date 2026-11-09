package com.kitchenai.recovery;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRecoveryAttemptRepository extends JpaRepository<RecipeRecoveryAttempt, UUID> {
  List<RecipeRecoveryAttempt> findBySessionIdInOrderByCreatedAtDesc(List<UUID> sessionIds);
  List<RecipeRecoveryAttempt> findBySessionIdAndComponentKeyOrderByAttemptNumberAsc(UUID sessionId, String componentKey);
  Optional<RecipeRecoveryAttempt> findTopBySessionIdAndComponentKeyOrderByAttemptNumberDesc(UUID sessionId, String componentKey);
  long countBySessionIdAndCreatedAtAfter(UUID sessionId, java.time.Instant after);
}
