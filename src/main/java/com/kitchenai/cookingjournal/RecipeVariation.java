package com.kitchenai.cookingjournal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_variation")
public class RecipeVariation {
  @Id private UUID id;
  private UUID userId;
  private UUID journalEntryId;
  private UUID sourceRecipeId;
  private String title;
  @Column(columnDefinition = "TEXT") private String description;
  private String productType;
  private int servings;
  private int preparationTime;
  private int cookingTime;
  @Column(columnDefinition = "TEXT", nullable = false) private String ingredientsJson;
  @Column(columnDefinition = "TEXT", nullable = false) private String stagesJson;
  private Instant createdAt;

  protected RecipeVariation() {}

  public RecipeVariation(UUID userId, UUID journalEntryId, UUID sourceRecipeId, String title,
      String description, String productType, int servings, int preparationTime, int cookingTime,
      String ingredientsJson, String stagesJson) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.journalEntryId = journalEntryId;
    this.sourceRecipeId = sourceRecipeId;
    this.title = title;
    this.description = description;
    this.productType = productType;
    this.servings = servings;
    this.preparationTime = preparationTime;
    this.cookingTime = cookingTime;
    this.ingredientsJson = ingredientsJson;
    this.stagesJson = stagesJson;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getJournalEntryId() { return journalEntryId; }
  public UUID getSourceRecipeId() { return sourceRecipeId; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public String getProductType() { return productType; }
  public int getServings() { return servings; }
  public int getPreparationTime() { return preparationTime; }
  public int getCookingTime() { return cookingTime; }
  public String getIngredientsJson() { return ingredientsJson; }
  public String getStagesJson() { return stagesJson; }
  public Instant getCreatedAt() { return createdAt; }
}
