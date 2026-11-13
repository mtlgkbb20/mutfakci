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
