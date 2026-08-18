CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    provider VARCHAR(16) NOT NULL,
    google_sub VARCHAR(128),
    provider_user_id VARCHAR(160),
    display_name VARCHAR(160),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    email_verification_token_hash VARCHAR(64),
    email_verification_expires_at TIMESTAMP,
    password_reset_token_hash VARCHAR(64),
    password_reset_expires_at TIMESTAMP,
    roles VARCHAR(255) NOT NULL,
    plan VARCHAR(32) NOT NULL,
    plan_expires_at TIMESTAMP,
    monthly_generation_count INTEGER NOT NULL DEFAULT 0,
    monthly_zip_download_count INTEGER NOT NULL DEFAULT 0,
    monthly_generation_period VARCHAR(7),
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_provider_user_id ON app_user(provider, provider_user_id);

CREATE TABLE IF NOT EXISTS api_project (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    job_id VARCHAR(64),
    db_type VARCHAR(32),
    source_jdbc_url VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    last_deployed_at TIMESTAMP,
    api_base_url VARCHAR(256),
    docker_image VARCHAR(256),
    docker_deployment_quota_charged BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_api_project_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS idx_api_project_owner ON api_project(owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_api_project_job_id_unique ON api_project(job_id);
