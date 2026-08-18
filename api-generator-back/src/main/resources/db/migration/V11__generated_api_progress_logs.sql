ALTER TABLE generated_api
    ADD COLUMN IF NOT EXISTS progress INTEGER;

ALTER TABLE generated_api
    ADD COLUMN IF NOT EXISTS logs TEXT;

UPDATE generated_api
SET progress = COALESCE(progress, 0),
    logs = COALESCE(logs, '');
