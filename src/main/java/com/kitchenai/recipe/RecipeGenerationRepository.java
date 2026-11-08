package com.kitchenai.recipe;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeGenerationRepository extends JpaRepository<RecipeGeneration, UUID> {
  Optional<RecipeGeneration> findTop1ByUserIdAndNormalizedDesiredFoodAndStatusOrderByCreatedAtDesc(
      UUID userId, String normalizedDesiredFood, String status);
}
