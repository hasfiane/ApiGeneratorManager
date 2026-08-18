package com.api.generator.schema;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.*;

@Data @NoArgsConstructor
public class TableInfo {
    private String name;
    private List<ColumnInfo>     columns      = new ArrayList<>();
    private List<String>         primaryKeys  = new ArrayList<>();
    private List<ForeignKeyInfo> foreignKeys  = new ArrayList<>();
    private List<ForeignKeyInfo> referencedBy = new ArrayList<>();
    private List<IndexInfo>      indexes      = new ArrayList<>();
    private Set<String>          uniqueColumns = new HashSet<>();
    private String  remarks;

    public TableInfo(String name) { this.name = name; }

    // ── Role-based helpers ────────────────────────────────────────────────────
    // These replace the old boolean flags and named column strings.
    // Any column with the matching ColumnRole is the "special" column —
    // but it is still a plain column in SQL; the role only drives auto-injection.

    /** Returns the soft-delete column, or empty if none configured. */
    public Optional<ColumnInfo> softDeleteColumn() {
        return columnWithRole(ColumnRole.SOFT_DELETE);
    }

    /** Returns the createdBy column, or empty if none configured. */
    public Optional<ColumnInfo> createdByColumn() {
        return columnWithRole(ColumnRole.CREATED_BY);
    }

    /** Returns the lastModifiedBy column, or empty if none configured. */
    public Optional<ColumnInfo> lastModifiedByColumn() {
        return columnWithRole(ColumnRole.LAST_MODIFIED_BY);
    }

    /** True if any column carries the SOFT_DELETE role. */
    public boolean hasSoftDelete() {
        return softDeleteColumn().isPresent();
    }

    private Optional<ColumnInfo> columnWithRole(ColumnRole role) {
        return columns.stream()
                .filter(c -> c.getRole() == role)
                .findFirst();
    }
}
