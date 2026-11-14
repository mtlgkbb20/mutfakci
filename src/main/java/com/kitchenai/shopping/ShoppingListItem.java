package com.kitchenai.shopping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_list_item")
public class ShoppingListItem {
  @Id private UUID id;
  @Column(name = "list_id", nullable = false) private UUID listId;
  @Column(nullable = false, length = 160) private String name;
  @Column(name = "normalized_name", nullable = false, length = 160) private String normalizedName;
  @Column(nullable = false, length = 60) private String category;
  @Column(name = "required_quantity", nullable = false, precision = 12, scale = 3) private BigDecimal requiredQuantity;
  @Column(name = "pantry_covered_quantity", nullable = false, precision = 12, scale = 3) private BigDecimal pantryCoveredQuantity;
  @Column(name = "to_buy_quantity", nullable = false, precision = 12, scale = 3) private BigDecimal toBuyQuantity;
  @Column(name = "purchased_quantity", nullable = false, precision = 12, scale = 3) private BigDecimal purchasedQuantity;
  @Column(nullable = false, length = 20) private String unit;
  @Column(nullable = false) private boolean checked;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version private long version;

  protected ShoppingListItem() {}

  public ShoppingListItem(UUID listId, String name, String normalizedName, String category,
      BigDecimal requiredQuantity, BigDecimal pantryCoveredQuantity, BigDecimal toBuyQuantity, String unit) {
    this.id = UUID.randomUUID();
    this.listId = listId;
    this.name = name;
    this.normalizedName = normalizedName;
    this.category = category;
    this.requiredQuantity = requiredQuantity;
    this.pantryCoveredQuantity = pantryCoveredQuantity;
    this.toBuyQuantity = toBuyQuantity;
    this.purchasedQuantity = BigDecimal.ZERO;
    this.unit = unit;
    this.checked = false;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void update(String name, String normalizedName, String category, BigDecimal toBuyQuantity,
      BigDecimal purchasedQuantity, Boolean checked) {
    this.name = name;
    this.normalizedName = normalizedName;
    this.category = category;
    this.toBuyQuantity = toBuyQuantity;
    this.purchasedQuantity = purchasedQuantity;
    this.checked = checked != null ? checked : purchasedQuantity.signum() > 0;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getListId() { return listId; }
  public String getName() { return name; }
  public String getNormalizedName() { return normalizedName; }
  public String getCategory() { return category; }
  public BigDecimal getRequiredQuantity() { return requiredQuantity; }
  public BigDecimal getPantryCoveredQuantity() { return pantryCoveredQuantity; }
  public BigDecimal getToBuyQuantity() { return toBuyQuantity; }
  public BigDecimal getPurchasedQuantity() { return purchasedQuantity; }
  public String getUnit() { return unit; }
  public boolean isChecked() { return checked; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
