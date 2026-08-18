package com.api.generator.runtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "generator")
public class RuntimeTableProperties {

    private Map<String, TableHint> tables = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class TableHint {
        private String softDeleteColumn;
        private String createdByColumn;
        private String lastModifiedByColumn;
        private String createdAtColumn;
        private String updatedAtColumn;
        private List<String> jsonColumns = new ArrayList<>();
        private List<String> arrayColumns = new ArrayList<>();
    }
}
