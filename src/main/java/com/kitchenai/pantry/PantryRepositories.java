package com.kitchenai.pantry;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface PantryItemRepository extends JpaRepository<PantryItem, UUID> {
  List<PantryItem> findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(UUID userId);
  Optional<PantryItem> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PantryItem p where p.id=:id and p.userId=:userId and p.archived=false")
  Optional<PantryItem> lockOwned(@Param("id") UUID id,@Param("userId") UUID userId);
  /**
   * Locks every active item with this normalized name. Several can exist (manual creation does not
   * merge, and V21 re-normalized legacy names), so callers must not assume a single row.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PantryItem p where p.userId=:userId and p.normalizedName=:normalizedName and p.archived=false order by p.createdAt asc, p.id asc")
  List<PantryItem> lockAllByUserAndNormalizedName(@Param("userId") UUID userId,@Param("normalizedName") String normalizedName);
  /** Oldest active item with this normalized name, locked for update. */
  default Optional<PantryItem> lockFirstByUserAndNormalizedName(UUID userId, String normalizedName) {
    return lockAllByUserAndNormalizedName(userId, normalizedName).stream().findFirst();
  }
  List<PantryItem> findByUserIdAndArchivedFalseAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(UUID userId, java.time.LocalDate date);
  List<PantryItem> findByUserIdAndArchivedFalseAndExpiryDateIsNotNullAndExpiryDateBetweenOrderByExpiryDateAsc(UUID userId, java.time.LocalDate start, java.time.LocalDate end);
  Optional<PantryItem> findFirstByUserIdAndArchivedFalseAndNormalizedNameOrderByCreatedAtAsc(UUID userId,String normalizedName);
}
interface PantryMovementRepository extends JpaRepository<PantryMovement,UUID> {
  List<PantryMovement> findTop100ByUserIdAndPantryItemIdOrderByCreatedAtDesc(UUID userId,UUID itemId);
  List<PantryMovement> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<PantryMovement> findFirstByUserIdAndOperationKey(UUID userId, String operationKey);
}
interface PantryOperationRepository extends JpaRepository<PantryOperation,UUID> {
  boolean existsByUserIdAndOperationKey(UUID userId,String operationKey);
}
