package com.api.generator.runtime.web;

import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.TableInfo;

import com.api.generator.runtime.schema.ManifestRegistry;
import com.api.generator.runtime.schema.Operation;
import com.api.generator.runtime.schema.SchemaRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.Locale;
import java.util.Set;

/**
 * Dynamically registers OpenAPI paths for every table exposed by the manifest.
 * Always active — reads from in-memory registries populated at startup
 * via {@code @PostConstruct} before Tomcat accepts connections.
 */
public class DynamicOpenApiContributor implements OpenApiCustomizer {

    private final SchemaRegistry schemaRegistry;
    private final ManifestRegistry manifestRegistry;

    public DynamicOpenApiContributor(SchemaRegistry schemaRegistry, ManifestRegistry manifestRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.manifestRegistry = manifestRegistry;
    }

    @Override
    public void customise(OpenAPI openApi) {
        for (TableInfo table : schemaRegistry.getAllTables()) {
            addTablePaths(openApi, table);
        }
    }

    private void addTablePaths(OpenAPI openApi, TableInfo table) {
        String tableName = table.getName();
        String schemaName = capitalize(tableName);
        Set<Operation> ops = manifestRegistry.operationsFor(tableName);

        openApi.schema(schemaName, buildSchema(table));

        PathItem collection = new PathItem();
        PathItem item      = new PathItem();

        if (ops.contains(Operation.LIST))       collection.setGet(listOp(tableName));
        if (ops.contains(Operation.CREATE))     collection.setPost(createOp(tableName, schemaName));
        if (ops.contains(Operation.GET_BY_ID))  item.setGet(getOp(tableName, schemaName));
        if (ops.contains(Operation.UPDATE))     item.setPut(updateOp(tableName, schemaName));
        if (ops.contains(Operation.DELETE))     item.setDelete(deleteOp(tableName));

        if (hasOps(collection)) openApi.path("/api/" + tableName, collection);
        if (hasOps(item))       openApi.path("/api/" + tableName + "/{id}", item);
    }

    // ── Operation builders ────────────────────────────────────────────────────

    private io.swagger.v3.oas.models.Operation listOp(String table) {
        return new io.swagger.v3.oas.models.Operation()
                .addTagsItem(table)
                .summary("List " + table)
                .operationId("list_" + table)
                .addParametersItem(queryParam("page", "Page number (0-based)"))
                .addParametersItem(queryParam("size", "Page size (default 20)"))
                .addParametersItem(queryParam("sort", "Column to sort by; prefix with '-' for DESC"))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("OK")));
    }

    private io.swagger.v3.oas.models.Operation getOp(String table, String schemaName) {
        return new io.swagger.v3.oas.models.Operation()
                .addTagsItem(table)
                .summary("Get " + table + " by ID")
                .operationId("get_" + table)
                .addParametersItem(idPathParam(table))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("OK").content(jsonRef(schemaName)))
                        .addApiResponse("404", new ApiResponse().description("Not Found")));
    }

    private io.swagger.v3.oas.models.Operation createOp(String table, String schemaName) {
        return new io.swagger.v3.oas.models.Operation()
                .addTagsItem(table)
                .summary("Create " + table)
                .operationId("create_" + table)
                .requestBody(jsonBody(schemaName))
                .responses(new ApiResponses()
                        .addApiResponse("201", new ApiResponse().description("Created").content(jsonRef(schemaName)))
                        .addApiResponse("400", new ApiResponse().description("Validation Error")));
    }

    private io.swagger.v3.oas.models.Operation updateOp(String table, String schemaName) {
        return new io.swagger.v3.oas.models.Operation()
                .addTagsItem(table)
                .summary("Update " + table)
                .operationId("update_" + table)
                .addParametersItem(idPathParam(table))
                .requestBody(jsonBody(schemaName))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("OK").content(jsonRef(schemaName)))
                        .addApiResponse("404", new ApiResponse().description("Not Found")));
    }

    private io.swagger.v3.oas.models.Operation deleteOp(String table) {
        return new io.swagger.v3.oas.models.Operation()
                .addTagsItem(table)
                .summary("Delete " + table)
                .operationId("delete_" + table)
                .addParametersItem(idPathParam(table))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Deleted"))
                        .addApiResponse("404", new ApiResponse().description("Not Found")));
    }

    // ── Schema builder ────────────────────────────────────────────────────────

    private Schema<?> buildSchema(TableInfo table) {
        ObjectSchema schema = new ObjectSchema();
        schema.description("Schema for table " + table.getName());
        for (ColumnInfo col : table.getColumns()) {
            schema.addProperty(col.getName(), columnSchema(col));
        }
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private Schema columnSchema(ColumnInfo col) {
        String type = col.getJdbcType() == null ? "" : col.getJdbcType().toLowerCase(Locale.ROOT);

        if (col.isEnumType()) {
            StringSchema s = new StringSchema();
            if (col.getEnumValues() != null) {
                for (String v : col.getEnumValues()) s.addEnumItemObject(v);
            }
            return s;
        }
        if (col.isArray())                                                  return new ArraySchema().items(new StringSchema());
        if (type.contains("int2") || type.contains("smallint"))            return new IntegerSchema();
        if (type.contains("int8") || type.contains("bigint") || type.contains("bigserial"))
                                                                            return new IntegerSchema().format("int64");
        if (type.contains("int") || type.contains("serial"))               return new IntegerSchema();
        if (type.contains("numeric") || type.contains("decimal")
                || type.contains("float") || type.contains("double"))      return new NumberSchema();
        if (type.contains("bool"))                                          return new BooleanSchema();
        if (type.contains("uuid"))                                          return new UUIDSchema();
        if (type.contains("date") && !type.contains("timestamp"))          return new DateSchema();
        if (type.contains("timestamp"))                                     return new DateTimeSchema();
        if (type.contains("json"))                                          return new ObjectSchema();
        if (type.contains("bytea") || type.contains("blob"))               return new BinarySchema();
        return new StringSchema();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Parameter queryParam(String name, String description) {
        return new Parameter().in("query").name(name).description(description).schema(new StringSchema());
    }

    private Parameter idPathParam(String table) {
        String pks = schemaRegistry.getTable(table)
                .map(t -> String.join(", ", t.getPrimaryKeys()))
                .orElse("id");
        return new Parameter()
                .in("path")
                .name("id")
                .required(true)
                .description("Primary key (" + pks + "). Composite PKs: separate values with ','")
                .schema(new StringSchema());
    }

    private Content jsonRef(String schemaName) {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + schemaName)));
    }

    private RequestBody jsonBody(String schemaName) {
        return new RequestBody().required(true).content(jsonRef(schemaName));
    }

    private boolean hasOps(PathItem item) {
        return item.getGet() != null || item.getPost() != null
                || item.getPut() != null || item.getDelete() != null
                || item.getPatch() != null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
