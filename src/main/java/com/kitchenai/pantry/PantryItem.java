package com.kitchenai.pantry;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pantry_item")
public class PantryItem {
  @Id private UUID id;
  @Column(name="user_id", nullable=false) private UUID userId;
  @Column(nullable=false, length=160) private String name;
  @Column(name="normalized_name", nullable=false, length=160) private String normalizedName;
  @Column(nullable=false, length=60) private String category;
  @Column(nullable=false, precision=12, scale=3) private BigDecimal quantity;
  @Column(nullable=false, length=20) private String unit;
  @Column(name="expiry_date") private LocalDate expiryDate;
  @Column(nullable=false) private boolean archived;
  @Version private long version;
  @Column(name="created_at", nullable=false) private Instant createdAt;
  @Column(name="updated_at", nullable=false) private Instant updatedAt;

  protected PantryItem() {}
  public PantryItem(UUID userId, String name, String normalizedName, String category, BigDecimal quantity, String unit, LocalDate expiryDate) {
    this.id=UUID.randomUUID(); this.userId=userId; this.name=name; this.normalizedName=normalizedName;
    this.category=category; this.quantity=quantity; this.unit=unit; this.expiryDate=expiryDate;
    this.archived=false; this.createdAt=Instant.now(); this.updatedAt=this.createdAt;
  }
  public void update(String name,String normalizedName,String category,BigDecimal quantity,String unit,LocalDate expiryDate) {
    this.name=name; this.normalizedName=normalizedName; this.category=category; this.quantity=quantity; this.unit=unit; this.expiryDate=expiryDate; this.updatedAt=Instant.now();
  }
  public void adjust(BigDecimal delta) { this.quantity=this.quantity.add(delta); this.updatedAt=Instant.now(); }
  public void archive() { this.archived=true; this.updatedAt=Instant.now(); }
  public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getName(){return name;}
  public String getNormalizedName(){return normalizedName;} public String getCategory(){return category;}
  public BigDecimal getQuantity(){return quantity;} public String getUnit(){return unit;} public LocalDate getExpiryDate(){return expiryDate;}
  public boolean isArchived(){return archived;} public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;}
}