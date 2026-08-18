package com.api.generator.runtime.schema;

import com.api.generator.schema.ColumnInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps SQL type strings from ColumnInfo to Java classes.
 */
public final class SqlTypeMapper {

    private SqlTypeMapper() {}

    public static Class<?> toJavaClass(ColumnInfo col) {
        if (col.isArray()) {
            return String[].class; // arrays stored/returned as String[]
        }
        String raw = normalize(col.getJdbcType());
        if (col.isEnumType()) return String.class;
        return switch (raw) {
            case "int2", "smallint" -> Short.class;
            case "int4", "integer", "int", "serial" -> Integer.class;
            case "int8", "bigint", "bigserial" -> Long.class;
            case "numeric", "decimal" -> BigDecimal.class;
            case "float4", "real" -> Float.class;
            case "float8", "double precision" -> Double.class;
            case "bool", "boolean" -> Boolean.class;
            case "uuid" -> UUID.class;
            case "date" -> LocalDate.class;
            case "timestamp", "timestamp without time zone" -> LocalDateTime.class;
            case "timestamptz", "timestamp with time zone" -> OffsetDateTime.class;
            case "time", "time without time zone" -> LocalTime.class;
            case "timetz", "time with time zone" -> LocalTime.class;
            case "bytea", "blob" -> byte[].class;
            default -> String.class; // varchar, text, json, jsonb, char, etc.
        };
    }

    /**
     * Convert a raw value (typically from JSON/request) to the expected Java type for a column.
     */
    public static Object convertValue(ColumnInfo col, Object raw) {
        if (raw == null) return null;
        Class<?> target = toJavaClass(col);

        if (target.isInstance(raw)) return raw;
        String s = raw.toString();

        if (target == Short.class) return Short.valueOf(s);
        if (target == Integer.class) return Integer.valueOf(s);
        if (target == Long.class) return Long.valueOf(s);
        if (target == BigDecimal.class) return new BigDecimal(s);
        if (target == Float.class) return Float.valueOf(s);
        if (target == Double.class) return Double.valueOf(s);
        if (target == Boolean.class) return Boolean.valueOf(s);
        if (target == UUID.class) return UUID.fromString(s);
        if (target == LocalDate.class) return LocalDate.parse(s);
        if (target == LocalDateTime.class) return LocalDateTime.parse(s);
        if (target == OffsetDateTime.class) return OffsetDateTime.parse(s);
        if (target == LocalTime.class) return LocalTime.parse(s);

        return s;
    }

    private static String normalize(String sqlType) {
        if (sqlType == null) return "";
        String s = sqlType.toLowerCase(Locale.ROOT).trim();
        // strip precision suffix e.g. "varchar(255)" -> "varchar"
        int paren = s.indexOf('(');
        if (paren > 0) s = s.substring(0, paren).trim();
        return s;
    }
}
