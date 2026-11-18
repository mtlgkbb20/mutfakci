package com.kitchenai.cookingjournal;

import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookingJournalMediaRepository extends JpaRepository<CookingJournalMedia, UUID> {
  Optional<CookingJournalMedia> findByUserIdAndJournalEntryId(UUID userId, UUID journalEntryId);
  void deleteByUserIdAndJournalEntryId(UUID userId, UUID journalEntryId);

  @Query("select media.journalEntryId from CookingJournalMedia media "
      + "where media.userId = :userId and media.journalEntryId in :entryIds")
  Set<UUID> findJournalEntryIdsWithPhotos(@Param("userId") UUID userId,
      @Param("entryIds") Collection<UUID> entryIds);
}
