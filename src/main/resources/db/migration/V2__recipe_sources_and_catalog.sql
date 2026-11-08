ALTER TABLE recipe ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE recipe ADD COLUMN catalog_id VARCHAR(160);
ALTER TABLE recipe ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'AI_GENERATED';
CREATE UNIQUE INDEX uq_recipe_catalog_id ON recipe(catalog_id) WHERE catalog_id IS NOT NULL;
CREATE INDEX idx_recipe_title_normalized ON recipe(lower(title));
