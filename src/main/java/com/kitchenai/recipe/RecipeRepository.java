package com.kitchenai.recipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
  List<Recipe> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<Recipe> findByCatalogId(String catalogId);
}
