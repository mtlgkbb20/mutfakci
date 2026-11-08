package com.kitchenai.recovery;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<CookingFeedback, UUID> {
  List<CookingFeedback> findBySessionIdOrderByCreatedAtDesc(UUID id);
}
