package com.kitchenai.recipe;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeGenerationRepository extends JpaRepository<RecipeGeneration, UUID> {}
