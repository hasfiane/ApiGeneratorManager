# ApiGeneratorManager documentation

Single source of technical information for contributors and integrators.

## Table of contents

1. [Technical architecture](#technical-architecture)
2. [Runtime contracts](#runtime-contracts)
3. [Continuous integration](#continuous-integration)
4. [Security rules](#security-rules)
5. [Contributing](#contributing)

## Technical architecture

### Dependency direction

```text
api-generator-core          -> schema and source generation
api-generator-runtime       -> runtime embedded in generated APIs
api-generator-template      -> source template used by generation
api-generator-back          -> manager API, persistence, jobs, preview, export
api-generator-front         -> dashboard consuming the manager API
```

The backend depends on core, runtime, and template concerns. The generated project receives the runtime and specialized source; it is not the manager application.

### Generation pipeline

1. `GenerationController` accepts a validated request.
2. `GenerationJobService` creates the persistent job and records progress.
3. `SchemaReader` implementations read JDBC metadata or `YamlSchemaSourceReader` reads YAML.
4. Core generators create entities and project files.
5. The backend stores logs, status, generated API metadata, and ZIP output.
6. `PreviewService` and the local runtime manage optional preview execution.
7. `PreviewProxyController` exposes controlled manager proxy operations.

### Security boundaries

- Backend authentication, ownership, roles, quotas, and capabilities are authoritative.
- Generated APIs have their own JWT and CRUD security configuration.
- Preview URLs and generated runtime URLs are distinct contracts.
- Schema input and JDBC targets must be validated before access.
- Configuration comes from the consuming application; source files contain no deployment credentials.

## Runtime contracts

### Generated API identity

A generated API is a separate runtime from the manager. The manager may store its metadata, logs, preview state, and artifact, but must not substitute manager OpenAPI or authentication for the generated API.

### URL fields

- `apiBaseUrl` identifies the generated API runtime.
- `proxyUrl` identifies a manager-owned proxy route.
- These values are not interchangeable.
- If the generated runtime URL is unavailable or unsafe, the client must not silently fall back to manager Swagger.

### Preview

Preview is an optional local validation flow. Preview state, logs, diagnostics, and access URLs must remain associated with the generated API record that created them.

## Continuous integration

The public workflows validate source code only:

- Maven compilation, tests, and verification;
- frontend dependency installation, type-checking, linting, tests, and build;
- dependency auditing and static security checks.

They do not publish container images, deploy infrastructure, require deployment credentials, reference a company domain, or depend on a developer's environment.

Deployment automation is outside this repository's public CI scope. Integrators may define it independently for their own infrastructure.

## Security rules

### Scope

Security boundaries of the source project and the local runtime.

### Rules for contributors and integrators

- Never commit `.env` files, credentials, tokens, certificates, JDBC passwords, generated customer data, or private URLs.
- Inject secrets through the consuming application's environment or secret manager.
- Replace every `change-me` value before starting the local stack.
- Keep PostgreSQL, Mailpit, CloudBeaver, and the local container runtime on a trusted network.
- Do not expose local preview or Docker control endpoints to an untrusted network.
- Keep manager authentication separate from generated API authentication.
- Treat generated ZIP artifacts and preview logs as potentially sensitive because they can contain schema metadata.

### Reporting

Do not open a public issue for a vulnerability. Use GitHub's private security advisory mechanism and include the affected commit, impact, reproduction steps, and suggested mitigation.

This project does not provide a hosted service or claim deployment security. Integrators are responsible for reviewing their own runtime, network, credentials, and data handling.

## Contributing

### Before opening a pull request

1. Preserve the existing module boundaries.
2. Do not commit credentials, private URLs, certificates, generated artifacts, or local environment files.
3. Keep manager authentication separate from authentication inside generated APIs.
4. Preserve generation history, persisted logs, ZIP export, preview lifecycle, Swagger proxying, i18n, and plan/capability enforcement.
5. Run the checks relevant to the changed module.

### Validation

Backend and root changes:

```bash
./mvnw test
```

Frontend changes:

```bash
cd api-generator-front
npm ci
npm run type-check
npm run lint
npm run test
npm run build
```

If a check cannot run locally, explain why in the pull request.

### Pull requests

Describe the user impact, affected modules, validation performed, and any remaining risks. Keep changes focused and avoid adding dependencies without documenting the reason.
