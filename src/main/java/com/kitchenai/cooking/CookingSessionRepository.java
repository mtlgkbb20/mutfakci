package com.kitchenai.cooking;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingSessionRepository extends JpaRepository<CookingSession, UUID> {
  List<CookingSession> findTop10ByUserIdOrderByStartedAtDesc(UUID userId);
  List<CookingSession> findByUserIdOrderByStartedAtDesc(UUID userId);
}
