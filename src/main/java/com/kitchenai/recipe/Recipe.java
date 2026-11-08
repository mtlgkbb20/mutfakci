package com.kitchenai.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class Recipe {
    @Id
    private UUID id;
    private UUID userId;
    private String catalogId;
    private String sourceType;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String productType;
    private int servings;
    private int preparationTime;
    private int cookingTime;
    @Column(columnDefinition = "TEXT")
    private String ingredientsJson;
    @Column(columnDefinition = "TEXT")
    private String stagesJson;
    private Instant createdAt;

    public Recipe() {
    }

    public Recipe(UUID userId, String title, String description, String productType,
                  int servings, int preparationTime, int cookingTime,
                  String ingredients, String stages) {
        this(userId, null, "AI_GENERATED", title, description, productType, servings,
                preparationTime, cookingTime, ingredients, stages);
    }

    public Recipe(UUID userId, String catalogId, String sourceType, String title,
                  String description, String productType, int servings,
                  int preparationTime, int cookingTime, String ingredients, String stages) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.catalogId = catalogId;
        this.sourceType = sourceType;
        this.title = title;
        this.description = description;
        this.productType = productType;
        this.servings = servings;
        this.preparationTime = preparationTime;
        this.cookingTime = cookingTime;
        this.ingredientsJson = ingredients;
        this.stagesJson = stages;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCatalogId() { return catalogId; }
    public String getSourceType() { return sourceType; }
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
