package com.kitchenai.cookingjournal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingJournalRepository extends JpaRepository<CookingJournalEntry, UUID> {
  List<CookingJournalEntry> findTop100ByUserIdOrderByCookedAtDesc(UUID userId);
  List<CookingJournalEntry> findByUserIdAndRecipeIdOrderByCookedAtDesc(UUID userId, UUID recipeId);
  Optional<CookingJournalEntry> findByUserIdAndCookingSessionId(UUID userId, UUID cookingSessionId);
  Optional<CookingJournalEntry> findByIdAndUserId(UUID id, UUID userId);
}
