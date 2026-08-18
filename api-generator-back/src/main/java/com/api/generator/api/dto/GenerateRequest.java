package com.api.generator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * DTO used to request an API generation.
 *
 * The generator-manager remains generic: all database connection details are
 * provided by the front-end for each generation request.
 */
public class GenerateRequest {

    /** Name of the generated Spring Boot application. Optional: falls back to generator defaults. */
    private String appName;

    /** Base Java package for the generated API. Optional: falls back to generator defaults. */
    private String basePackage;

    /** Database type (postgres, mysql, h2, oracle). */
    @NotBlank(message = "databaseType must not be blank")
    private String databaseType;

    /** JDBC URL of the database to introspect. */
    @NotBlank(message = "jdbcUrl must not be blank")
    private String jdbcUrl;

    /** DB username. */
    @NotBlank(message = "jdbcUsername must not be blank")
    private String jdbcUsername;

    /** DB password (optional). */
    private String jdbcPassword;

    /** Database schema name (optional). */
    private String schema;

    /** If true, build the generated project using Maven Wrapper. Default: true. */
    private boolean build = true;

    /** If true, build a Docker image and run a container locally. Default: false. */
    private boolean deployDocker = false;

    /** Preferred host port for docker run (optional). */
    @Min(value = 1, message = "hostPort must be >= 1")
    @Max(value = 65535, message = "hostPort must be <= 65535")
    private Integer hostPort;

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getJdbcUsername() { return jdbcUsername; }
    public void setJdbcUsername(String jdbcUsername) { this.jdbcUsername = jdbcUsername; }

    public String getJdbcPassword() { return jdbcPassword; }
    public void setJdbcPassword(String jdbcPassword) { this.jdbcPassword = jdbcPassword; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public boolean isBuild() { return build; }
    public void setBuild(boolean build) { this.build = build; }

    public boolean isDeployDocker() { return deployDocker; }
    public void setDeployDocker(boolean deployDocker) { this.deployDocker = deployDocker; }

    public Integer getHostPort() { return hostPort; }
    public void setHostPort(Integer hostPort) { this.hostPort = hostPort; }
}
