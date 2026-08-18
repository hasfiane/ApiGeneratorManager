package com.api.generator.runtime.schema;

import com.api.generator.schema.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class SqlTypeMapperTest {

    @Test
    void mapsCommonSqlTypesToJavaClasses() {
        assertEquals(Integer.class, SqlTypeMapper.toJavaClass(column("integer")));
        assertEquals(Long.class, SqlTypeMapper.toJavaClass(column("bigint")));
        assertEquals(BigDecimal.class, SqlTypeMapper.toJavaClass(column("numeric(10,2)")));
        assertEquals(Boolean.class, SqlTypeMapper.toJavaClass(column("boolean")));
        assertEquals(UUID.class, SqlTypeMapper.toJavaClass(column("uuid")));
        assertEquals(LocalDate.class, SqlTypeMapper.toJavaClass(column("date")));
        assertEquals(LocalDateTime.class, SqlTypeMapper.toJavaClass(column("timestamp")));
        assertEquals(OffsetDateTime.class, SqlTypeMapper.toJavaClass(column("timestamp with time zone")));
        assertEquals(LocalTime.class, SqlTypeMapper.toJavaClass(column("time")));
        assertEquals(byte[].class, SqlTypeMapper.toJavaClass(column("blob")));
        assertEquals(String.class, SqlTypeMapper.toJavaClass(column("varchar(255)")));
    }

    @Test
    void convertsRawJsonValuesToColumnTypes() {
        assertEquals(42, SqlTypeMapper.convertValue(column("integer"), "42"));
        assertEquals(42L, SqlTypeMapper.convertValue(column("bigint"), "42"));
        assertEquals(new BigDecimal("19.95"), SqlTypeMapper.convertValue(column("numeric"), "19.95"));
        assertEquals(true, SqlTypeMapper.convertValue(column("boolean"), "true"));
        assertInstanceOf(UUID.class, SqlTypeMapper.convertValue(column("uuid"), "550e8400-e29b-41d4-a716-446655440000"));
        assertEquals(LocalDate.parse("2026-04-15"), SqlTypeMapper.convertValue(column("date"), "2026-04-15"));
        assertEquals(LocalDateTime.parse("2026-04-15T10:15:30"), SqlTypeMapper.convertValue(column("timestamp"), "2026-04-15T10:15:30"));
        assertEquals(LocalTime.parse("10:15:30"), SqlTypeMapper.convertValue(column("time"), "10:15:30"));
    }

    @Test
    void returnsExistingInstanceAndNullWithoutConversion() {
        Integer value = 7;
        assertSame(value, SqlTypeMapper.convertValue(column("integer"), value));
        assertEquals(null, SqlTypeMapper.convertValue(column("integer"), null));
    }

    private static ColumnInfo column(String type) {
        return ColumnInfo.builder().name("value").jdbcType(type).build();
    }
}
