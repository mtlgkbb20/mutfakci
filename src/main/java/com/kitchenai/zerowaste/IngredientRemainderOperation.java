package com.kitchenai.zerowaste;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingredient_remainder_operation",
    uniqueConstraints = @UniqueConstraint(name = "uq_remainder_operation_user_key",
        columnNames = {"user_id", "operation_key"}))
public class IngredientRemainderOperation {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "remainder_id", nullable = false) private UUID remainderId;
  @Column(name = "operation_key", nullable = false, length = 100) private String operationKey;
  @Column(name = "operation_type", nullable = false, length = 20) private String operationType;
  @Column(name = "quantity_before", precision = 12, scale = 3) private BigDecimal quantityBefore;
  @Column(name = "quantity_after", precision = 12, scale = 3) private BigDecimal quantityAfter;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected IngredientRemainderOperation() {}
  public IngredientRemainderOperation(UUID userId, UUID remainderId, String key, String type,
      BigDecimal before, BigDecimal after) {
    this.id = UUID.randomUUID(); this.userId = userId; this.remainderId = remainderId;
    this.operationKey = key; this.operationType = type; this.quantityBefore = before;
    this.quantityAfter = after; this.createdAt = Instant.now();
  }
  public UUID getRemainderId(){ return remainderId; }
}
