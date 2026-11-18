ALTER TABLE shopping_list
    ADD COLUMN recommendations_json TEXT NOT NULL DEFAULT '[]';
