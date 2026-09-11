package com.api.generator.generator;

import com.api.generator.schema.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class JpaEntitySourceGenerator implements EntitySourceGenerator {

    @Override
    public List<GeneratedSource> generate(List<TableInfo> tables, String basePackage) {
        return generateInternal(tables, basePackage, false);
    }

    /**
     * Experimental A/B mode for TypeBridge: keep the current generator untouched by default,
     * but generate semantic value types for eligible single-column primary keys.
     *
     * Each strong id gets an explicit JPA AttributeConverter. TypeBridge is only responsible
     * for source-level raw -> strong adaptation; persistence conversion remains explicit here.
     */
    public List<GeneratedSource> generateStrongIds(List<TableInfo> tables, String basePackage) {
        return generateInternal(tables, basePackage, true);
    }

    private List<GeneratedSource> generateInternal(List<TableInfo> tables, String basePackage, boolean strongIds) {
        Map<String, String> classMap = new LinkedHashMap<>();
        for (TableInfo t : tables) classMap.put(t.getName(), toPascal(t.getName()));

        Map<String, StrongIdDef> strongIdMap = strongIds ? buildStrongIdMap(tables, classMap) : Map.of();
        List<GeneratedSource> result = new ArrayList<>();
        if (strongIds) {
            for (StrongIdDef def : strongIdMap.values()) {
                result.add(buildStrongIdType(def, basePackage));
                result.add(buildStrongIdConverter(def, basePackage));
            }
        }
        for (TableInfo t : tables) result.add(buildEntity(t, basePackage, classMap, strongIdMap));
        return result;
    }

    private Map<String, StrongIdDef> buildStrongIdMap(List<TableInfo> tables, Map<String, String> classMap) {
        Map<String, StrongIdDef> result = new LinkedHashMap<>();
        for (TableInfo table : tables) {
            if (table.getPrimaryKeys().size() != 1) continue;
            ColumnInfo pk = findCol(table, table.getPrimaryKeys().get(0));
            if (pk == null || pk.isAutoIncrement() || pk.isArray() || pk.isJson() || pk.isEnumType()) continue;
            String rawType = javaType(pk);
            if (rawType.equals("Object") || rawType.equals("byte[]") || rawType.startsWith("List<")) continue;
            String entityName = classMap.getOrDefault(table.getName(), toPascal(table.getName()));
            result.put(table.getName(), new StrongIdDef(table.getName(), pk.getName(), entityName + "Id", rawType));
        }
        return result;
    }

    private GeneratedSource buildStrongIdType(StrongIdDef def, String basePackage) {
        String pkg = basePackage + ".types";
        String relPath = pkg.replace('.', '/') + "/" + def.typeName() + ".java";
        Src w = new Src();
        w.ln("package " + pkg + ";"); w.nl();
        w.ln("import dev.typebridge.api.StrongType;");
        rawTypeImport(def.rawType()).ifPresent(i -> w.ln("import " + i + ";"));
        w.nl();
        w.ln("@StrongType");
        w.ln("public record " + def.typeName() + "(" + def.rawType() + " value) {}");
        return new GeneratedSource(relPath, w.toString());
    }

    private GeneratedSource buildStrongIdConverter(StrongIdDef def, String basePackage) {
        String pkg = basePackage + ".types";
        String converter = def.typeName() + "JpaConverter";
        String relPath = pkg.replace('.', '/') + "/" + converter + ".java";
        Src w = new Src();
        w.ln("package " + pkg + ";"); w.nl();
        w.ln("import jakarta.persistence.AttributeConverter;");
        w.ln("import jakarta.persistence.Converter;");
        rawTypeImport(def.rawType()).ifPresent(i -> w.ln("import " + i + ";"));
        w.nl();
        w.ln("@Converter");
        w.ln("public final class " + converter + " implements AttributeConverter<" + def.typeName() + ", " + def.rawType() + "> {");
        w.ln("    @Override");
        w.ln("    public " + def.rawType() + " convertToDatabaseColumn(" + def.typeName() + " value) {");
        w.ln("        return value == null ? null : value.value();");
        w.ln("    }"); w.nl();
        w.ln("    @Override");
        w.ln("    public " + def.typeName() + " convertToEntityAttribute(" + def.rawType() + " value) {");
        w.ln("        return value == null ? null : new " + def.typeName() + "(value);");
        w.ln("    }");
        w.ln("}");
        return new GeneratedSource(relPath, w.toString());
    }

    private Optional<String> rawTypeImport(String rawType) {
        return switch (rawType) {
            case "UUID" -> Optional.of("java.util.UUID");
            case "BigDecimal" -> Optional.of("java.math.BigDecimal");
            case "LocalDate" -> Optional.of("java.time.LocalDate");
            case "LocalTime" -> Optional.of("java.time.LocalTime");
            case "LocalDateTime" -> Optional.of("java.time.LocalDateTime");
            default -> Optional.empty();
        };
    }

    private GeneratedSource buildEntity(TableInfo table, String basePackage, Map<String, String> classMap,
                                        Map<String, StrongIdDef> strongIdMap) {
        String pkg = basePackage + ".entity";
        String className = classMap.get(table.getName());
        String relPath = pkg.replace('.', '/') + "/" + className + ".java";
        StrongIdDef strongId = strongIdMap.get(table.getName());
        Src w = new Src();
        w.ln("package " + pkg + ";"); w.nl();
        buildImports(table, basePackage, strongId).stream().sorted().forEach(i -> w.ln("import " + i + ";"));
        w.nl();
        writeClassAnnotations(w, table);
        w.ln("public class " + className + " {"); w.nl();
        boolean composite = table.getPrimaryKeys().size() > 1;
        if (composite) { writeEmbeddableId(w, table, className); w.nl();
            w.ln("    @EmbeddedId"); w.ln("    private " + className + "Id id;"); w.nl(); }
        Set<String> fkCols = fkColNames(table);
        for (ColumnInfo col : table.getColumns()) {
            if (composite && table.getPrimaryKeys().contains(col.getName())) continue;
            writeField(w, col, table, fkCols, strongId);
        }
        for (ForeignKeyInfo fk : table.getForeignKeys()) {
            String ref = classMap.getOrDefault(fk.getPkTable(), toPascal(fk.getPkTable()));
            w.ln("    @ManyToOne(fetch = FetchType.LAZY)");
            w.ln("    @JoinColumn(name = \"" + fk.getFkColumn() + "\")");
            w.ln("    private " + ref + " " + toCamel(fk.getPkTable()) + ";"); w.nl();
        }
        for (ForeignKeyInfo ref : table.getReferencedBy()) {
            String child = classMap.getOrDefault(ref.getPkTable(), toPascal(ref.getPkTable()));
            w.ln("    @OneToMany(mappedBy = \"" + toCamel(table.getName()) + "\", fetch = FetchType.LAZY)");
            w.ln("    private List<" + child + "> " + toCamel(ref.getPkTable()) + "List = new ArrayList<>();"); w.nl();
        }
        for (ColumnInfo col : table.getColumns())
            if (col.isEnumType() && col.getEnumValues() != null && col.getEnumValues().length > 0)
                { w.ln("    public enum " + toPascal(col.getEnumTypeName() != null ? col.getEnumTypeName() : col.getName())
                    + " { " + String.join(", ", col.getEnumValues()) + " }"); w.nl(); }
        w.ln("}");
        return new GeneratedSource(relPath, w.toString());
    }

    private void writeClassAnnotations(Src w, TableInfo t) {
        t.softDeleteColumn().ifPresent(sdCol -> {
            w.ln("@SQLDelete(sql = \"UPDATE " + t.getName() + " SET " + sdCol.getName() + " = NOW() WHERE id = ?\")");
            w.ln("@Where(clause = \"" + sdCol.getName() + " IS NULL\")");
        });
        boolean hasAudit = t.createdByColumn().isPresent() || t.lastModifiedByColumn().isPresent();
        if (hasAudit) w.ln("@EntityListeners(AuditingEntityListener.class)");
        List<IndexInfo> mu = t.getIndexes().stream().filter(i -> i.isUnique() && i.getColumns().size() > 1).toList();
        if (!mu.isEmpty()) {
            StringBuilder sb = new StringBuilder("@Table(name = \"" + t.getName() + "\", uniqueConstraints = {");
            for (int i = 0; i < mu.size(); i++) {
                String cols = mu.get(i).getColumns().stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + ", " + b).orElse("");
                sb.append("@UniqueConstraint(columnNames = {").append(cols).append("})");
                if (i < mu.size() - 1) sb.append(", ");
            }
            w.ln(sb.append("})").toString());
        } else { w.ln("@Table(name = \"" + t.getName() + "\")"); }
        w.ln("@Entity");
        w.ln("@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder");
    }

    private void writeEmbeddableId(Src w, TableInfo t, String cn) {
        w.ln("    @Embeddable");
        w.ln("    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode");
        w.ln("    public static class " + cn + "Id implements java.io.Serializable {");
        for (String pk : t.getPrimaryKeys()) {
            ColumnInfo col = findCol(t, pk);
            if (col != null) { w.ln("        @Column(name = \"" + col.getName() + "\")");
                w.ln("        private " + javaType(col) + " " + toCamel(col.getName()) + ";"); }
        }
        w.ln("    }");
    }

    private void writeField(Src w, ColumnInfo col, TableInfo t, Set<String> fkCols, StrongIdDef strongId) {
        if (fkCols.contains(col.getName())) return;
        boolean singlePk = t.getPrimaryKeys().size() == 1 && t.getPrimaryKeys().contains(col.getName());
        if (singlePk) {
            w.ln("    @Id");
            if (col.isAutoIncrement()) w.ln("    @GeneratedValue(strategy = GenerationType.IDENTITY)");
            if (strongId != null && strongId.pkColumn().equals(col.getName())) {
                w.ln("    @Convert(converter = " + strongId.typeName() + "JpaConverter.class)");
            }
        }
        w.ln("    @Column(" + colAttrs(col) + ")");
        if (col.getRole() == ColumnRole.CREATED_BY)       w.ln("    @CreatedBy");
        else if (col.getRole() == ColumnRole.LAST_MODIFIED_BY) w.ln("    @LastModifiedBy");
        String fieldType = singlePk && strongId != null && strongId.pkColumn().equals(col.getName())
                ? strongId.typeName() : javaType(col);
        w.ln("    private " + fieldType + " " + toCamel(col.getName()) + ";"); w.nl();
    }

    private String colAttrs(ColumnInfo col) {
        List<String> a = new ArrayList<>();
        a.add("name = \"" + col.getName() + "\"");
        if (!col.isNullable())   a.add("nullable = false");
        if (col.isUnique())      a.add("unique = true");
        if (col.isJson())        a.add("columnDefinition = \"jsonb\"");
        if (col.isArray())       a.add("columnDefinition = \"" + col.getJdbcType() + "\"");
        if (col.getSize() > 0 && isString(col)) a.add("length = " + col.getSize());
        if (col.getDecimalDigits() > 0) { a.add("precision = " + col.getSize()); a.add("scale = " + col.getDecimalDigits()); }
        return String.join(", ", a);
    }

    private Set<String> buildImports(TableInfo t, String basePackage, StrongIdDef strongId) {
        Set<String> i = new TreeSet<>();
        i.addAll(List.of("jakarta.persistence.Entity","jakarta.persistence.Table","jakarta.persistence.Column",
            "jakarta.persistence.Id","jakarta.persistence.GeneratedValue","jakarta.persistence.GenerationType","jakarta.persistence.FetchType",
            "lombok.Getter","lombok.Setter","lombok.NoArgsConstructor","lombok.AllArgsConstructor","lombok.Builder"));
        if (strongId != null) {
            i.add("jakarta.persistence.Convert");
            i.add(basePackage + ".types." + strongId.typeName());
            i.add(basePackage + ".types." + strongId.typeName() + "JpaConverter");
        }
        if (!t.getForeignKeys().isEmpty()) i.addAll(List.of("jakarta.persistence.ManyToOne","jakarta.persistence.JoinColumn"));
        if (!t.getReferencedBy().isEmpty()) i.addAll(List.of("jakarta.persistence.OneToMany","java.util.List","java.util.ArrayList"));
        if (t.getPrimaryKeys().size() > 1) i.addAll(List.of("jakarta.persistence.EmbeddedId","jakarta.persistence.Embeddable","lombok.EqualsAndHashCode"));
        if (t.hasSoftDelete()) i.addAll(List.of("org.hibernate.annotations.SQLDelete","org.hibernate.annotations.Where"));
        boolean hasAudit = t.createdByColumn().isPresent() || t.lastModifiedByColumn().isPresent();
        if (hasAudit) i.addAll(List.of("org.springframework.data.jpa.domain.support.AuditingEntityListener",
            "jakarta.persistence.EntityListeners","org.springframework.data.annotation.CreatedBy","org.springframework.data.annotation.LastModifiedBy"));
        if (t.getIndexes().stream().anyMatch(x -> x.isUnique() && x.getColumns().size() > 1)) i.add("jakarta.persistence.UniqueConstraint");
        for (ColumnInfo col : t.getColumns()) {
            String tp = col.getJdbcType().toLowerCase(Locale.ROOT);
            if (col.isArray()) i.add("java.util.List");
            if (tp.contains("timestamp") || tp.contains("datetime")) i.add("java.time.LocalDateTime");
            else if (tp.contains("date") && !tp.contains("time")) i.add("java.time.LocalDate");
            else if (tp.contains("time") && !tp.contains("stamp") && !tp.contains("date")) i.add("java.time.LocalTime");
            if (tp.contains("uuid")) i.add("java.util.UUID");
            if (tp.contains("numeric") || tp.contains("decimal")) i.add("java.math.BigDecimal");
        }
        return i;
    }

    private String javaType(ColumnInfo col) {
        if (col.isEnumType()) return toPascal(col.getEnumTypeName() != null ? col.getEnumTypeName() : col.getName());
        if (col.isArray())    return "List<" + box(baseType(col.getArrayComponentType())) + ">";
        if (col.isJson())     return "String";
        return baseType(col.getJdbcType());
    }

    private String baseType(String t) {
        if (t == null) return "Object";
        return switch (t.toLowerCase(Locale.ROOT)) {
            case "int2","smallint"                    -> "Short";
            case "int4","int","integer","serial"      -> "Integer";
            case "int8","bigint","bigserial"          -> "Long";
            case "float4","real"                     -> "Float";
            case "float8","double","double precision" -> "Double";
            case "numeric","decimal"                 -> "BigDecimal";
            case "bool","boolean"                    -> "Boolean";
            case "char","bpchar","character"         -> "Character";
            case "varchar","text","character varying","tinytext","mediumtext","longtext","clob","nclob","nvarchar" -> "String";
            case "date"                              -> "LocalDate";
            case "time","timetz"                     -> "LocalTime";
            case "timestamp","timestamptz","datetime","timestamp with time zone","timestamp without time zone" -> "LocalDateTime";
            case "uuid"                              -> "UUID";
            case "json","jsonb"                      -> "String";
            case "bytea","blob","mediumblob","longblob" -> "byte[]";
            default                                  -> "Object";
        };
    }

    private String box(String t) {
        return switch (t) {
            case "int" -> "Integer"; case "long" -> "Long"; case "double" -> "Double";
            case "float" -> "Float"; case "boolean" -> "Boolean"; case "short" -> "Short";
            case "char" -> "Character"; default -> t;
        };
    }

    public static String toPascal(String s) {
        if (s == null || s.isBlank()) return "Unknown";
        StringBuilder sb = new StringBuilder(); boolean up = true;
        for (char c : s.toCharArray()) {
            if (c == '_') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c)); up = false;
        }
        return sb.toString();
    }

    public static String toCamel(String s) {
        String p = toPascal(s);
        return p.isEmpty() ? p : Character.toLowerCase(p.charAt(0)) + p.substring(1);
    }

    private Set<String> fkColNames(TableInfo t) {
        Set<String> s = new HashSet<>();
        for (ForeignKeyInfo fk : t.getForeignKeys()) s.add(fk.getFkColumn());
        return s;
    }

    private ColumnInfo findCol(TableInfo t, String name) {
        return t.getColumns().stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }

    private boolean isString(ColumnInfo col) {
        String tp = col.getJdbcType().toLowerCase(Locale.ROOT);
        return tp.contains("char") || tp.contains("text") || tp.contains("clob");
    }

    private record StrongIdDef(String tableName, String pkColumn, String typeName, String rawType) {}

    private static class Src {
        private final StringBuilder sb = new StringBuilder();
        void ln(String s) { sb.append(s).append('\n'); }
        void nl()         { sb.append('\n'); }
        @Override public String toString() { return sb.toString(); }
    }
}
