package com.kitchenai.recovery;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<CookingObservation, UUID> {
  List<CookingObservation> findBySessionIdOrderByCreatedAtDesc(UUID id);
}
