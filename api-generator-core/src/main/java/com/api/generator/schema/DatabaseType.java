package com.api.generator.schema;

import lombok.Getter;

@Getter
public enum DatabaseType {
    POSTGRESQL("org.postgresql.Driver"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    ORACLE("oracle.jdbc.OracleDriver"),
    H2("org.h2.Driver");

    private final String driverClass;
    DatabaseType(String d) { this.driverClass = d; }
}

