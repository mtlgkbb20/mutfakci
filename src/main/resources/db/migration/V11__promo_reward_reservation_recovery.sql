ALTER TABLE gamification_reward_grant ADD COLUMN reserved_at TIMESTAMPTZ;
CREATE INDEX idx_gamification_reward_reserved ON gamification_reward_grant(status, reserved_at);
