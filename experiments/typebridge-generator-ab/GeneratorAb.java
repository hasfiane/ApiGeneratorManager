package experiment.ab;

import com.api.generator.generator.GeneratedSource;
import com.api.generator.generator.JpaEntitySourceGenerator;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ForeignKeyInfo;
import com.api.generator.schema.TableInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GeneratorAb {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        Path root = Path.of(args[0]);
        Path baselineDir = root.resolve("baseline-src");
        Path strongDir = root.resolve("strong-src");

        TableInfo customer = new TableInfo("customer");
        customer.getColumns().add(uuid("id", false));
        customer.getColumns().add(varchar("name", false));
        customer.getPrimaryKeys().add("id");

        TableInfo order = new TableInfo("orders");
        order.getColumns().add(uuid("id", false));
        order.getColumns().add(uuid("customer_id", false));
        order.getColumns().add(decimal("total", false));
        order.getPrimaryKeys().add("id");
        order.getForeignKeys().add(new ForeignKeyInfo("customer_id", "customer", "id"));

        List<TableInfo> schema = List.of(customer, order);
        JpaEntitySourceGenerator generator = new JpaEntitySourceGenerator();
        List<GeneratedSource> baseline = generator.generate(schema, "bench");
        List<GeneratedSource> strong = generator.generateStrongIds(schema, "bench");

        write(baselineDir, baseline);
        write(strongDir, strong);

        String baselineCustomer = source(baseline, "bench/entity/Customer.java");
        String strongCustomer = source(strong, "bench/entity/Customer.java");
        require(baselineCustomer.contains("private UUID id;"), "baseline Customer.id must remain UUID");
        require(!baselineCustomer.contains("CustomerId"), "baseline must not contain strong id references");
        require(strongCustomer.contains("private CustomerId id;"), "strong Customer.id must use CustomerId");
        require(strongCustomer.contains("@Convert(converter = CustomerIdJpaConverter.class)"),
                "strong Customer.id must declare its JPA converter");
        require(hasPath(strong, "bench/types/CustomerId.java"), "CustomerId value type missing");
        require(hasPath(strong, "bench/types/CustomerIdJpaConverter.java"), "CustomerId converter missing");
        require(hasPath(strong, "bench/types/OrdersId.java"), "OrdersId value type missing");
        require(hasPath(strong, "bench/types/OrdersIdJpaConverter.java"), "OrdersId converter missing");

        long baselineLoc = baseline.stream().mapToLong(s -> lines(s.sourceCode())).sum();
        long strongLoc = strong.stream().mapToLong(s -> lines(s.sourceCode())).sum();
        System.out.println("METRIC baseline_files=" + baseline.size());
        System.out.println("METRIC strong_files=" + strong.size());
        System.out.println("METRIC baseline_loc=" + baselineLoc);
        System.out.println("METRIC strong_loc=" + strongLoc);
        System.out.println("METRIC added_files=" + (strong.size() - baseline.size()));
        System.out.println("METRIC added_loc=" + (strongLoc - baselineLoc));
        System.out.println("PASS: real JpaEntitySourceGenerator produced baseline and strong-id variants from the same schema");
    }

    private static ColumnInfo uuid(String name, boolean autoIncrement) {
        ColumnInfo c = new ColumnInfo();
        c.setName(name);
        c.setJdbcType("uuid");
        c.setNullable(false);
        c.setAutoIncrement(autoIncrement);
        return c;
    }

    private static ColumnInfo varchar(String name, boolean nullable) {
        ColumnInfo c = new ColumnInfo();
        c.setName(name);
        c.setJdbcType("varchar");
        c.setSize(120);
        c.setNullable(nullable);
        return c;
    }

    private static ColumnInfo decimal(String name, boolean nullable) {
        ColumnInfo c = new ColumnInfo();
        c.setName(name);
        c.setJdbcType("numeric");
        c.setSize(19);
        c.setDecimalDigits(2);
        c.setNullable(nullable);
        return c;
    }

    private static void write(Path root, List<GeneratedSource> sources) throws Exception {
        for (GeneratedSource source : sources) {
            Path file = root.resolve(source.relativePath());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.sourceCode());
        }
    }

    private static String source(List<GeneratedSource> sources, String path) {
        return sources.stream().filter(s -> s.relativePath().equals(path)).findFirst().orElseThrow().sourceCode();
    }

    private static boolean hasPath(List<GeneratedSource> sources, String path) {
        return sources.stream().anyMatch(s -> s.relativePath().equals(path));
    }

    private static long lines(String source) {
        return source.lines().count();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
