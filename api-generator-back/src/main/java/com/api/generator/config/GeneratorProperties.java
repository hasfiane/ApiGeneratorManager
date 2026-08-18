package com.api.generator.config;

import com.api.generator.schema.DatabaseType;
import com.api.generator.schema.TableInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "generator")
public class GeneratorProperties {

    @NotBlank
    private String outputDir = "./out/generated-api";

    @NotBlank
    private String basePackage = "com.example.generated";

    @NotBlank
    private String appName = "GeneratedApi";

    private boolean cleanOutputDir = true;
    private final Db db = new Db();
    private final Security security = new Security();
    private final Maven maven = new Maven();
    private final Features features = new Features();
    private Map<String, TableHint> tables = new LinkedHashMap<>();
    private List<TableInfo> schemaTables = new ArrayList<>();

    @Getter
    @Setter
    public static class Db {
        private DatabaseType type = DatabaseType.POSTGRESQL;
        private String url;
        private String username;
        private String password;
        private String schema;
        private Map<String, String> properties = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Security {
        private boolean enabled = true;
        private String bootstrapUsername = "admin";
        private String bootstrapPassword = "";
        private String jwtSecret = "";
        private long jwtExpirationSeconds = 3600;
        private String jwtIssuer = "generated-api";
    }

    @Getter
    @Setter
    public static class Maven {
        private String groupId = "com.generated";
        private String artifactId = "generated-api";
        private String version = "0.0.1-SNAPSHOT";
    }

    @Getter
    @Setter
    public static class Features {
        private boolean generateOpenApi = true;
        private boolean generateDocker = true;
        private boolean generateClientSdkDocs = true;
    }

    @Getter
    @Setter
    public static class TableHint {
        private String softDeleteColumn;
        private String createdByColumn;
        private String lastModifiedByColumn;
        private List<String> jsonColumns = new ArrayList<>();
        private List<String> arrayColumns = new ArrayList<>();
    }
}
