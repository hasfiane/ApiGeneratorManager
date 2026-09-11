# TypeBridge × ApiGeneratorManager — real generator A/B

Branch-only experiment. This document records what TypeBridge actually gains or loses in the current generator architecture before adding any new product mode.

## Frozen branch state

The TypeBridge/Lombok integration is validated by the repository CI on commit `568e17f1cf05679f84f7189ee12db715e5d0a44c`:

- normal backend verification passes;
- frontend verification passes;
- real Lombok integration passes;
- UUID -> strong type wrapping is allowed only inside `@AdaptationScope`;
- strong type -> raw type remains explicit and is protected by a negative compilation test.

## What the generator actually emits today

The production generation path in `GenerationJobService` copies `api-generator-template`, writes `application.yml`, writes `schema.json`, then calls `writeGeneratedApplicationSources`.

That method writes only two Java source files:

1. `<basePackage>/GeneratedApiApplication.java`
2. `<basePackage>/GeneratedApiApplicationTests.java`

The database/domain model is not emitted as a static DTO/domain/service/mapper graph. It is interpreted at runtime from `schema.json` by `api-generator-runtime` (`DynamicCrudController`, `DynamicRepository`, schema registry/validation, etc.).

`JpaEntitySourceGenerator` exists in `api-generator-core`, but it is not the dominant source-generation path used by the generated ZIP tested by `GeneratedApiEndToEndTest`.

## Honest A/B result for the current product

| Metric | Current generator | Current generator + TypeBridge |
|---|---:|---:|
| Generated domain strong types | 0 | 0 |
| Generated mapper conversion sites | 0 | 0 |
| Generated wrapper boilerplate removable by TypeBridge | 0 | 0 |
| Runtime behavior changed | no | no |
| Compiler plugin dependency | no | yes if integrated |
| `javac` internals coupling | no | yes if integrated |
| IDE/compiler integration burden | no | yes if integrated |

**Verdict: NO-GO for integrating TypeBridge into the current default generation path.**

There is no meaningful boilerplate for TypeBridge to remove because the current generated API is deliberately runtime/schema-driven. Adding the compiler extension to that path would create build and tooling cost without user-visible benefit.

This is not a failure of the TypeBridge mechanism. It means ApiGeneratorManager's current architecture is not the workload TypeBridge was designed to optimize.

## Where TypeBridge could become useful

The relevant product experiment is a separate optional generation mode, for example `typed-domain`, that emits compile-time Java domain artifacts while leaving the current dynamic runtime mode untouched.

A credible typed mode should generate at least:

- strong identifiers (`CustomerId`, `OrderId`, ...);
- request/response DTOs with technical boundary types;
- domain commands/models with strong types;
- mappers between boundary DTOs and domain types;
- explicit raw exits (`id.value()` / `id.getValue()`) at persistence/serialization boundaries;
- TypeBridge only for declared raw -> strong wrapping inside generated mapping scopes.

Example target source:

```java
@StrongType
public record CustomerId(UUID value) {}

@AdaptationScope
final class CustomerMapper {
    CustomerCommand toDomain(CreateCustomerRequest request) {
        // Constructor expects CustomerId; request exposes UUID.
        return new CustomerCommand(request.customerId());
    }

    CustomerResponse toResponse(Customer customer) {
        // Leaving the domain stays explicit.
        return new CustomerResponse(customer.id().value());
    }
}
```

The compiler elaborates only the first direction to `new CustomerId(request.customerId())`. It must never automatically elaborate the second direction.

## Acceptance criteria for typed-domain experiment

Do not promote this mode unless an end-to-end generated project proves all of the following on the same schema:

1. generated project builds and starts;
2. HTTP behavior matches the dynamic baseline for covered CRUD operations;
3. raw -> strong wrapper expressions materially decrease;
4. strong -> raw exits remain visible and explicit;
5. no arbitrary adaptation chains are introduced;
6. Lombok compatibility remains green;
7. compile-time overhead is measured on the complete generated project;
8. generated LOC/template complexity is reported, not hidden;
9. persistence does not require unsafe implicit conversions;
10. removing TypeBridge causes the intentionally concise mapper calls to fail compilation.

Until that experiment demonstrates a concrete net win, TypeBridge should remain isolated under `experiments/` and must not be added to the generated project's default Maven configuration.
