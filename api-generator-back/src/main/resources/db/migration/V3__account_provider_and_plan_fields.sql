ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(160);

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS plan_expires_at TIMESTAMP;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS monthly_generation_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS monthly_generation_period VARCHAR(7);

UPDATE app_user
SET provider_user_id = google_sub
WHERE provider = 'GOOGLE'
  AND provider_user_id IS NULL
  AND google_sub IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_app_user_provider_user_id
    ON app_user(provider, provider_user_id);
