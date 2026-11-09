-- Preserve XP accumulated in the legacy user_progress aggregate before the ledger became authoritative.
-- The difference avoids double-counting XP already imported from xp_transaction in V7.
INSERT INTO xp_ledger_entry(id,user_id,xp_delta,source_type,source_reference,idempotency_key,reason,created_at)
SELECT gen_random_uuid(),
       p.user_id,
       p.total_xp - COALESCE(SUM(t.amount),0)::int,
       'LEGACY_PROGRESS',
       p.user_id::text,
       'legacy-progress:' || p.user_id::text,
       'Legacy progress aggregate backfill',
       COALESCE(p.updated_at, now())
FROM user_progress p
LEFT JOIN xp_transaction t ON t.user_id=p.user_id
GROUP BY p.user_id,p.total_xp,p.updated_at
HAVING p.total_xp - COALESCE(SUM(t.amount),0)::int <> 0
ON CONFLICT (idempotency_key) DO NOTHING;
