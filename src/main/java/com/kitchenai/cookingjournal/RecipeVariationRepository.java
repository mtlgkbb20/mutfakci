package com.kitchenai.cookingjournal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeVariationRepository extends JpaRepository<RecipeVariation, UUID> {
  List<RecipeVariation> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<RecipeVariation> findByIdAndUserId(UUID id, UUID userId);
}
