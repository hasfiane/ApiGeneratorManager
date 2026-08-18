ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS monthly_docker_deployment_count INTEGER NOT NULL DEFAULT 0;
