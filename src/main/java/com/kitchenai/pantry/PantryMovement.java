package com.kitchenai.pantry;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="pantry_movement")
public class PantryMovement {
  @Id private UUID id;
  @Column(name="user_id",nullable=false) private UUID userId;
  @Column(name="pantry_item_id",nullable=false) private UUID pantryItemId;
  @Column(name="movement_type",nullable=false,length=20) private String movementType;
  @Column(name="quantity_delta",nullable=false,precision=12,scale=3) private BigDecimal quantityDelta;
  @Column(nullable=false,length=20) private String unit;
  @Column(length=255) private String reason;
  @Column(name="operation_key",length=100) private String operationKey;
  @Column(name="created_at",nullable=false) private Instant createdAt;
  protected PantryMovement() {}
  public PantryMovement(UUID userId,UUID itemId,String type,BigDecimal delta,String unit,String reason,String operationKey) {
    this.id=UUID.randomUUID();this.userId=userId;this.pantryItemId=itemId;this.movementType=type;this.quantityDelta=delta;this.unit=unit;this.reason=reason;this.operationKey=operationKey;this.createdAt=Instant.now();
  }
  public UUID getId(){return id;} public UUID getUserId(){return userId;} public UUID getPantryItemId(){return pantryItemId;}
  public String getMovementType(){return movementType;} public BigDecimal getQuantityDelta(){return quantityDelta;}
  public String getUnit(){return unit;} public String getReason(){return reason;} public String getOperationKey(){return operationKey;} public Instant getCreatedAt(){return createdAt;}
}