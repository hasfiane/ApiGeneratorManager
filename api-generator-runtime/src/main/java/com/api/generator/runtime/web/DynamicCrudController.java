package com.api.generator.runtime.web;

import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.TableInfo;
import com.api.generator.runtime.error.NotFoundException;
import com.api.generator.runtime.error.ValidationError;
import com.api.generator.runtime.jdbc.DynamicRepository;
import com.api.generator.runtime.schema.ManifestRegistry;
import com.api.generator.runtime.schema.Operation;
import com.api.generator.runtime.schema.SchemaRegistry;
import com.api.generator.runtime.validation.SchemaValidator;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Single dynamic controller handling all generated CRUD endpoints.
 * Marked {@link Hidden} so SpringDoc ignores it — paths are documented by
 * {@link DynamicOpenApiContributor} which honours the {@link ManifestRegistry}.
 */
@Hidden
@RestController
@RequestMapping("/api")
public class DynamicCrudController {

    private final DynamicRepository repository;
    private final SchemaRegistry schemaRegistry;
    private final SchemaValidator validator;
    private final ManifestRegistry manifestRegistry;

    private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort");

    public DynamicCrudController(DynamicRepository repository,
                                 SchemaRegistry schemaRegistry,
                                 SchemaValidator validator,
                                 ManifestRegistry manifestRegistry) {
        this.repository = repository;
        this.schemaRegistry = schemaRegistry;
        this.validator = validator;
        this.manifestRegistry = manifestRegistry;
    }

    @GetMapping("/{table}")
    public ResponseEntity<?> list(
            @PathVariable String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest request) {

        if (manifestRegistry.isDenied(table, Operation.LIST)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        requireTable(table);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);

        String sortCol = null;
        String sortDir = "ASC";
        if (sort != null && !sort.isBlank()) {
            if (sort.startsWith("-")) {
                sortDir = "DESC";
                sortCol = sort.substring(1);
            } else {
                sortCol = sort;
            }
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (!RESERVED_PARAMS.contains(entry.getKey()) && entry.getValue().length > 0) {
                filters.put(entry.getKey(), entry.getValue()[0]);
            }
        }

        List<Map<String, Object>> content = repository.findAll(table, safePage * safeSize, safeSize, sortCol, sortDir, filters);
        long total = repository.count(table, filters);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return ResponseEntity.ok(new PageResponse(content, safePage, safeSize, total, totalPages));
    }

    @GetMapping("/{table}/{id}")
    public ResponseEntity<?> getById(@PathVariable String table, @PathVariable String id) {
        if (manifestRegistry.isDenied(table, Operation.GET_BY_ID)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        requireTable(table);
        Map<String, Object> pkValues = parsePkValues(table, id);
        return ResponseEntity.ok(repository.findById(table, pkValues));
    }

    @PostMapping("/{table}")
    public ResponseEntity<?> create(@PathVariable String table,
                                    @RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        if (manifestRegistry.isDenied(table, Operation.CREATE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        TableInfo info = requireTable(table);
        // Inject auto-managed columns BEFORE validation so they pass "required" checks
        autoSetAuditCreate(info, body);

        List<ValidationError.FieldError> errors = validator.validateInsert(table, body);
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(new ValidationError(
                    Instant.now(), 400, "Validation Failed",
                    "Input validation failed for " + errors.size() + " field(s)",
                    request.getRequestURI(), errors));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.insert(table, body));
    }

    @PutMapping("/{table}/{id}")
    public ResponseEntity<?> update(@PathVariable String table,
                                    @PathVariable String id,
                                    @RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        if (manifestRegistry.isDenied(table, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        TableInfo info = requireTable(table);
        Map<String, Object> pkValues = parsePkValues(table, id);
        // Inject auto-managed columns BEFORE validation
        autoSetAuditUpdate(info, body);

        List<ValidationError.FieldError> errors = validator.validateUpdate(table, body);
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(new ValidationError(
                    Instant.now(), 400, "Validation Failed",
                    "Input validation failed for " + errors.size() + " field(s)",
                    request.getRequestURI(), errors));
        }
        return ResponseEntity.ok(repository.update(table, pkValues, body));
    }

    @DeleteMapping("/{table}/{id}")
    public ResponseEntity<Void> delete(@PathVariable String table, @PathVariable String id) {
        if (manifestRegistry.isDenied(table, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        requireTable(table);
        repository.delete(table, parsePkValues(table, id));
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TableInfo requireTable(String table) {
        return schemaRegistry.getTable(table)
                .orElseThrow(() -> new NotFoundException("Table '" + table + "' not found"));
    }

    private Map<String, Object> parsePkValues(String table, String idStr) {
        TableInfo info = requireTable(table);
        List<String> pks = info.getPrimaryKeys();
        String[] parts = idStr.split(",");

        if (parts.length != pks.size()) {
            throw new IllegalArgumentException(
                    "Expected " + pks.size() + " primary key value(s) for table '" + table +
                    "' (" + String.join(",", pks) + "), got " + parts.length);
        }

        Map<String, Object> pkValues = new LinkedHashMap<>();
        for (int i = 0; i < pks.size(); i++) {
            pkValues.put(pks.get(i), parts[i].trim());
        }
        return pkValues;
    }

    /**
     * Injects all auto-managed columns before INSERT:
     * - Timestamp columns (created_at, updated_at) → current time
     * - createdByColumn defined in hints → current authenticated user
     * - lastModifiedByColumn defined in hints → current authenticated user
     *
     * These columns are treated as plain columns in SQL — no special handling needed
     * in SqlBuilder. They are injected into the body Map so the standard INSERT path picks them up.
     */
    private void autoSetAuditCreate(TableInfo info, Map<String, Object> body) {
        LocalDateTime now = LocalDateTime.now();
        String actor = resolveCurrentUser();
        for (ColumnInfo col : info.getColumns()) {
            switch (col.getRole() == null ? com.api.generator.schema.ColumnRole.NONE : col.getRole()) {
                case CREATED_AT, UPDATED_AT -> body.putIfAbsent(col.getName(), now);
                case CREATED_BY, LAST_MODIFIED_BY -> body.putIfAbsent(col.getName(), actor);
                default -> {
                    // Fallback nom de colonne pour les colonnes sans rôle explicite (rétro-compatibilité)
                    String n = col.getName().toLowerCase(Locale.ROOT);
                    if (n.equals("created_at") || n.equals("createdat")
                            || n.equals("updated_at") || n.equals("updatedat")) {
                        body.putIfAbsent(col.getName(), now);
                    }
                }
            }
        }
    }

    /**
     * Injects all auto-managed columns before UPDATE:
     * - Timestamp columns (updated_at) → current time
     * - lastModifiedByColumn defined in hints → current authenticated user
     */
    private void autoSetAuditUpdate(TableInfo info, Map<String, Object> body) {
        LocalDateTime now = LocalDateTime.now();
        String actor = resolveCurrentUser();
        for (ColumnInfo col : info.getColumns()) {
            switch (col.getRole() == null ? com.api.generator.schema.ColumnRole.NONE : col.getRole()) {
                case UPDATED_AT -> body.put(col.getName(), now);
                case LAST_MODIFIED_BY -> body.put(col.getName(), actor);
                default -> {
                    // Fallback nom de colonne pour les colonnes sans rôle explicite (rétro-compatibilité)
                    String n = col.getName().toLowerCase(Locale.ROOT);
                    if (n.equals("updated_at") || n.equals("updatedat")) {
                        body.put(col.getName(), now);
                    }
                }
            }
        }
    }

    /**
     * Resolves the authenticated username from the Spring Security context,
     * or "system" when no authentication is present.
     */
    private String resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }
}
