CREATE TABLE recipe_recovery_attempt (
  id UUID PRIMARY KEY,
  recipe_id UUID REFERENCES recipe(id),
  session_id UUID NOT NULL REFERENCES cooking_session(id),
  parent_attempt_id UUID REFERENCES recipe_recovery_attempt(id),
  component_key VARCHAR(120) NOT NULL,
  stage_order INT,
  attempt_number INT NOT NULL CHECK (attempt_number > 0),
  image_object_key VARCHAR(500) NOT NULL,
  user_problem_category VARCHAR(40),
  user_problem_description TEXT,
  user_status_text TEXT,
  desired_outcome TEXT,
  image_trust_score INTEGER CHECK (image_trust_score IS NULL OR image_trust_score BETWEEN 0 AND 100),
  analysis_confidence NUMERIC(4,3) CHECK (analysis_confidence IS NULL OR (analysis_confidence >= 0 AND analysis_confidence <= 1)),
  target_proximity_score INTEGER CHECK (target_proximity_score IS NULL OR target_proximity_score BETWEEN 0 AND 100),
  observed_state_json TEXT,
  target_state_json TEXT,
  assessment_json TEXT,
  action_json TEXT,
  status VARCHAR(40) NOT NULL,
  user_action_confirmed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_recovery_attempt_number UNIQUE (session_id, component_key, attempt_number)
);
CREATE INDEX idx_recovery_recipe_created ON recipe_recovery_attempt(recipe_id, created_at DESC);
CREATE INDEX idx_recovery_session_component_attempt ON recipe_recovery_attempt(session_id, component_key, attempt_number);
CREATE INDEX idx_recovery_parent ON recipe_recovery_attempt(parent_attempt_id);
