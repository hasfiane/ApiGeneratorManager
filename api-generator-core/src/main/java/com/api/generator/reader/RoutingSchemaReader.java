package com.api.generator.reader;

import com.api.generator.schema.TableInfo;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.List;

@Component @Primary
public class RoutingSchemaReader implements SchemaReader {

    private final H2SchemaReader h2;
    private final MySqlSchemaReader mysql;
    private final OracleSchemaReader oracle;
    private final PostgresSchemaReader postgres;

    public RoutingSchemaReader(H2SchemaReader h2, MySqlSchemaReader mysql,
                               OracleSchemaReader oracle, PostgresSchemaReader postgres) {
        this.h2 = h2; this.mysql = mysql; this.oracle = oracle; this.postgres = postgres;
    }

    @Override
    public List<TableInfo> readSchema(SchemaReadRequest req) throws Exception {
        if (req.type() == null) throw new IllegalArgumentException("DatabaseType required");
        return switch (req.type()) {
            case H2         -> h2.readSchema(req);
            case MYSQL      -> mysql.readSchema(req);
            case ORACLE     -> oracle.readSchema(req);
            case POSTGRESQL -> postgres.readSchema(req);
        };
    }
}

