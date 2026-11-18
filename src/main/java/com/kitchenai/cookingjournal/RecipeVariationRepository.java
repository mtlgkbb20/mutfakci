package com.kitchenai.cookingjournal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeVariationRepository extends JpaRepository<RecipeVariation, UUID> {
  Page<RecipeVariationSummaryProjection> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
  List<RecipeVariation> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<RecipeVariation> findByIdAndUserId(UUID id, UUID userId);
}
