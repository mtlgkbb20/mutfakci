ALTER TABLE cooking_journal_entry
  ADD COLUMN recipe_title TEXT;

UPDATE cooking_journal_entry
SET recipe_title = COALESCE(
  NULLIF(BTRIM(recipe_snapshot_json::jsonb ->> 'title'), ''),
  'Tarif adı bulunamadı'
);

ALTER TABLE cooking_journal_entry
  ALTER COLUMN recipe_title SET NOT NULL;
