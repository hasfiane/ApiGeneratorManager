package com.api.generator.generator;

import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ForeignKeyInfo;
import com.api.generator.schema.TableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JpaEntitySourceGeneratorStrongIdsTest {

    private final JpaEntitySourceGenerator generator = new JpaEntitySourceGenerator();

    @Test
    void baselineKeepsRawPrimaryKeyTypes() {
        Map<String, String> files = byPath(generator.generate(schema(), "demo.api"));

        String customer = files.get("demo/api/entity/Customer.java");
        String order = files.get("demo/api/entity/Order.java");

        assertNotNull(customer);
        assertNotNull(order);
        assertTrue(customer.contains("private UUID id;"));
        assertTrue(order.contains("private UUID id;"));
        assertFalse(files.keySet().stream().anyMatch(path -> path.contains("/types/")));
    }

    @Test
    void strongModeGeneratesSemanticIdsAndExplicitJpaConverters() {
        Map<String, String> files = byPath(generator.generateStrongIds(schema(), "demo.api"));

        assertEquals(6, files.size());

        String customerId = files.get("demo/api/types/CustomerId.java");
        String orderId = files.get("demo/api/types/OrderId.java");
        String customerConverter = files.get("demo/api/types/CustomerIdJpaConverter.java");
        String customer = files.get("demo/api/entity/Customer.java");
        String order = files.get("demo/api/entity/Order.java");

        assertNotNull(customerId);
        assertNotNull(orderId);
        assertNotNull(customerConverter);
        assertTrue(customerId.contains("@StrongType"));
        assertTrue(customerId.contains("record CustomerId(UUID value)"));
        assertTrue(orderId.contains("record OrderId(UUID value)"));

        assertTrue(customerConverter.contains("AttributeConverter<CustomerId, UUID>"));
        assertTrue(customerConverter.contains("value.value()"));
        assertTrue(customerConverter.contains("new CustomerId(value)"));

        assertTrue(customer.contains("private CustomerId id;"));
        assertTrue(customer.contains("@Convert(converter = CustomerIdJpaConverter.class)"));
        assertTrue(order.contains("private OrderId id;"));
        assertTrue(order.contains("@Convert(converter = OrderIdJpaConverter.class)"));
    }

    @Test
    void foreignKeyRelationshipSemanticsStayUnchanged() {
        Map<String, String> files = byPath(generator.generateStrongIds(schema(), "demo.api"));
        String order = files.get("demo/api/entity/Order.java");

        // The current generator models FKs as object relationships, not duplicate scalar id fields.
        assertTrue(order.contains("@ManyToOne(fetch = FetchType.LAZY)"));
        assertTrue(order.contains("@JoinColumn(name = \"customer_id\")"));
        assertTrue(order.contains("private Customer customer;"));
        assertFalse(order.contains("private CustomerId customerId;"));
        assertFalse(order.contains("private UUID customerId;"));
    }

    @Test
    void autoIncrementPrimaryKeyIsNotWrapped() {
        TableInfo users = new TableInfo("users");
        users.getColumns().add(ColumnInfo.builder()
                .name("id").jdbcType("bigint").autoIncrement(true).nullable(false).build());
        users.getPrimaryKeys().add("id");

        Map<String, String> files = byPath(generator.generateStrongIds(List.of(users), "demo.api"));

        assertEquals(1, files.size());
        assertTrue(files.get("demo/api/entity/Users.java").contains("private Long id;"));
        assertFalse(files.keySet().stream().anyMatch(path -> path.contains("UsersId")));
    }

    private static List<TableInfo> schema() {
        TableInfo customer = new TableInfo("customer");
        customer.getColumns().add(uuid("id", false));
        customer.getColumns().add(ColumnInfo.builder()
                .name("name").jdbcType("varchar").size(255).nullable(false).build());
        customer.getPrimaryKeys().add("id");

        TableInfo order = new TableInfo("order");
        order.getColumns().add(uuid("id", false));
        order.getColumns().add(uuid("customer_id", false));
        order.getPrimaryKeys().add("id");
        order.getForeignKeys().add(new ForeignKeyInfo("customer_id", "customer", "id"));

        return List.of(customer, order);
    }

    private static ColumnInfo uuid(String name, boolean autoIncrement) {
        return ColumnInfo.builder()
                .name(name)
                .jdbcType("uuid")
                .nullable(false)
                .autoIncrement(autoIncrement)
                .build();
    }

    private static Map<String, String> byPath(List<GeneratedSource> sources) {
        return sources.stream().collect(Collectors.toMap(
                GeneratedSource::relativePath,
                GeneratedSource::content,
                (a, b) -> b));
    }
}
