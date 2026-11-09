-- Text-only recovery attempts do not have an image object.
ALTER TABLE recipe_recovery_attempt ALTER COLUMN image_object_key DROP NOT NULL;
ALTER TABLE recipe_recovery_attempt ADD COLUMN fallback_recipe_name VARCHAR(200);
