package com.kitchenai.cookingjournal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingJournalRepository extends JpaRepository<CookingJournalEntry, UUID> {
  Page<CookingJournalSummaryProjection> findByUserIdOrderByCookedAtDesc(UUID userId, Pageable pageable);
  List<UUID> findCookingSessionIdByUserId(UUID userId);
  List<CookingJournalEntry> findByUserIdAndRecipeIdOrderByCookedAtDesc(UUID userId, UUID recipeId);
  Optional<CookingJournalEntry> findByUserIdAndCookingSessionId(UUID userId, UUID cookingSessionId);
  Optional<CookingJournalEntry> findByIdAndUserId(UUID id, UUID userId);
}
