ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_reset_token_hash VARCHAR(64);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP;
