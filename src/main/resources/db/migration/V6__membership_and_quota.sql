CREATE TABLE user_membership (
    user_id UUID PRIMARY KEY REFERENCES app_user(id),
    tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    billing_interval VARCHAR(20) NOT NULL DEFAULT 'NONE',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    provider VARCHAR(30),
    provider_customer_id VARCHAR(255),
    provider_subscription_id VARCHAR(255),
    paid_until TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_membership_tier CHECK (tier IN ('FREE','COOKER','CHEF')),
    CONSTRAINT ck_user_membership_interval CHECK (billing_interval IN ('NONE','MONTHLY','YEARLY'))
);

INSERT INTO user_membership(user_id, tier, billing_interval, status)
SELECT id, 'FREE', 'NONE', 'ACTIVE' FROM app_user
ON CONFLICT (user_id) DO NOTHING;

CREATE TABLE recipe_quota_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    period_key VARCHAR(80) NOT NULL,
    used_count INT NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, period_key)
);

-- Preserve existing successful AI generation history when assigning the Free lifetime allowance.
INSERT INTO recipe_quota_usage(user_id, period_key, used_count)
SELECT user_id, 'FREE_LIFETIME', COUNT(*)::INT
FROM recipe_generation
WHERE status = 'COMPLETED' AND source_type = 'AI_GENERATED'
GROUP BY user_id
ON CONFLICT (user_id, period_key) DO NOTHING;

CREATE TABLE payment_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    merchant_reference VARCHAR(100) NOT NULL,
    event_status VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider, provider_event_id)
);
