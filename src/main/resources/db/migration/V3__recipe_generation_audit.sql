CREATE TABLE recipe_generation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    desired_food VARCHAR(500) NOT NULL,
    normalized_desired_food VARCHAR(500) NOT NULL,
    recipe_id UUID REFERENCES recipe(id),
    source_type VARCHAR(30) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    decomposition_json TEXT,
    component_responses_json TEXT,
    final_recipe_json TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_recipe_generation_user ON recipe_generation(user_id, created_at DESC);
CREATE INDEX idx_recipe_generation_cache
    ON recipe_generation(user_id, normalized_desired_food, status, created_at DESC);
