package com.kitchenai.cookingjournal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cooking_journal_entry")
public class CookingJournalEntry {
  @Id private UUID id;
  private UUID userId;
  private UUID cookingSessionId;
  private UUID recipeId;
  @Column(columnDefinition = "TEXT", nullable = false)
  private String recipeSnapshotJson;
  private Integer servings;
  private Integer rating;
  @Column(columnDefinition = "TEXT")
  private String notes;
  private Instant cookedAt;
  private Instant createdAt;
  private Instant updatedAt;

  protected CookingJournalEntry() {}

  public CookingJournalEntry(
      UUID userId, UUID cookingSessionId, UUID recipeId, String recipeSnapshotJson,
      Integer servings, Integer rating, String notes, Instant cookedAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.cookingSessionId = cookingSessionId;
    this.recipeId = recipeId;
    this.recipeSnapshotJson = recipeSnapshotJson;
    this.servings = servings;
    this.rating = rating;
    this.notes = notes;
    this.cookedAt = cookedAt == null ? Instant.now() : cookedAt;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(Integer servings, Integer rating, String notes) {
    if (servings != null) this.servings = servings;
    if (rating != null) this.rating = rating;
    if (notes != null) this.notes = notes;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getCookingSessionId() { return cookingSessionId; }
  public UUID getRecipeId() { return recipeId; }
  public String getRecipeSnapshotJson() { return recipeSnapshotJson; }
  public Integer getServings() { return servings; }
  public Integer getRating() { return rating; }
  public String getNotes() { return notes; }
  public Instant getCookedAt() { return cookedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
