package com.api.generator.api.service;

import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.DatabaseType;
import com.api.generator.schema.ForeignKeyInfo;
import com.api.generator.schema.IndexInfo;
import com.api.generator.schema.TableInfo;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class YamlSchemaSourceReader {

    public static final long MAX_FILE_SIZE_BYTES = 512 * 1024;
    private static final int MAX_TABLES = 100;
    private static final int MAX_COLUMNS_PER_TABLE = 200;
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$");
    private static final Pattern APP_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,31}$");
    private static final Pattern PACKAGE_NAME = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "uuid", "varchar", "text", "int", "integer", "bigint", "decimal", "numeric",
            "boolean", "date", "timestamp", "datetime"
    );

    private final ObjectMapper mapper = new ObjectMapper(
            YAMLFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
    );

    public NormalizedYamlSchema read(MultipartFile file) {
        validateFile(file);
        JsonNode root = parse(file);
        String appName = optionalText(root.path("app"), "name");
        String packageName = optionalText(root.path("app"), "packageName");
        String dbType = optionalText(root.path("database"), "type");
        String schema = optionalText(root.path("database"), "schema");

        validateOptionalAppName(appName);
        validateOptionalPackageName(packageName);
        validateOptionalSchema(schema);

        DatabaseType databaseType = parseDatabaseType(dbType);
        List<TableInfo> tables = parseTables(root.path("tables"));
        return new NormalizedYamlSchema(appName, packageName, databaseType, schema, tables);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            badRequest("YAML_SCHEMA_EMPTY");
        }
        String name = file.getOriginalFilename();
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".yaml") && !lowerName.endsWith(".yml")) {
            badRequest("YAML_SCHEMA_INVALID_EXTENSION");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            badRequest("YAML_SCHEMA_TOO_LARGE");
        }
    }

    private JsonNode parse(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || new String(bytes, java.nio.charset.StandardCharsets.UTF_8).isBlank()) {
                badRequest("YAML_SCHEMA_EMPTY");
            }
            return mapper.readTree(bytes);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            badRequest("YAML_SCHEMA_INVALID");
            return null;
        }
    }

    private List<TableInfo> parseTables(JsonNode tablesNode) {
        if (!tablesNode.isObject() || tablesNode.isEmpty()) {
            badRequest("YAML_SCHEMA_NO_TABLES");
        }
        if (tablesNode.size() > MAX_TABLES) {
            badRequest("YAML_SCHEMA_TOO_MANY_TABLES");
        }

        Map<String, TableInfo> tables = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> tableFields = tablesNode.fields();
        while (tableFields.hasNext()) {
            Map.Entry<String, JsonNode> tableEntry = tableFields.next();
            String tableName = tableEntry.getKey();
            validateSqlIdentifier(tableName, "YAML_SCHEMA_INVALID_TABLE_NAME");
            TableInfo table = new TableInfo(tableName);
            parseColumns(table, tableEntry.getValue().path("columns"));
            tables.put(tableName, table);
        }

        validateForeignKeys(tables);
        buildReverseRelations(tables);
        return new ArrayList<>(tables.values());
    }

    private void parseColumns(TableInfo table, JsonNode columnsNode) {
        if (!columnsNode.isObject() || columnsNode.isEmpty()) {
            badRequest("YAML_SCHEMA_TABLE_WITHOUT_COLUMNS");
        }
        if (columnsNode.size() > MAX_COLUMNS_PER_TABLE) {
            badRequest("YAML_SCHEMA_TOO_MANY_COLUMNS");
        }

        int ordinal = 1;
        Iterator<Map.Entry<String, JsonNode>> columnFields = columnsNode.fields();
        while (columnFields.hasNext()) {
            Map.Entry<String, JsonNode> columnEntry = columnFields.next();
            String columnName = columnEntry.getKey();
            JsonNode columnNode = columnEntry.getValue();
            validateSqlIdentifier(columnName, "YAML_SCHEMA_INVALID_COLUMN_NAME");

            String rawType = optionalText(columnNode, "type");
            if (rawType == null || rawType.isBlank()) {
                badRequest("YAML_SCHEMA_COLUMN_TYPE_MISSING");
            }
            String jdbcType = mapType(rawType);
            ColumnInfo column = ColumnInfo.builder()
                    .name(columnName)
                    .jdbcType(jdbcType)
                    .nullable(optionalBoolean(columnNode, "nullable", true))
                    .size(optionalInt(columnNode, "length", optionalInt(columnNode, "precision", 0)))
                    .decimalDigits(optionalInt(columnNode, "scale", 0))
                    .unique(optionalBoolean(columnNode, "unique", false))
                    .ordinalPosition(ordinal++)
                    .build();
            table.getColumns().add(column);

            if (optionalBoolean(columnNode, "primaryKey", false)) {
                table.getPrimaryKeys().add(columnName);
            }
            if (column.isUnique()) {
                table.getUniqueColumns().add(columnName);
                table.getIndexes().add(IndexInfo.builder()
                        .indexName("uk_" + table.getName() + "_" + columnName)
                        .unique(true)
                        .type("BTREE")
                        .columns(new ArrayList<>(List.of(columnName)))
                        .build());
            }

            JsonNode reference = columnNode.path("references");
            if (reference.isObject()) {
                String refTable = optionalText(reference, "table");
                String refColumn = optionalText(reference, "column");
                if (refTable != null && !refTable.isBlank() && refColumn != null && !refColumn.isBlank()) {
                    validateSqlIdentifier(refTable, "YAML_SCHEMA_INVALID_TABLE_NAME");
                    validateSqlIdentifier(refColumn, "YAML_SCHEMA_INVALID_COLUMN_NAME");
                    table.getForeignKeys().add(new ForeignKeyInfo(columnName, refTable, refColumn));
                }
            }
        }
    }

    private void validateForeignKeys(Map<String, TableInfo> tables) {
        for (TableInfo table : tables.values()) {
            for (ForeignKeyInfo fk : table.getForeignKeys()) {
                TableInfo target = tables.get(fk.getPkTable());
                if (target == null) {
                    badRequest("YAML_SCHEMA_UNKNOWN_FOREIGN_KEY");
                }
                boolean columnExists = target.getColumns().stream()
                        .anyMatch(column -> column.getName().equals(fk.getPkColumn()));
                if (!columnExists) {
                    badRequest("YAML_SCHEMA_UNKNOWN_FOREIGN_KEY");
                }
            }
        }
    }

    private void buildReverseRelations(Map<String, TableInfo> tables) {
        for (TableInfo table : tables.values()) {
            for (ForeignKeyInfo fk : table.getForeignKeys()) {
                TableInfo referenced = tables.get(fk.getPkTable());
                if (referenced != null) {
                    referenced.getReferencedBy().add(new ForeignKeyInfo(fk.getPkColumn(), table.getName(), fk.getFkColumn()));
                }
            }
        }
    }

    private String mapType(String rawType) {
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            badRequest("YAML_SCHEMA_INVALID");
        }
        return switch (normalized) {
            case "int" -> "integer";
            default -> normalized;
        };
    }

    private DatabaseType parseDatabaseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return DatabaseType.POSTGRESQL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("post")) return DatabaseType.POSTGRESQL;
        if (normalized.contains("mysql")) return DatabaseType.MYSQL;
        if (normalized.equals("h2")) return DatabaseType.H2;
        if (normalized.contains("oracle")) return DatabaseType.ORACLE;
        badRequest("YAML_SCHEMA_INVALID");
        return DatabaseType.POSTGRESQL;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText().trim();
    }

    private boolean optionalBoolean(JsonNode node, String field, boolean fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asBoolean(fallback);
    }

    private int optionalInt(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        int parsed = value.asInt(fallback);
        return Math.max(0, parsed);
    }

    private void validateSqlIdentifier(String value, String code) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            badRequest(code);
        }
    }

    private void validateOptionalSchema(String value) {
        if (value != null && !value.isBlank()) {
            validateSqlIdentifier(value, "YAML_SCHEMA_INVALID");
        }
    }

    private void validateOptionalAppName(String value) {
        if (value != null && !value.isBlank() && !APP_NAME.matcher(value).matches()) {
            badRequest("YAML_SCHEMA_INVALID_APP_NAME");
        }
    }

    private void validateOptionalPackageName(String value) {
        if (value != null && !value.isBlank() && (!PACKAGE_NAME.matcher(value).matches() || value.length() > 200)) {
            badRequest("YAML_SCHEMA_INVALID_PACKAGE_NAME");
        }
    }

    private void badRequest(String code) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }

    public record NormalizedYamlSchema(
            String appName,
            String packageName,
            DatabaseType databaseType,
            String schema,
            List<TableInfo> tables
    ) {
    }
}
