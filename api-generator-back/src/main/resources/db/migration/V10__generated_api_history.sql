CREATE TABLE IF NOT EXISTS generated_api (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    job_id VARCHAR(64),
    db_type VARCHAR(32),
    file_path VARCHAR(1024),
    api_base_url VARCHAR(256),
    error_message VARCHAR(2048),
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    zip_downloaded_at TIMESTAMP,
    user_id UUID NOT NULL,
    CONSTRAINT fk_generated_api_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_generated_api_user ON generated_api(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_generated_api_job_id ON generated_api(job_id);
