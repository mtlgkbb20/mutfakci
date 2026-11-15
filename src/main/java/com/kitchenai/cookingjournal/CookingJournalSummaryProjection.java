package com.kitchenai.cookingjournal;

import java.time.Instant;
import java.util.UUID;

public interface CookingJournalSummaryProjection {
  UUID getId();
  UUID getCookingSessionId();
  UUID getRecipeId();
  String getRecipeTitle();
  Integer getServings();
  Integer getRating();
  Instant getCookedAt();
}
