ALTER TABLE app_user ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE xp_ledger_entry (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    xp_delta INT NOT NULL CHECK (xp_delta <> 0),
    source_type VARCHAR(50) NOT NULL,
    source_reference VARCHAR(120),
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_xp_ledger_user_created ON xp_ledger_entry(user_id, created_at);
CREATE INDEX idx_xp_ledger_created ON xp_ledger_entry(created_at);

INSERT INTO xp_ledger_entry(id, user_id, xp_delta, source_type, source_reference, idempotency_key, reason, created_at)
SELECT id, user_id, amount, 'LEGACY', COALESCE(session_id::text, id::text),
       'legacy:' || id::text, reason, created_at
FROM xp_transaction
WHERE amount <> 0
ON CONFLICT (idempotency_key) DO NOTHING;

CREATE TABLE badge_definition (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon VARCHAR(40) NOT NULL,
    category VARCHAR(30) NOT NULL,
    repeatable BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE user_badge_award (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    badge_id UUID NOT NULL REFERENCES badge_definition(id),
    source_key VARCHAR(160) NOT NULL,
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(500),
    UNIQUE(user_id, badge_id, source_key)
);
CREATE INDEX idx_user_badges_history ON user_badge_award(user_id, awarded_at DESC);

INSERT INTO badge_definition(id, code, name, description, icon, category, repeatable) VALUES
(gen_random_uuid(), 'FIRST_DISH', 'İlk Yemeğim', 'İlk tamamlanan pişirme deneyimin.', '🍳', 'MILESTONE', false),
(
    gen_random_uuid(),
    'WEEKLY_REGULAR',
    'Haftanın Müdavimi',
    'Bir haftada en az üç pişirme oturumu tamamladın.',
    '🔥',
    'WEEKLY',
    true
),
(gen_random_uuid(), 'PODIUM_FINISH', 'Podyum', 'Haftalık sıralamada ilk üçe girdin.', '🏆', 'WEEKLY', true),
(
    gen_random_uuid(),
    'COMMUNITY_CHEF',
    'Topluluk Şefi',
    'Üç farklı topluluk etkinliğine katıldın.',
    '👩‍🍳',
    'COMMUNITY',
    false
);

CREATE TABLE weekly_leaderboard_snapshot (
    id UUID PRIMARY KEY,
    week_start DATE NOT NULL UNIQUE,
    week_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SETTLED',
    settled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE weekly_leaderboard_entry (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES weekly_leaderboard_snapshot(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    rank_position INT NOT NULL,
    weekly_xp INT NOT NULL,
    lifetime_xp INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(snapshot_id, user_id),
    UNIQUE(snapshot_id, rank_position)
);
CREATE INDEX idx_weekly_entries_rank ON weekly_leaderboard_entry(snapshot_id, rank_position);

CREATE TABLE gamification_reward_grant (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    reward_type VARCHAR(40) NOT NULL,
    source_key VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    redeemed_at TIMESTAMPTZ,
    UNIQUE(user_id, reward_type, source_key)
);

CREATE TABLE cooking_event (
    id UUID PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    theme VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    starts_at TIMESTAMPTZ NOT NULL,
    submission_opens_at TIMESTAMPTZ NOT NULL,
    submission_closes_at TIMESTAMPTZ NOT NULL,
    voting_opens_at TIMESTAMPTZ NOT NULL,
    voting_closes_at TIMESTAMPTZ NOT NULL,
    max_images INT NOT NULL DEFAULT 4 CHECK (max_images BETWEEN 1 AND 8),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (submission_opens_at <= submission_closes_at),
    CHECK (voting_opens_at <= voting_closes_at)
);
CREATE INDEX idx_cooking_event_status_dates ON cooking_event(status, starts_at);

CREATE TABLE event_participation (
    event_id UUID NOT NULL REFERENCES cooking_event(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(event_id, user_id)
);
CREATE TABLE event_submission (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES cooking_event(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    caption VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    moderated_at TIMESTAMPTZ,
    moderation_reason VARCHAR(500),
    UNIQUE(event_id, user_id)
);
CREATE INDEX idx_event_submission_event_status ON event_submission(event_id, status, submitted_at);
CREATE TABLE event_submission_image (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES event_submission(id) ON DELETE CASCADE,
    content_type VARCHAR(40) NOT NULL,
    image_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE event_voter_snapshot (
    event_id UUID NOT NULL REFERENCES cooking_event(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    rank_position INT NOT NULL,
    source_week_start DATE NOT NULL,
    selected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(event_id, user_id),
    UNIQUE(event_id, rank_position)
);
CREATE TABLE event_vote (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES cooking_event(id),
    submission_id UUID NOT NULL REFERENCES event_submission(id),
    voter_user_id UUID NOT NULL REFERENCES app_user(id),
    score INT NOT NULL CHECK (score BETWEEN 1 AND 10),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(submission_id, voter_user_id)
);
CREATE TABLE event_result (
    submission_id UUID PRIMARY KEY REFERENCES event_submission(id),
    event_id UUID NOT NULL REFERENCES cooking_event(id),
    average_score NUMERIC(5,3) NOT NULL,
    vote_count INT NOT NULL,
    rank_position INT NOT NULL,
    finalized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(event_id, rank_position)
);
