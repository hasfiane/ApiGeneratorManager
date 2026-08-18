# ApiGeneratorManager

> **Alpha project** — this repository is in early development. APIs, configuration, and generated output may change significantly between versions. Use it for experimentation and feedback, not production workloads.

ApiGeneratorManager is a local-first, multi-module Java project that generates Spring Boot APIs from JDBC database metadata or YAML schemas.

It contains:

- a schema reader and source generator;
- a reusable runtime embedded in generated APIs;
- a Spring Boot manager backend;
- a React/Vite dashboard;
- a Spring Boot project template.

The repository has no hosted service or provider-specific deployment workflow.

## Technology stack

| Area | Technology |
| --- | --- |
| Backend | Java 17+, Spring Boot 4.0.4, Spring Security, Spring Data JPA |
| Build | Maven multi-module project, Maven Wrapper |
| Persistence | PostgreSQL, Flyway |
| API security | JWT in HttpOnly cookies, CSRF, OAuth2 client support |
| Schema input | JDBC metadata and YAML/YML |
| Generated API | Spring Boot, JPA, dynamic CRUD runtime, OpenAPI |
| Frontend | React 18, TypeScript, Vite 8, React Router |
| Frontend tests | Vitest, Testing Library, jsdom |
| Local services | Docker Compose, PostgreSQL, Mailpit, optional CloudBeaver |
| Supported local database readers | PostgreSQL, MySQL, Oracle, H2 |

## Repository layout

```text
.
├── api-generator-core/       Schema reading and code generation
├── api-generator-runtime/    Runtime embedded in generated APIs
├── api-generator-back/       Manager API, jobs, persistence, preview, export
├── api-generator-front/      React/Vite dashboard
├── api-generator-template/   Base generated Spring Boot project
├── docs/                     Technical architecture and runtime contracts
├── scripts/                  Local validation utilities
├── docker-compose.yml        Optional local stack
├── Dockerfile.back           Local backend image build
├── pom.xml                   Maven parent project
└── .env.local.example        Local environment template
```

## Requirements

### Required for backend

- Java 17 or newer;
- Maven 3.9+ or the included Maven Wrapper;
- PostgreSQL 16+ for the manager database.

### Required for frontend

- Node.js 20 or newer;
- npm 10 or newer.

### Optional

- Docker Engine 24+;
- Docker Compose v2;
- Mailpit for local email inspection;
- CloudBeaver for local database inspection.

Check installed versions:

```bash
java -version
./mvnw -version
node --version
npm --version
docker compose version
```

## Option A — Run with Docker Compose

This is the easiest way to start the complete local stack.

### 1. Create the local environment file

```bash
cp .env.local.example .env.local
```

Replace every `change-me` value in `.env.local`. The file is ignored by Git and must never be committed.

### 2. Start the stack

```bash
docker compose --env-file .env.local up -d --build
```

### 3. Inspect the services

| Service | Local address |
| --- | --- |
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health |
| PostgreSQL | localhost:5432 |
| Mailpit UI | http://localhost:8025 |
| CloudBeaver | http://localhost:8978 |

The Docker profile uses a local Docker socket proxy for the optional preview runtime. Keep the whole stack on a trusted local machine/network.

### 4. View logs and stop

```bash
docker compose --env-file .env.local logs -f backend
docker compose --env-file .env.local logs -f frontend
docker compose --env-file .env.local ps
docker compose --env-file .env.local down
```

Add `-v` to `down` only if you intentionally want to remove local database volumes.

## Option B — Run services directly

Use this mode when developing a module or debugging without the full Docker stack.

### 1. Start PostgreSQL

Provide a local PostgreSQL database and set the corresponding variables:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/apigen_manager
SPRING_DATASOURCE_USERNAME=apigen
SPRING_DATASOURCE_PASSWORD=<local-password>
```

The backend also requires a non-empty `MANAGER_JWT_SECRET`. Use `.env.local.example` as the reference for the remaining local values.

### 2. Start the backend

From the repository root:

```bash
./mvnw -pl api-generator-back -am spring-boot:run
```

The backend listens on port `8080` by default.

### 3. Start the frontend

In another terminal:

```bash
cd api-generator-front
npm ci
npm run dev
```

The Vite development server normally listens on port `5173`. Set `VITE_API_BASE_URL=http://localhost:8080` when required by the local frontend configuration.

## Build and test

### Full Maven verification

```bash
./mvnw -B clean verify
```

### Build a single Maven module

```bash
./mvnw -pl api-generator-core -am verify
./mvnw -pl api-generator-runtime -am verify
./mvnw -pl api-generator-back -am verify
```

### Frontend checks

```bash
cd api-generator-front
npm ci
npm run type-check
npm run lint
npm run test
npm run build
```

Run one frontend test file:

```bash
npm run test -- src/services/api.test.ts
```

## Local configuration

The backend reads Spring configuration from environment variables and `application.yml`. Important settings include:

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_*` | Manager database connection |
| `MANAGER_JWT_SECRET` | Manager session signing secret |
| `GENERATED_API_JWT_SECRET` | Generated API signing secret |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed frontend origins |
| `APP_TEMPLATE_PATH` | Generated project template path |
| `APP_CONTAINER_RUNTIME` | Local preview runtime, usually `docker` |
| `APP_GENERATION_DOCKER_DEPLOYMENT_ENABLED` | Enables optional local container preview |
| `APP_ACCOUNT_BOOTSTRAP_*` | Optional local bootstrap account |
| `VITE_API_BASE_URL` | Backend URL used by the frontend |

Never put database credentials, OAuth secrets, JWT keys, or customer schemas in tracked files.

## Generation flow

1. The frontend sends a generation request to the backend.
2. The backend authenticates the user and validates input, ownership, quotas, capabilities, and JDBC policy.
3. `api-generator-core` reads JDBC metadata or YAML.
4. The generator specializes `api-generator-template`.
5. `api-generator-runtime` is included in the generated project.
6. The backend persists the job, progress logs, generated API metadata, and ZIP artifact.
7. The optional local preview starts the generated API.
8. The dashboard displays status, logs, diagnostics, preview access, and downloads.

## Integration

For backend integration, install the modules in the local Maven repository first:

```bash
./mvnw -DskipTests install
```

Then depend on the required modules from the consuming project:

```xml
<dependency>
  <groupId>com.api</groupId>
  <artifactId>api-generator-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

Add `api-generator-runtime` when the generated API requires the shared runtime. The manager backend is optional when generation is embedded into another application.

The frontend can be integrated separately by configuring `VITE_API_BASE_URL` and implementing the manager API contract consumed by `api-generator-front/src/services/api.ts`.

See:

- [Technical documentation](docs/DOCUMENTATION.md)

## Troubleshooting

### Backend does not start

Check that PostgreSQL is reachable, the database credentials are correct, and `MANAGER_JWT_SECRET` is set.

### Frontend cannot reach the backend

Check `VITE_API_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS`, and that the backend is listening on port `8080`.

### Email verification is not visible

The Docker profile uses Mailpit. Open http://localhost:8025 and inspect the captured messages.

### Preview cannot start

Confirm that Docker is running, local Docker preview is enabled, and the Docker socket proxy is healthy. Preview is optional; generation and ZIP export do not require exposing the project to an external network.

## License

MIT. See [LICENSE](LICENSE).
