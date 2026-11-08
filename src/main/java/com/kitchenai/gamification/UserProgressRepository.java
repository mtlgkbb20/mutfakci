package com.kitchenai.gamification;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
  Optional<UserProgress> findByUserId(UUID userId);
}
