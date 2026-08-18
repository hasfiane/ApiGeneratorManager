-- noinspection SqlNoDataSourceInspection
ALTER TABLE api_project
    ADD COLUMN IF NOT EXISTS zip_downloaded_at TIMESTAMP;
