CREATE TABLE ingredient_remainder (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  source_catalog_recipe_id VARCHAR(120),
  source_recipe_id UUID REFERENCES recipe(id),
  cooking_session_id UUID REFERENCES cooking_session(id),
  journal_entry_id UUID REFERENCES cooking_journal_entry(id),
  ingredient_name VARCHAR(160) NOT NULL,
  normalized_name VARCHAR(160) NOT NULL,
  quantity NUMERIC(12,3) NOT NULL CHECK (quantity >= 0),
  CONSTRAINT ck_ingredient_remainder_quantity_status CHECK (
    (status = 'CONSUMED' AND quantity = 0) OR
    (status <> 'CONSUMED' AND quantity > 0)
  ),
  unit VARCHAR(20) NOT NULL,
  certainty VARCHAR(20) NOT NULL CHECK (certainty IN ('USER_CONFIRMED', 'RULE_DERIVED', 'UNKNOWN')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'CONSUMED', 'TRANSFERRED', 'DISCARDED')),
  use_by_date DATE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ingredient_remainder_owner_status
  ON ingredient_remainder(user_id, status, created_at DESC);
CREATE INDEX idx_ingredient_remainder_name
  ON ingredient_remainder(user_id, normalized_name, status);

CREATE TABLE ingredient_remainder_operation (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  remainder_id UUID NOT NULL REFERENCES ingredient_remainder(id),
  operation_key VARCHAR(100) NOT NULL,
  operation_type VARCHAR(20) NOT NULL,
  quantity_before NUMERIC(12,3),
  quantity_after NUMERIC(12,3),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_remainder_operation_user_key UNIQUE(user_id, operation_key)
);
CREATE INDEX idx_remainder_operation_history
  ON ingredient_remainder_operation(user_id, remainder_id, created_at DESC);
