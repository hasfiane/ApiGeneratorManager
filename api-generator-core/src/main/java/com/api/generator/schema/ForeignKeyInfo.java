package com.api.generator.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ForeignKeyInfo {
    private String fkColumn;
    private String pkTable;
    private String pkColumn;
}

