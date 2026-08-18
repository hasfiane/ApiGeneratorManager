package com.api.generator.reader;
import org.springframework.stereotype.Component;
@Component
public class H2SchemaReader extends AbstractJdbcSchemaReader {
    @Override protected String[] tableTypes() { return new String[]{"TABLE"}; }
}

