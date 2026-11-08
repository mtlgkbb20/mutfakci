package com.kitchenai.recovery;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class CookingObservation {
  @Id private UUID id;
  private UUID sessionId;
  private String imageObjectKey;

  @Column(columnDefinition = "TEXT")
  private String observedStateJson;

  @Column(columnDefinition = "TEXT")
  private String targetComparisonJson;

  private double confidence;
  private Instant createdAt;

  public CookingObservation() {}

  public CookingObservation(
      UUID s, String key, String observed, String comparison, double confidence) {
    id = UUID.randomUUID();
    sessionId = s;
    imageObjectKey = key;
    observedStateJson = observed;
    targetComparisonJson = comparison;
    this.confidence = confidence;
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getObservedStateJson() {
    return observedStateJson;
  }

  public String getTargetComparisonJson() {
    return targetComparisonJson;
  }

  public double getConfidence() {
    return confidence;
  }
}
