package com.api.generator.generator;

import com.api.generator.schema.TableInfo;
import java.util.List;

public interface EntitySourceGenerator {
    List<GeneratedSource> generate(List<TableInfo> tables, String basePackage);
}

