CREATE TABLE IF NOT EXISTS generation_job (
    job_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    error_message VARCHAR(2048),
    zip_path VARCHAR(2048),
    output_dir VARCHAR(2048),
    host_port INTEGER,
    api_base_url VARCHAR(512),
    container_id VARCHAR(512),
    logs TEXT,
    user_logs TEXT,
    request_payload_json TEXT,
    build_requested BOOLEAN NOT NULL DEFAULT FALSE,
    deploy_docker_requested BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_port INTEGER
);

CREATE INDEX IF NOT EXISTS idx_generation_job_status ON generation_job(status);
CREATE INDEX IF NOT EXISTS idx_generation_job_created_at ON generation_job(created_at);
