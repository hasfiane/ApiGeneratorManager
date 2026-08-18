package com.api.generator.runtime.config;

import com.api.generator.schema.DatabaseType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "generator.db")
public class RuntimeDbProperties {

    private DatabaseType type = DatabaseType.POSTGRESQL;
    private String url;
    private String username;
    private String password;
    private String schema = "public";
}
