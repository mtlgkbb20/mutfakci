INSERT INTO badge_definition(id, code, name, description, icon, category, repeatable)
VALUES (gen_random_uuid(), 'PEOPLES_CHOICE', 'Halkın Seçimi', 'Bir topluluk etkinliğini kazandın.', '🥇', 'EVENT', true)
ON CONFLICT (code) DO NOTHING;
