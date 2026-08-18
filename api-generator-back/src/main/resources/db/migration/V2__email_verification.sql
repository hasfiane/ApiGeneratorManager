ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS email_verification_token_hash VARCHAR(64);

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_app_user_email_verification_token_hash
    ON app_user(email_verification_token_hash);
