package com.kitchenai.zerowaste;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface IngredientRemainderRepository extends JpaRepository<IngredientRemainder, UUID> {
  List<IngredientRemainder> findByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<IngredientRemainder> findByIdAndUserId(UUID id, UUID userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from IngredientRemainder r where r.id=:id and r.userId=:userId")
  Optional<IngredientRemainder> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
interface IngredientRemainderOperationRepository extends JpaRepository<IngredientRemainderOperation, UUID> {
  boolean existsByUserIdAndOperationKey(UUID userId, String operationKey);
  Optional<IngredientRemainderOperation> findByUserIdAndOperationKey(UUID userId, String operationKey);
}
