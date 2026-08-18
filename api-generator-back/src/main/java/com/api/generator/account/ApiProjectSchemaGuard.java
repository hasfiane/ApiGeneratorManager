package com.api.generator.account;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApiProjectSchemaGuard implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public ApiProjectSchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("""
                ALTER TABLE api_project
                    ADD COLUMN IF NOT EXISTS docker_deployment_quota_charged BOOLEAN DEFAULT FALSE
                """);
        jdbc.execute("""
                UPDATE api_project
                SET docker_deployment_quota_charged = FALSE
                WHERE docker_deployment_quota_charged IS NULL
                """);
        jdbc.execute("""
                ALTER TABLE app_user
                    ADD COLUMN IF NOT EXISTS monthly_zip_download_count INTEGER DEFAULT 0
                """);
        jdbc.execute("""
                UPDATE app_user
                SET monthly_zip_download_count = 0
                WHERE monthly_zip_download_count IS NULL
                """);
    }
}
