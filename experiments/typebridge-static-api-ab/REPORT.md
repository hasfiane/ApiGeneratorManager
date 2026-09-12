# Runtime-driven API vs static strong API — TypeBridge benchmark

Date: 2026-09-12

This experiment compares two generated Spring Boot APIs built from the same logical `customer` / `orders` schema.

## A — Current runtime-driven architecture

The current ApiGeneratorManager product path keeps the Java surface very small and drives behavior from `schema.json` through `api-generator-runtime`.

## B — Full static strongly typed architecture

The experimental variant generates concrete Java artifacts:

- JPA entities
- semantic primary-key value objects (`CustomerId`, `OrdersId`)
- Spring Data repositories
- services
- HTTP controllers and DTO records
- integration tests

HTTP DTOs still expose `UUID`. Domain services accept semantic IDs. TypeBridge inserts only declared raw -> strong construction inside `@AdaptationScope`. Leaving the domain remains explicit with `.value()`.

Strong primary keys are persisted as JPA `@Embeddable` value objects referenced by entity `@EmbeddedId` fields. The earlier `AttributeConverter` approach was rejected by Hibernate 7 for identifier attributes and is no longer used.

## CI environment

- GitHub Actions Ubuntu runner
- Temurin JDK 21
- Java source/release 17
- Spring Boot 4.0.4
- Hibernate ORM 7.2.7.Final
- Lombok 1.18.42
- H2 2.4.240

## Final measured result

| Metric | Runtime-driven | Static strong + TypeBridge |
| --- | ---: | ---: |
| Generated Java files | 2 | 12 |
| Generated Java LOC | 22 | 244 |
| `clean verify` | 8,996 ms | 9,449 ms |
| Median `clean compile` (3 runs) | 3,046 ms | 3,452 ms |
| Median compile delta | — | +13.3% |

The timing numbers are CI microbenchmarks on this deliberately small generated project. They must not be extrapolated linearly to a large application.

A narrower entity-generator benchmark on the same branch measured:

| Metric | Raw-ID generator | Strong-ID generator |
| --- | ---: | ---: |
| Generated files | 2 | 4 |
| Generated LOC | 65 | 125 |
| Median direct javac compile | 1,186 ms | 1,251 ms |
| Compile delta | — | +5.5% |

## Safety / behavior checks that passed

- Real Lombok 1.18.42 generated builder members are discovered after annotation processing.
- `UUID -> CustomerId`, `Long -> CustomerId`, and `BigDecimal -> Money` declared raw -> strong adaptations compile in opted-in scopes.
- Strong -> raw remains explicit (`.value()`).
- Arbitrary adaptation chains are not introduced.
- A baseline UUID-only API accepts an order-ID/customer-ID mix-up because both values are `UUID`.
- The strong-ID API rejects `OrdersId -> CustomerId`, even with TypeBridge enabled.
- Controllers keep raw UUID request values and do not contain explicit `new CustomerId(...)` / `new OrdersId(...)` boilerplate.
- Spring Data discovers both generated repositories.
- Hibernate initializes the entity model with `@EmbeddedId` strong IDs.
- H2 persistence round-trips `CustomerId` and `OrdersId` successfully.
- Runtime behavior for the compared ID mapping remains unchanged.
- Existing repository backend and frontend CI remain green in the CI workflow used for this experiment.

## What the experiment disproved

TypeBridge is not a useful addition to ApiGeneratorManager's current runtime-driven generation path by itself. That product path does not generate a static domain model, so there is little conversion boilerplate for TypeBridge to remove.

It also disproved the first persistence design: a JPA `AttributeConverter<StrongId, UUID>` applied to an `@Id` is not a viable Hibernate 7 strategy. The working design uses `@Embeddable` + `@EmbeddedId`.

## What the experiment proved

TypeBridge can make a fully static, strongly typed generated Java API materially more ergonomic at raw/domain boundaries while preserving semantic type safety. In particular, the generated controller can remain visually equivalent to the raw-ID version:

```java
service.create(request.id(), request.customerId(), request.total());
```

while the service contract is strongly typed:

```java
Orders create(OrdersId id, CustomerId customerId, BigDecimal total)
```

and Java still rejects passing an `OrdersId` where a `CustomerId` is required.

## Cost

The cost is real:

- substantially more generated Java surface (+10 files / +222 LOC in this two-table full-static fixture)
- compiler-plugin coupling to javac internals
- JDK/compiler compatibility work
- Maven/compiler configuration
- IDE support burden
- approximately +13.3% median compile time in this tiny full-static CI benchmark

The generated-source increase is primarily the cost of choosing static code generation over the existing runtime-driven architecture, not TypeBridge alone. TypeBridge's role is to prevent the strong type model from also imposing explicit wrapper construction at every Java call site.

## Decision

**GO for TypeBridge as a compiler primitive / optional strong-domain generation capability.**

**NO-GO for inserting TypeBridge into the current runtime-driven ApiGeneratorManager path just to use it.**

The commercially meaningful direction is an optional `static/strong-domain` generation mode, not replacing the existing runtime mode. The two modes solve different problems:

- runtime mode: minimal generated code, fast iteration, dynamic schema behavior
- static strong mode: compile-time domain contracts, IDE discoverability, semantic-ID safety, explicit Java architecture

Before productizing, the next required work is packaging TypeBridge as a proper versioned compiler artifact (instead of the experiment probe), IDE ergonomics, supported-JDK policy, and a larger multi-table benchmark with composite keys, nullable FKs, collections, enums, validation and OpenAPI.
