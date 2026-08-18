ALTER TABLE api_project
    ADD COLUMN IF NOT EXISTS docker_deployment_quota_charged BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE app_user
SET monthly_docker_deployment_count = 0;
