package com.kitchenai.shopping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_list")
public class ShoppingList {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(nullable = false, length = 160) private String title;
  @Column(name = "source_type", nullable = false, length = 20) private String sourceType;
  @Column(name = "source_meal_plan_id") private UUID sourceMealPlanId;
  @Column(nullable = false, length = 20) private String status;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "completion_operation_key", length = 100) private String completionOperationKey;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version private long version;

  protected ShoppingList() {}

  public ShoppingList(UUID userId, String title, String sourceType, UUID sourceMealPlanId, String currency) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.title = title;
    this.sourceType = sourceType;
    this.sourceMealPlanId = sourceMealPlanId;
    this.status = "ACTIVE";
    this.currency = currency;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void rename(String title) { this.title = title; this.updatedAt = Instant.now(); }
  public void complete(String operationKey) {
    this.status = "COMPLETED";
    this.completionOperationKey = operationKey;
    this.completedAt = Instant.now();
    this.updatedAt = this.completedAt;
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getTitle() { return title; }
  public String getSourceType() { return sourceType; }
  public UUID getSourceMealPlanId() { return sourceMealPlanId; }
  public String getStatus() { return status; }
  public String getCurrency() { return currency; }
  public String getCompletionOperationKey() { return completionOperationKey; }
  public Instant getCompletedAt() { return completedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
