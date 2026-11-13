CREATE TABLE pantry_item (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  name VARCHAR(160) NOT NULL,
  normalized_name VARCHAR(160) NOT NULL,
  category VARCHAR(60) NOT NULL,
  quantity NUMERIC(12,3) NOT NULL CHECK (quantity >= 0),
  unit VARCHAR(20) NOT NULL,
  expiry_date DATE,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_pantry_user_active ON pantry_item(user_id, archived, category);
CREATE INDEX idx_pantry_expiry ON pantry_item(user_id, expiry_date) WHERE archived = FALSE;
CREATE INDEX idx_pantry_name ON pantry_item(user_id, normalized_name) WHERE archived = FALSE;

CREATE TABLE pantry_movement (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  pantry_item_id UUID NOT NULL REFERENCES pantry_item(id),
  movement_type VARCHAR(20) NOT NULL,
  quantity_delta NUMERIC(12,3) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  reason VARCHAR(255),
  operation_key VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_pantry_movement_item ON pantry_movement(user_id, pantry_item_id, created_at DESC);
CREATE UNIQUE INDEX uq_pantry_movement_operation_item ON pantry_movement(user_id, operation_key, pantry_item_id) WHERE operation_key IS NOT NULL;
CREATE TABLE pantry_operation (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  operation_key VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_pantry_operation_user_key UNIQUE(user_id, operation_key)
);
