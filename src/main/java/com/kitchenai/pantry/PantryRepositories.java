package com.kitchenai.pantry;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface PantryItemRepository extends JpaRepository<PantryItem, UUID> {
  List<PantryItem> findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(UUID userId);
  Optional<PantryItem> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PantryItem p where p.id=:id and p.userId=:userId and p.archived=false")
  Optional<PantryItem> lockOwned(@Param("id") UUID id,@Param("userId") UUID userId);
  List<PantryItem> findByUserIdAndArchivedFalseAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(UUID userId, java.time.LocalDate date);
  List<PantryItem> findByUserIdAndArchivedFalseAndExpiryDateIsNotNullAndExpiryDateBetweenOrderByExpiryDateAsc(UUID userId, java.time.LocalDate start, java.time.LocalDate end);
  Optional<PantryItem> findFirstByUserIdAndArchivedFalseAndNormalizedName(UUID userId,String normalizedName);
}
interface PantryMovementRepository extends JpaRepository<PantryMovement,UUID> {
  List<PantryMovement> findTop100ByUserIdAndPantryItemIdOrderByCreatedAtDesc(UUID userId,UUID itemId);
  List<PantryMovement> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<PantryMovement> findByUserIdAndOperationKey(UUID userId,String operationKey);
}