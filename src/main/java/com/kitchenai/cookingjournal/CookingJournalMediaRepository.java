package com.kitchenai.cookingjournal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingJournalMediaRepository extends JpaRepository<CookingJournalMedia, UUID> {
  Optional<CookingJournalMedia> findByUserIdAndJournalEntryId(UUID userId, UUID journalEntryId);
  void deleteByUserIdAndJournalEntryId(UUID userId, UUID journalEntryId);
}
