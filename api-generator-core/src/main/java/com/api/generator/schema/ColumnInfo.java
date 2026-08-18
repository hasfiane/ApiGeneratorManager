package com.api.generator.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ColumnInfo {
    private String  name;
    private String  jdbcType;
    private int     size;
    private boolean nullable;
    private boolean autoIncrement;
    private String  defaultValue;
    private int     decimalDigits;
    private boolean unique;
    private int     ordinalPosition;
    private String  remarks;
    private boolean array;
    private String  arrayComponentType;
    private boolean json;
    private boolean enumType;
    private String  enumTypeName;
    private String[] enumValues;

    /**
     * Semantic role of this column — set via per-table hints in {@code application.yml}.
     * Defaults to {@link ColumnRole#NONE} (ordinary column, no auto-injection).
     */
    @Builder.Default
    private ColumnRole role = ColumnRole.NONE;
}

