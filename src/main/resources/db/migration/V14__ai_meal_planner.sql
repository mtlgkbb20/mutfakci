CREATE TABLE meal_plan (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  week_start DATE NOT NULL,
  servings INTEGER NOT NULL CHECK (servings BETWEEN 1 AND 30),
  meal_slots TEXT NOT NULL,
  max_preparation_minutes INTEGER NOT NULL CHECK (max_preparation_minutes BETWEEN 1 AND 1440),
  weekly_budget NUMERIC(12,2),
  currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
  dietary_restrictions TEXT NOT NULL DEFAULT '[]',
  goal VARCHAR(500),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_meal_plan_user_week UNIQUE(user_id, week_start)
);

CREATE INDEX idx_meal_plan_user_created ON meal_plan(user_id, created_at DESC);

CREATE TABLE meal_plan_entry (
  id UUID PRIMARY KEY,
  plan_id UUID NOT NULL REFERENCES meal_plan(id) ON DELETE CASCADE,
  meal_date DATE NOT NULL,
  meal_slot VARCHAR(20) NOT NULL CHECK (meal_slot IN ('BREAKFAST', 'LUNCH', 'DINNER')),
  recipe_catalog_id VARCHAR(120) NOT NULL,
  servings INTEGER NOT NULL CHECK (servings BETWEEN 1 AND 30),
  note VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_meal_plan_entry_slot UNIQUE(plan_id, meal_date, meal_slot)
);

CREATE INDEX idx_meal_plan_entry_plan_date ON meal_plan_entry(plan_id, meal_date, meal_slot);
