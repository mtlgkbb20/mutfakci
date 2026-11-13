package com.kitchenai.zerowaste;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ingredient_remainder")
public class IngredientRemainder {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "source_catalog_recipe_id", length = 120) private String sourceCatalogRecipeId;
  @Column(name = "source_recipe_id") private UUID sourceRecipeId;
  @Column(name = "cooking_session_id") private UUID cookingSessionId;
  @Column(name = "journal_entry_id") private UUID journalEntryId;
  @Column(name = "ingredient_name", nullable = false, length = 160) private String ingredientName;
  @Column(name = "normalized_name", nullable = false, length = 160) private String normalizedName;
  @Column(nullable = false, precision = 12, scale = 3) private BigDecimal quantity;
  @Column(nullable = false, length = 20) private String unit;
  @Column(nullable = false, length = 20) private String certainty;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "use_by_date") private LocalDate useByDate;
  @Version private long version;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected IngredientRemainder() {}

  public IngredientRemainder(UUID userId, String catalogRecipeId, UUID recipeId, UUID sessionId,
      UUID journalEntryId, String ingredientName, String normalizedName, BigDecimal quantity,
      String unit, String certainty, LocalDate useByDate) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.sourceCatalogRecipeId = catalogRecipeId;
    this.sourceRecipeId = recipeId;
    this.cookingSessionId = sessionId;
    this.journalEntryId = journalEntryId;
    this.ingredientName = ingredientName;
    this.normalizedName = normalizedName;
    this.quantity = quantity;
    this.unit = unit;
    this.certainty = certainty;
    this.status = "AVAILABLE";
    this.useByDate = useByDate;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void correct(BigDecimal quantity, String unit, LocalDate useByDate) {
    this.quantity = quantity;
    this.unit = unit;
    this.useByDate = useByDate;
    this.updatedAt = Instant.now();
  }

  public BigDecimal consume(BigDecimal amount) {
    if (amount.compareTo(quantity) > 0) throw new IllegalArgumentException("Consumption exceeds remainder quantity");
    BigDecimal before = quantity;
    quantity = quantity.subtract(amount);
    if (quantity.signum() == 0) status = "CONSUMED";
    updatedAt = Instant.now();
    return before;
  }

  public void transition(String nextStatus) {
    if (!"AVAILABLE".equals(status)) throw new IllegalStateException("Remainder is not available");
    if (!java.util.Set.of("TRANSFERRED", "DISCARDED").contains(nextStatus))
      throw new IllegalArgumentException("Unsupported remainder transition");
    status = nextStatus;
    updatedAt = Instant.now();
  }

  public UUID getId(){ return id; }
  public UUID getUserId(){ return userId; }
  public String getSourceCatalogRecipeId(){ return sourceCatalogRecipeId; }
  public UUID getSourceRecipeId(){ return sourceRecipeId; }
  public UUID getCookingSessionId(){ return cookingSessionId; }
  public UUID getJournalEntryId(){ return journalEntryId; }
  public String getIngredientName(){ return ingredientName; }
  public String getNormalizedName(){ return normalizedName; }
  public BigDecimal getQuantity(){ return quantity; }
  public String getUnit(){ return unit; }
  public String getCertainty(){ return certainty; }
  public String getStatus(){ return status; }
  public LocalDate getUseByDate(){ return useByDate; }
  public long getVersion(){ return version; }
  public Instant getCreatedAt(){ return createdAt; }
  public Instant getUpdatedAt(){ return updatedAt; }
}
