package com.kitchenai.recipe;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
  List<Recipe> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);

  Page<Recipe> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<Recipe> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
      UUID userId, Instant from, Instant to, Pageable pageable);

  Optional<Recipe> findByCatalogId(String catalogId);

  List<RecipeTitleProjection> findByIdIn(Collection<UUID> ids);
}
