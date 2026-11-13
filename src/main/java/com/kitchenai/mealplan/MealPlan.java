package com.kitchenai.mealplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "meal_plan")
public class MealPlan {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "week_start", nullable = false) private LocalDate weekStart;
  @Column(nullable = false) private int servings;
  @Column(name = "meal_slots", nullable = false, columnDefinition = "TEXT") private String mealSlots;
  @Column(name = "max_preparation_minutes", nullable = false) private int maxPreparationMinutes;
  @Column(name = "weekly_budget", precision = 12, scale = 2) private BigDecimal weeklyBudget;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "dietary_restrictions", nullable = false, columnDefinition = "TEXT") private String dietaryRestrictions;
  @Column(length = 500) private String goal;
  @Column(columnDefinition = "TEXT") private String notes;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version private long version;

  protected MealPlan() {}

  public MealPlan(UUID userId, LocalDate weekStart, int servings, String mealSlots,
      int maxPreparationMinutes, BigDecimal weeklyBudget, String currency,
      String dietaryRestrictions, String goal, String notes) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.weekStart = weekStart;
    this.servings = servings;
    this.mealSlots = mealSlots;
    this.maxPreparationMinutes = maxPreparationMinutes;
    this.weeklyBudget = weeklyBudget;
    this.currency = currency;
    this.dietaryRestrictions = dietaryRestrictions;
    this.goal = goal;
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public LocalDate getWeekStart() { return weekStart; }
  public int getServings() { return servings; }
  public String getMealSlots() { return mealSlots; }
  public int getMaxPreparationMinutes() { return maxPreparationMinutes; }
  public BigDecimal getWeeklyBudget() { return weeklyBudget; }
  public String getCurrency() { return currency; }
  public String getDietaryRestrictions() { return dietaryRestrictions; }
  public String getGoal() { return goal; }
  public String getNotes() { return notes; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
