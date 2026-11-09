CREATE TABLE payment_checkout (
    id UUID PRIMARY KEY,
    merchant_order_id VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES app_user(id),
    plan_code VARCHAR(40) NOT NULL CHECK (plan_code IN ('COOKER_MONTHLY', 'COOKER_YEARLY')),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'TRY',
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(255),
    paid_amount NUMERIC(12, 2),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_checkout_user_created
    ON payment_checkout(user_id, created_at DESC);

CREATE INDEX idx_payment_checkout_status_expiry
    ON payment_checkout(status, expires_at);
