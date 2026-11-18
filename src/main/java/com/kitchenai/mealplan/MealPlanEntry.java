package com.kitchenai.mealplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "meal_plan_entry")
public class MealPlanEntry {
  @Id private UUID id;
  @Column(name = "plan_id", nullable = false) private UUID planId;
  @Column(name = "meal_date", nullable = false) private LocalDate mealDate;
  @Column(name = "meal_slot", nullable = false, length = 20) private String mealSlot;
  @Column(name = "recipe_catalog_id", nullable = false, length = 120) private String recipeCatalogId;
  @Column(nullable = false) private int servings;
  @Column(length = 500) private String note;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected MealPlanEntry() {}

  public MealPlanEntry(UUID planId, LocalDate mealDate, String mealSlot,
      String recipeCatalogId, int servings, String note) {
    this.id = UUID.randomUUID();
    this.planId = planId;
    this.mealDate = mealDate;
    this.mealSlot = mealSlot;
    this.recipeCatalogId = recipeCatalogId;
    this.servings = servings;
    this.note = note;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void replace(String recipeCatalogId, int servings, String note) {
    this.recipeCatalogId = recipeCatalogId;
    this.servings = servings;
    this.note = note;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getPlanId() { return planId; }
  public LocalDate getMealDate() { return mealDate; }
  public String getMealSlot() { return mealSlot; }
  public String getRecipeCatalogId() { return recipeCatalogId; }
  public int getServings() { return servings; }
  public String getNote() { return note; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
