package com.api.generator.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class IndexInfo {
    private String  indexName;
    private boolean unique;
    @Builder.Default
    private List<String> columns = new ArrayList<>();
    private String  type;
}

