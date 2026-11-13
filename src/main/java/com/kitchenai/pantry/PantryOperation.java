package com.kitchenai.pantry;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="pantry_operation", uniqueConstraints=@UniqueConstraint(name="uq_pantry_operation_user_key",columnNames={"user_id","operation_key"}))
public class PantryOperation {
  @Id private UUID id;
  @Column(name="user_id",nullable=false) private UUID userId;
  @Column(name="operation_key",nullable=false,length=100) private String operationKey;
  @Column(name="created_at",nullable=false) private Instant createdAt;
  protected PantryOperation() {}
  public PantryOperation(UUID userId,String operationKey){this.id=UUID.randomUUID();this.userId=userId;this.operationKey=operationKey;this.createdAt=Instant.now();}
  public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getOperationKey(){return operationKey;} public Instant getCreatedAt(){return createdAt;}
}
