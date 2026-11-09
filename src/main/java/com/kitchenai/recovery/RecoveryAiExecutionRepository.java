package com.kitchenai.recovery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryAiExecutionRepository extends JpaRepository<RecoveryAiExecution, UUID> {
  List<RecoveryAiExecution> findByAttemptIdOrderByCreatedAtAsc(UUID attemptId);
}
