# TypeBridge × ApiGeneratorManager — real A/B verdict

## Scope

This experiment tests TypeBridge against the real `ApiGeneratorManager` repository, real `javac`, real Lombok 1.18.42, generated JPA sources, JPA `AttributeConverter`s, and the repository CI.

The baseline branch behavior remains unchanged. The experimental strong-ID path is opt-in through `JpaEntitySourceGenerator.generateStrongIds(...)`.

## What was proven

### Compiler / Lombok

The generalized TypeBridge probe runs after annotation processing and successfully discovers Lombok-generated builder members.

It supports declared raw-to-strong relations such as:

```text
UUID       -> CustomerId
Long       -> CustomerId
BigDecimal -> Money
```

inside `@AdaptationScope` while keeping strong-to-raw exits explicit.

The typed-domain fixture also validates nested types and fluent Lombok builders.

### Safety

The experiment deliberately tests semantic ID confusion.

Baseline generated model:

```java
UUID customerId;
UUID orderId;
```

A value semantically representing an order ID can be passed to a customer-ID position because both are `UUID`.

Strong-ID generated model:

```java
CustomerId customerId;
OrdersId orderId;
```

`OrdersId -> CustomerId` is rejected by the compiler even when TypeBridge is enabled. TypeBridge does not chain through the raw representation and does not implicitly unwrap strong values.

### Ergonomics

The mapper source is identical in both variants:

```java
return Customer.builder().id(raw).name("Ada").build();
```

- baseline: valid because `id(...)` accepts `UUID`
- strong IDs without TypeBridge: compile error
- strong IDs with TypeBridge: TypeBridge elaborates `UUID -> CustomerId`

Leaving the strong domain is explicit:

```java
customer.getId().value()
```

### Persistence

Each generated strong ID has an explicit JPA `AttributeConverter<StrongId, Raw>`.
The generated entities and converters compile successfully in CI.

### Runtime

Baseline and strong-ID + TypeBridge variants produce the same tested runtime value:

```text
123e4567-e89b-12d3-a456-426614174000
```

## Measured cost

Test schema: two tables with two UUID primary keys.

| Metric | Baseline | Strong IDs + TypeBridge | Delta |
|---|---:|---:|---:|
| Generated Java files | 2 | 6 | +4 |
| Generated LOC | 65 | 123 | +58 |
| Median tiny-project compile time | 1083 ms | 1165 ms | +82 ms / +7.6% |

The compile-time figure is a CI microbenchmark over a tiny generated source set. JVM/javac/plugin startup is a significant portion of the measurement and the +7.6% number must not be extrapolated to a full application build.

## Bugs found by the real integration

The real Lombok benchmark exposed compiler-phase issues that the synthetic fixtures did not fully reveal:

1. `@StrongType` constructor validation was too early at PARSE time. Constructor relation discovery was moved after annotation processing.
2. Nested generated types required consistent compiler type canonicalization.
3. Nested source types were indexed both directly and recursively, duplicating Lombok-generated method signatures. Method indexing was changed to a deduplicated `Set<MethodSig>`.

These failures were fixed without weakening the fixtures.

## Critical product-pipeline finding

`JpaEntitySourceGenerator` is **not currently used by the main generated-API delivery path**.

`GenerationJobService` currently:

1. copies `api-generator-template`;
2. writes `application.yml`;
3. writes `schema.json`;
4. writes the generated Spring Boot application/test/support files;
5. optionally executes `clean verify`;
6. packages the generated project.

The delivered API is runtime-driven from `schema.json`; the current generation path does not emit the JPA entity sources used by this A/B benchmark.

Therefore TypeBridge does not currently remove real boilerplate from the product's normal generated API output. Integrating it into `GenerationJobService` today solely to exercise strong IDs would create a new static-domain generation mode rather than improve the existing runtime-driven mode.

## Verdict

### TypeBridge mechanism: GO for continued technical validation

The experiment demonstrates a real capability:

- stronger semantic type safety;
- unchanged mapper ergonomics for raw-to-strong entry;
- real Lombok compatibility;
- explicit domain exits;
- no cross-ID coercion;
- JPA persistence compatibility in the tested model.

### Direct integration into the current ApiGeneratorManager product path: NO-GO for now

The current product does not generate the static domain model where TypeBridge provides its value. Adding TypeBridge to the existing runtime-driven path would add compiler/JDK coupling without a corresponding user benefit.

### Product opportunity worth testing separately

A future **Static Strong Domain Model** generation mode could be a differentiated ApiGeneratorManager feature:

```text
Database schema
    -> generated CustomerId / OrderId / ...
    -> generated typed JPA entities / DTOs / services
    -> TypeBridge keeps raw-to-domain call sites concise
    -> explicit .value() at persistence/protocol exits
```

That should be treated as a new product mode and evaluated on its own merits, not smuggled into the current runtime-driven generator.

## Decision rule for the next experiment

Do not add TypeBridge to production until a real static-domain generation path shows enough value to justify:

- generated strong-type and converter code;
- javac internal API coupling;
- IDE/tooling requirements;
- compiler integration maintenance;
- measured build overhead.

The next meaningful benchmark should compare the existing runtime-driven generated API against a complete static strongly typed generated API on the same non-trivial schema and API use cases, including CRUD, relationships, validation, JSON/OpenAPI, persistence, tests, and build time.
