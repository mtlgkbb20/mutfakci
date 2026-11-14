CREATE TABLE shopping_list (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  title VARCHAR(160) NOT NULL,
  source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('RECIPE', 'RECIPES', 'MEAL_PLAN', 'MANUAL')),
  source_meal_plan_id UUID REFERENCES meal_plan(id),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED')),
  currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
  completion_operation_key VARCHAR(100),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_shopping_list_user_updated ON shopping_list(user_id, updated_at DESC);

CREATE TABLE shopping_list_item (
  id UUID PRIMARY KEY,
  list_id UUID NOT NULL REFERENCES shopping_list(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  normalized_name VARCHAR(160) NOT NULL,
  category VARCHAR(60) NOT NULL DEFAULT 'OTHER',
  required_quantity NUMERIC(12,3) NOT NULL CHECK (required_quantity >= 0),
  pantry_covered_quantity NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (pantry_covered_quantity >= 0),
  to_buy_quantity NUMERIC(12,3) NOT NULL CHECK (to_buy_quantity >= 0),
  purchased_quantity NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (purchased_quantity >= 0),
  unit VARCHAR(20) NOT NULL,
  checked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_shopping_list_item_list_category ON shopping_list_item(list_id, category, name);
