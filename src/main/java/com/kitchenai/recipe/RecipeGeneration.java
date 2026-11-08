package com.kitchenai.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class RecipeGeneration {
  @Id private UUID id;
  private UUID userId;
  private String desiredFood;
  private String sourceType;
  private String provider;
  private String status;

  @Column(columnDefinition = "TEXT")
  private String decompositionJson;

  @Column(columnDefinition = "TEXT")
  private String componentResponsesJson;

  @Column(columnDefinition = "TEXT")
  private String finalRecipeJson;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  private Instant createdAt;
  private Instant completedAt;

  protected RecipeGeneration() {}

  public RecipeGeneration(UUID userId, String desiredFood, String sourceType, String provider) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.desiredFood = desiredFood;
    this.sourceType = sourceType;
    this.provider = provider;
    this.status = "RUNNING";
    this.createdAt = Instant.now();
  }

  public void complete(String decompositionJson, String componentResponsesJson,
                       String finalRecipeJson) {
    this.status = "COMPLETED";
    this.decompositionJson = decompositionJson;
    this.componentResponsesJson = componentResponsesJson;
    this.finalRecipeJson = finalRecipeJson;
    this.completedAt = Instant.now();
  }

  public void fail(String errorMessage) {
    this.status = "FAILED";
    this.errorMessage = errorMessage;
    this.completedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getDesiredFood() { return desiredFood; }
  public String getSourceType() { return sourceType; }
  public String getProvider() { return provider; }
  public String getStatus() { return status; }
  public String getDecompositionJson() { return decompositionJson; }
  public String getComponentResponsesJson() { return componentResponsesJson; }
  public String getFinalRecipeJson() { return finalRecipeJson; }
  public String getErrorMessage() { return errorMessage; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getCompletedAt() { return completedAt; }
}
