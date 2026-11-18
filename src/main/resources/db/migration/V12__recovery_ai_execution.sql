CREATE TABLE recovery_ai_execution (
  id UUID PRIMARY KEY,
  attempt_id UUID NOT NULL REFERENCES recipe_recovery_attempt(id) ON DELETE CASCADE,
  operation VARCHAR(40) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  model VARCHAR(160),
  status VARCHAR(30) NOT NULL,
  request_metadata_json TEXT,
  response_json TEXT,
  error_code VARCHAR(80),
  error_message VARCHAR(1000),
  duration_ms BIGINT,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_recovery_execution_attempt ON recovery_ai_execution(attempt_id, created_at);
