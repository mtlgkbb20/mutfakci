CREATE TABLE cooking_journal_entry (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  cooking_session_id UUID NOT NULL UNIQUE REFERENCES cooking_session(id),
  recipe_id UUID REFERENCES recipe(id),
  recipe_snapshot_json TEXT NOT NULL,
  servings INT CHECK (servings IS NULL OR servings BETWEEN 1 AND 30),
  rating INT CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
  notes TEXT,
  cooked_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cooking_journal_user_date
  ON cooking_journal_entry(user_id, cooked_at DESC);
CREATE INDEX idx_cooking_journal_recipe_attempts
  ON cooking_journal_entry(user_id, recipe_id, cooked_at DESC);

CREATE TABLE cooking_journal_media (
  id UUID PRIMARY KEY,
  journal_entry_id UUID NOT NULL UNIQUE REFERENCES cooking_journal_entry(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id),
  object_key VARCHAR(500) NOT NULL UNIQUE,
  content_type VARCHAR(80) NOT NULL,
  uploaded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_cooking_journal_media_owner ON cooking_journal_media(user_id, journal_entry_id);

CREATE TABLE recipe_variation (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  journal_entry_id UUID NOT NULL REFERENCES cooking_journal_entry(id),
  source_recipe_id UUID NOT NULL REFERENCES recipe(id),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  product_type VARCHAR(60) NOT NULL,
  servings INT NOT NULL CHECK (servings BETWEEN 1 AND 30),
  preparation_time INT NOT NULL,
  cooking_time INT NOT NULL,
  ingredients_json TEXT NOT NULL,
  stages_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_recipe_variation_owner ON recipe_variation(user_id, created_at DESC);
CREATE INDEX idx_recipe_variation_source ON recipe_variation(user_id, source_recipe_id, created_at DESC);
