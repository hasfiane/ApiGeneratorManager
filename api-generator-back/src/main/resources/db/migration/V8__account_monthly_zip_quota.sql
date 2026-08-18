ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS monthly_zip_download_count INTEGER NOT NULL DEFAULT 0;

