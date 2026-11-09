ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE email_verification (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 code_hash CHAR(64) NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
 resend_after TIMESTAMPTZ NOT NULL, consumed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_expiry ON email_verification(expires_at);
CREATE TABLE auth_rate_limit (
 bucket_key CHAR(64) PRIMARY KEY, window_started_at TIMESTAMPTZ NOT NULL,
 request_count INT NOT NULL CHECK (request_count >= 0)
);