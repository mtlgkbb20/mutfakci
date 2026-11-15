package com.kitchenai.cookingjournal;

import java.time.Instant;
import java.util.UUID;

public interface RecipeVariationSummaryProjection {
  UUID getId();
  String getTitle();
  String getDescription();
  int getServings();
  Instant getCreatedAt();
}
