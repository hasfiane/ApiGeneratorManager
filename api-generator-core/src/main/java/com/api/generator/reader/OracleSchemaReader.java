package com.api.generator.reader;
import org.springframework.stereotype.Component;
@Component
public class OracleSchemaReader extends AbstractJdbcSchemaReader {
    @Override protected String[] tableTypes() { return new String[]{"TABLE"}; }
}

