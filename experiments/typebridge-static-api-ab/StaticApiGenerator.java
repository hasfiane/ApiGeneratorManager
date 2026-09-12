package experiment.staticab;

import com.api.generator.generator.GeneratedSource;
import com.api.generator.generator.JpaEntitySourceGenerator;
import com.api.generator.schema.ColumnInfo;
import com.api.generator.schema.ForeignKeyInfo;
import com.api.generator.schema.TableInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class StaticApiGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output root");
        Path root = Path.of(args[0]);
        Path runtime = root.resolve("runtime-api");
        Path statik = root.resolve("static-api");
        Files.createDirectories(runtime);
        Files.createDirectories(statik);

        List<TableInfo> schema = schema();
        writeRuntimeProject(runtime);
        writeStaticProject(statik, schema);

        Metrics runtimeMetrics = metrics(runtime);
        Metrics staticMetrics = metrics(statik);
        System.out.println("METRIC runtime_java_files=" + runtimeMetrics.files);
        System.out.println("METRIC runtime_java_loc=" + runtimeMetrics.loc);
        System.out.println("METRIC static_java_files=" + staticMetrics.files);
        System.out.println("METRIC static_java_loc=" + staticMetrics.loc);
        System.out.println("METRIC static_added_java_files=" + (staticMetrics.files - runtimeMetrics.files));
        System.out.println("METRIC static_added_java_loc=" + (staticMetrics.loc - runtimeMetrics.loc));
        System.out.println("PASS: runtime-driven and full static APIs were generated from the same customer/orders schema");
    }

    private static List<TableInfo> schema() {
        TableInfo customer = new TableInfo("customer");
        customer.getColumns().add(uuid("id"));
        customer.getColumns().add(varchar("name"));
        customer.getPrimaryKeys().add("id");

        TableInfo order = new TableInfo("orders");
        order.getColumns().add(uuid("id"));
        order.getColumns().add(uuid("customer_id"));
        order.getColumns().add(decimal("total"));
        order.getPrimaryKeys().add("id");
        order.getForeignKeys().add(new ForeignKeyInfo("customer_id", "customer", "id"));
        return List.of(customer, order);
    }

    private static void writeRuntimeProject(Path root) throws Exception {
        write(root.resolve("pom.xml"), runtimePom());
        write(root.resolve("src/main/java/bench/runtime/RuntimeApiApplication.java"), """
                package bench.runtime;
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class RuntimeApiApplication {
                    public static void main(String[] args) { SpringApplication.run(RuntimeApiApplication.class, args); }
                }
                """);
        write(root.resolve("src/main/resources/schema.json"), """
                [
                  {"name":"customer","columns":[
                    {"name":"id","jdbcType":"uuid","nullable":false},
                    {"name":"name","jdbcType":"varchar","nullable":false}
                  ],"primaryKeys":["id"],"foreignKeys":[]},
                  {"name":"orders","columns":[
                    {"name":"id","jdbcType":"uuid","nullable":false},
                    {"name":"customer_id","jdbcType":"uuid","nullable":false},
                    {"name":"total","jdbcType":"numeric","nullable":false,"size":19,"decimalDigits":2}
                  ],"primaryKeys":["id"],"foreignKeys":[{"fkColumn":"customer_id","pkTable":"customer","pkColumn":"id"}]}
                ]
                """);
        write(root.resolve("src/test/java/bench/runtime/RuntimeApiApplicationTests.java"), """
                package bench.runtime;
                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;
                import static org.junit.jupiter.api.Assertions.*;
                @SpringBootTest(properties = {
                  "generator.runtime.enabled=false",
                  "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
                })
                class RuntimeApiApplicationTests {
                    @Test void schemaIsPackaged() throws Exception {
                        try (var in = getClass().getClassLoader().getResourceAsStream("schema.json")) {
                            assertNotNull(in); assertTrue(in.readAllBytes().length > 0);
                        }
                    }
                }
                """);
    }

    private static void writeStaticProject(Path root, List<TableInfo> schema) throws Exception {
        write(root.resolve("pom.xml"), staticPom());
        JpaEntitySourceGenerator generator = new JpaEntitySourceGenerator();
        for (GeneratedSource source : generator.generateStrongIds(schema, "bench.staticapi")) {
            write(root.resolve("src/main/java").resolve(source.relativePath()), source.sourceCode());
        }
        write(root.resolve("src/main/java/bench/staticapi/StaticApiApplication.java"), """
                package bench.staticapi;
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class StaticApiApplication {
                    public static void main(String[] args) { SpringApplication.run(StaticApiApplication.class, args); }
                }
                """);
        write(root.resolve("src/main/java/bench/staticapi/repository/CustomerRepository.java"), """
                package bench.staticapi.repository;
                import bench.staticapi.entity.Customer;
                import bench.staticapi.types.CustomerId;
                import org.springframework.data.jpa.repository.JpaRepository;
                public interface CustomerRepository extends JpaRepository<Customer, CustomerId> {}
                """);
        write(root.resolve("src/main/java/bench/staticapi/repository/OrdersRepository.java"), """
                package bench.staticapi.repository;
                import bench.staticapi.entity.Orders;
                import bench.staticapi.types.OrdersId;
                import org.springframework.data.jpa.repository.JpaRepository;
                public interface OrdersRepository extends JpaRepository<Orders, OrdersId> {}
                """);
        write(root.resolve("src/main/java/bench/staticapi/service/CustomerService.java"), """
                package bench.staticapi.service;
                import bench.staticapi.entity.Customer;
                import bench.staticapi.repository.CustomerRepository;
                import bench.staticapi.types.CustomerId;
                import org.springframework.stereotype.Service;
                @Service
                public class CustomerService {
                    private final CustomerRepository repository;
                    public CustomerService(CustomerRepository repository) { this.repository = repository; }
                    public Customer create(CustomerId id, String name) {
                        return repository.save(Customer.builder().id(id).name(name).build());
                    }
                    public Customer get(CustomerId id) { return repository.findById(id).orElseThrow(); }
                }
                """);
        write(root.resolve("src/main/java/bench/staticapi/service/OrderService.java"), """
                package bench.staticapi.service;
                import bench.staticapi.entity.Orders;
                import bench.staticapi.repository.CustomerRepository;
                import bench.staticapi.repository.OrdersRepository;
                import bench.staticapi.types.CustomerId;
                import bench.staticapi.types.OrdersId;
                import java.math.BigDecimal;
                import org.springframework.stereotype.Service;
                @Service
                public class OrderService {
                    private final OrdersRepository orders;
                    private final CustomerRepository customers;
                    public OrderService(OrdersRepository orders, CustomerRepository customers) {
                        this.orders = orders; this.customers = customers;
                    }
                    public Orders create(OrdersId id, CustomerId customerId, BigDecimal total) {
                        var customer = customers.findById(customerId).orElseThrow();
                        return orders.save(Orders.builder().id(id).customer(customer).total(total).build());
                    }
                    public Orders get(OrdersId id) { return orders.findById(id).orElseThrow(); }
                }
                """);
        write(root.resolve("src/main/java/bench/staticapi/web/CustomerController.java"), """
                package bench.staticapi.web;
                import bench.staticapi.entity.Customer;
                import bench.staticapi.service.CustomerService;
                import dev.typebridge.api.AdaptationScope;
                import java.util.UUID;
                import org.springframework.web.bind.annotation.*;
                @RestController @RequestMapping("/customers") @AdaptationScope
                public class CustomerController {
                    private final CustomerService service;
                    public CustomerController(CustomerService service) { this.service = service; }
                    public record CreateCustomerRequest(UUID id, String name) {}
                    public record CustomerResponse(UUID id, String name) {}
                    @PostMapping public CustomerResponse create(@RequestBody CreateCustomerRequest request) {
                        Customer c = service.create(request.id(), request.name());
                        return new CustomerResponse(c.getId().value(), c.getName());
                    }
                    @GetMapping("/{id}") public CustomerResponse get(@PathVariable UUID id) {
                        Customer c = service.get(id);
                        return new CustomerResponse(c.getId().value(), c.getName());
                    }
                }
                """);
        write(root.resolve("src/main/java/bench/staticapi/web/OrderController.java"), """
                package bench.staticapi.web;
                import bench.staticapi.entity.Orders;
                import bench.staticapi.service.OrderService;
                import dev.typebridge.api.AdaptationScope;
                import java.math.BigDecimal;
                import java.util.UUID;
                import org.springframework.web.bind.annotation.*;
                @RestController @RequestMapping("/orders") @AdaptationScope
                public class OrderController {
                    private final OrderService service;
                    public OrderController(OrderService service) { this.service = service; }
                    public record CreateOrderRequest(UUID id, UUID customerId, BigDecimal total) {}
                    public record OrderResponse(UUID id, UUID customerId, BigDecimal total) {}
                    @PostMapping public OrderResponse create(@RequestBody CreateOrderRequest request) {
                        Orders o = service.create(request.id(), request.customerId(), request.total());
                        return new OrderResponse(o.getId().value(), o.getCustomer().getId().value(), o.getTotal());
                    }
                    @GetMapping("/{id}") public OrderResponse get(@PathVariable UUID id) {
                        Orders o = service.get(id);
                        return new OrderResponse(o.getId().value(), o.getCustomer().getId().value(), o.getTotal());
                    }
                }
                """);
        write(root.resolve("src/main/resources/application.yml"), """
                spring:
                  datasource:
                    url: jdbc:h2:mem:staticdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
                    driver-class-name: org.h2.Driver
                    username: sa
                    password: ''
                  jpa:
                    hibernate:
                      ddl-auto: create-drop
                """);
        write(root.resolve("src/test/java/bench/staticapi/StaticApiIntegrationTest.java"), """
                package bench.staticapi;
                import bench.staticapi.service.CustomerService;
                import bench.staticapi.service.OrderService;
                import bench.staticapi.types.CustomerId;
                import bench.staticapi.types.OrdersId;
                import java.math.BigDecimal;
                import java.util.UUID;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.context.SpringBootTest;
                import static org.junit.jupiter.api.Assertions.*;
                @SpringBootTest
                class StaticApiIntegrationTest {
                    @Autowired CustomerService customers;
                    @Autowired OrderService orders;
                    @Test void persistsStrongIdsThroughJpaConverters() {
                        UUID customerRaw = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                        UUID orderRaw = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
                        customers.create(new CustomerId(customerRaw), "Ada");
                        orders.create(new OrdersId(orderRaw), new CustomerId(customerRaw), new BigDecimal("42.50"));
                        assertEquals(customerRaw, customers.get(new CustomerId(customerRaw)).getId().value());
                        assertEquals(orderRaw, orders.get(new OrdersId(orderRaw)).getId().value());
                    }
                }
                """);
    }

    private static String runtimePom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.0.4</version><relativePath/></parent>
                  <groupId>bench</groupId><artifactId>runtime-api-ab</artifactId><version>1.0-SNAPSHOT</version>
                  <properties><java.version>17</java.version></properties>
                  <dependencies>
                    <dependency><groupId>com.api</groupId><artifactId>api-generator-runtime</artifactId><version>0.1.0</version></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """;
    }

    private static String staticPom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.0.4</version><relativePath/></parent>
                  <groupId>bench</groupId><artifactId>static-strong-api-ab</artifactId><version>1.0-SNAPSHOT</version>
                  <properties><java.version>17</java.version><lombok.version>1.18.42</lombok.version></properties>
                  <dependencies>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
                    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
                    <dependency><groupId>dev.typebridge</groupId><artifactId>typebridge-probe</artifactId><version>0.0.0-experiment</version><scope>provided</scope></dependency>
                    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
                  </dependencies>
                  <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version><configuration>
                    <release>17</release>
                    <compilerArgs><arg>-Xplugin:TypeBridgeLombokProbe</arg></compilerArgs>
                    <annotationProcessorPaths><path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version></path></annotationProcessorPaths>
                  </configuration></plugin></plugins></build>
                </project>
                """;
    }

    private static ColumnInfo uuid(String name) {
        ColumnInfo c = new ColumnInfo(); c.setName(name); c.setJdbcType("uuid"); c.setNullable(false); return c;
    }
    private static ColumnInfo varchar(String name) {
        ColumnInfo c = new ColumnInfo(); c.setName(name); c.setJdbcType("varchar"); c.setSize(120); c.setNullable(false); return c;
    }
    private static ColumnInfo decimal(String name) {
        ColumnInfo c = new ColumnInfo(); c.setName(name); c.setJdbcType("numeric"); c.setSize(19); c.setDecimalDigits(2); c.setNullable(false); return c;
    }
    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent()); Files.writeString(path, content);
    }
    private static Metrics metrics(Path project) throws Exception {
        try (var stream = Files.walk(project.resolve("src"))) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".java")).toList();
            long loc = 0;
            for (Path p : files) loc += Files.readString(p).lines().count();
            return new Metrics(files.size(), loc);
        }
    }
    private record Metrics(long files, long loc) {}
}
