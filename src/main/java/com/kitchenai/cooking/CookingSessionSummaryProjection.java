package com.kitchenai.cooking;

import java.time.Instant;
import java.util.UUID;

public interface CookingSessionSummaryProjection {
  UUID getId();
  UUID getRecipeId();
  String getStatus();
  String getSessionType();
  Instant getStartedAt();
  String getDesiredOutcome();
  String getRecipeTitle();
}
