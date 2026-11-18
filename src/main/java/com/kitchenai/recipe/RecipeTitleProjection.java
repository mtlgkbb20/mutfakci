package com.kitchenai.recipe;

import java.util.UUID;

public interface RecipeTitleProjection {
  UUID getId();
  String getTitle();
}
