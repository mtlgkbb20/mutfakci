package com.kitchenai.recovery;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class CookingFeedback {
  @Id private UUID id;
  private UUID sessionId;
  private UUID observationId;
  private String status;
  private String action;

  @Column(columnDefinition = "TEXT")
  private String actionDetailsJson;

  private String reasonCode;
  private String messageKey;
  private String resolvedMessage;
  private Instant createdAt;

  public CookingFeedback() {}

  public CookingFeedback(
      UUID s,
      UUID o,
      String st,
      String a,
      String details,
      String reason,
      String key,
      String message) {
    id = UUID.randomUUID();
    sessionId = s;
    observationId = o;
    status = st;
    action = a;
    actionDetailsJson = details;
    reasonCode = reason;
    messageKey = key;
    resolvedMessage = message;
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public String getAction() {
    return action;
  }

  public String getResolvedMessage() {
    return resolvedMessage;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
