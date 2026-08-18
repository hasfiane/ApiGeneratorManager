package com.api.generator.reader;

import com.api.generator.schema.TableInfo;
import java.util.List;

public interface SchemaReader {
    List<TableInfo> readSchema(SchemaReadRequest request) throws Exception;
}

