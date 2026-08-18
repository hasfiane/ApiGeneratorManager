ALTER TABLE generated_api
    ADD COLUMN IF NOT EXISTS preview_config_json TEXT;

CREATE TABLE IF NOT EXISTS api_preview (
    id UUID PRIMARY KEY,
    generated_api_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    container_id VARCHAR(128),
    image_tag VARCHAR(160),
    workspace_dir VARCHAR(1024),
    host_port INTEGER,
    base_url VARCHAR(256),
    error_message VARCHAR(2048),
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    stopped_at TIMESTAMP,
    CONSTRAINT fk_api_preview_generated_api FOREIGN KEY (generated_api_id) REFERENCES generated_api(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_api_preview_generated_api ON api_preview(generated_api_id);
