# CLAUDE.md — Backend (`apps/api`)

> Read the repo-root `CLAUDE.md` first. This file picks up where that one stops.
> It covers only backend-specific concerns: layer map, commands, conventions, domain
> concepts, and workflows inside `apps/api`.

---

## 1. Backend Scope

The backend is a Spring Boot REST API server responsible for: user authentication and role-based authorization; the full session ingestion pipeline (intake → extraction → entity resolution → conflict detection → diff generation → commit); world state persistence and versioning; natural language query execution; and all campaign, membership, and annotation management.

It is NOT responsible for rendering any UI, serving static assets, or making decisions about frontend layout or navigation. The frontend is the sole consumer of the HTTP API; there are no other callers in v1.

This is a stateless, synchronous REST monolith following hexagonal (Ports & Adapters) architecture. There is no message queue, event bus, or separate worker process — background work is handled within the same JVM process via Spring `@Async` and `ApplicationEvent`.

---

## 2. Backend Tech Stack

| Concern | Choice | Version |
|---|---|---|
| Language | Java | 25 (LTS) |
| Framework | Spring Boot | 4.0.3 |
| Build tool | Maven | latest stable |
| ORM | Spring Data JPA + Hibernate | aligned with Boot 4.x |
| Database driver | PostgreSQL JDBC | latest stable |
| Migrations | Liquibase | latest stable |
| Auth | Spring Security (JWT, HS256) | aligned with Boot 4.x |
| LLM generation | Spring AI `ChatClient` → Anthropic (Claude) | aligned with Boot 4.x |
| LLM embeddings | Spring AI `EmbeddingModel` → OpenAI `text-embedding-3-small` (1536 dims) | aligned with Boot 4.x |
| Vector retrieval | Native pgvector queries via Spring Data JPA / `JdbcTemplate` | — |
| Testing | JUnit 5, Mockito, Testcontainers, PITest, ArchUnit | latest stable |
| Linting/style | Spotless (Maven plugin) + google-java-format | latest stable |

**Spring AI scope:** `ChatClient` (text generation) and `EmbeddingModel` (vector generation) only. `VectorStore` is never used — see Rule ARCH-04 and D-062.

---

## 3. Backend Directory Structure

```
apps/api/
├── pom.xml                        ← Maven root; all dependency versions declared here
├── src/
│   ├── main/
│   │   ├── java/com/bluesteel/
│   │   │   ├── domain/            ← Pure Java; zero framework imports; entities, value objects, aggregates
│   │   │   │   ├── campaign/
│   │   │   │   ├── session/
│   │   │   │   ├── worldstate/
│   │   │   │   │   ├── actor/
│   │   │   │   │   ├── space/
│   │   │   │   │   ├── event/
│   │   │   │   │   └── relation/
│   │   │   │   ├── annotation/
│   │   │   │   └── proposal/
│   │   │   ├── application/
│   │   │   │   ├── port/
│   │   │   │   │   ├── in/        ← Driving ports (use-case interfaces)
│   │   │   │   │   └── out/       ← Driven ports (repository & service interfaces)
│   │   │   │   └── service/       ← Use-case implementations (orchestration only)
│   │   │   ├── adapters/
│   │   │   │   ├── in/
│   │   │   │   │   ├── web/       ← REST controllers, request/response DTOs, exception handlers
│   │   │   │   │   │   └── WebConfig.java
│   │   │   │   │   └── security/  ← Spring Security filter chain, JWT validation filter
│   │   │   │   │       └── SecurityConfig.java
│   │   │   │   └── out/
│   │   │   │       ├── persistence/ ← JPA entities, Spring Data repositories, mappers
│   │   │   │       │   └── PersistenceConfig.java
│   │   │   │       └── ai/          ← Spring AI adapters + mock adapters for local dev
│   │   │   │           └── AiConfig.java
│   │   │   └── config/            ← Cross-cutting beans with no adapter home
│   │   │       └── ApplicationConfig.java
│   │   └── resources/
│   │       ├── application.yml            ← Shared config (all profiles)
│   │       ├── application-local.yml      ← Local dev: mocks active, H2 or local PG
│   │       ├── application-llm-real.yml   ← Activates real Anthropic + OpenAI adapters
│   │       └── db/changelog/              ← Liquibase changelogs (never hand-edit)
│   └── test/
│       ├── java/com/bluesteel/
│       │   ├── architecture/      ← ArchUnit boundary enforcement tests
│       │   ├── domain/            ← Pure unit tests for domain logic
│       │   ├── application/       ← Unit tests for use-case services (ports mocked)
│       │   └── adapters/          ← Integration tests (Testcontainers for persistence, mocked LLM)
│       └── resources/
│           └── db/                ← Test fixtures and seed scripts
└── Dockerfile                     ← linux/arm64 image for Oracle Cloud VM
```

> ⚠️ `db/changelog/` is generated/managed by Liquibase. Never edit migration files by hand after they are applied. Add new changesets only.

---

## 4. How to Run the Backend

> ⚠️ This project is in the **Definition & Analysis phase**. The commands below will be filled in before Phase 1 begins.

**Start infrastructure first (run from repo root):**

```bash
docker compose up -d   # Starts PostgreSQL + pgvector on localhost:5432
```

**Install dependencies:**

```bash
cd apps/api && mvn dependency:resolve
```

**Run database migrations:**

```bash
# Liquibase runs automatically on application startup via Spring Boot integration.
# To run explicitly against the local database without starting the app:
cd apps/api && mvn liquibase:update
```

**Start development server (LLM ports mocked — zero API cost):**

```bash
cd apps/api && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Start with real LLM APIs (Anthropic + OpenAI — incurs cost):**

```bash
# Requires ANTHROPIC_API_KEY and OPENAI_API_KEY in .env.local
cd apps/api && mvn spring-boot:run -Dspring-boot.run.profiles=local,llm-real
```

**Run tests:**

```bash
# Unit tests + ArchUnit (fast; no Spring context, no external services)
cd apps/api && mvn test

# Integration tests (Testcontainers; spins up real PostgreSQL — slower)
cd apps/api && mvn verify

# Mutation tests — domain core only; slow — run deliberately, not on every save
# PITest is scoped to com.bluesteel.domain.* via pom.xml plugin config (D-036)
cd apps/api && mvn test-compile pitest:mutationCoverage
```

**Build for production:**

```bash
# Build JAR
cd apps/api && mvn package -DskipTests

# Build linux/arm64 Docker image (matches Oracle Cloud ARM VM — D-046)
docker buildx build --platform linux/arm64 \
  -t ghcr.io/<org>/blue-steel-api:<tag> \
  apps/api/
# CI handles this via backend.yml on push to main; image is pushed to ghcr.io
```

---

## 5. Architecture

The backend strictly follows Cockburn's Hexagonal (Ports & Adapters) architecture. The domain core is pure Java with zero framework imports. The application layer owns use-case interfaces (driving ports) and infrastructure interfaces (driven ports). Adapters implement driven ports and wire Spring's machinery to domain logic without the domain knowing about it. ArchUnit enforces these boundaries on every build (D-037).

Request flow: HTTP request → Spring Security (JWT validation, stateless) → REST controller (adapter-in) → use-case interface (driving port) → use-case service (application layer) → driven port interfaces → persistence/AI adapters (adapter-out) → PostgreSQL or LLM provider.

Authorization is resolved at the use-case boundary, not in the controller. The JWT carries only `user_id` and `is_admin`; campaign-level role is fetched from `campaign_members` via DB on each authorized call (D-043). The 15-minute access token TTL limits stale-auth exposure.

Background work (embedding generation post-commit) runs in the same JVM process via Spring `@Async` / `ApplicationEvent`. The commit endpoint returns HTTP 200 before embeddings are generated (D-063). The two LLM profile modes (mock vs real) are transparent to domain and application code — only the adapter implementation changes (D-049).

---

## 6. Backend Domain Concepts

### Campaign

The top-level container. Every other entity belongs to exactly one campaign via `campaign_id`. Created exclusively by `admin`; a GM is assigned atomically at creation (D-061). A campaign can never exist without exactly one `gm` — enforced by a partial unique index on `campaign_members WHERE role = 'gm'`. Stored in: `campaigns` table, `domain/campaign/`.

**Invariant:** exactly one active GM per campaign at all times; `gm_user_id` is required at creation.

### Session

A single play event with a lifecycle state machine. Valid transitions:

```
pending → processing → draft → committed
                     ↘ failed
                     ↘ discarded   (terminal; only from draft, GM only)
```

At most **one** session per campaign may be in `processing` or `draft` state simultaneously (D-054). A `committed` session is immutable — its `diff_payload` is cleared and `narrative_blocks` record is preserved forever. Stored in: `sessions` table.

**Invariant:** submitting a new session while another is in `processing` or `draft` returns `409`. A `discarded` session cannot be reactivated.

### Narrative Block

The raw submitted summary text, stored in `narrative_blocks.raw_summary_text` immediately on intake. Immutable after storage — never modified for any reason. `token_count` is estimated at intake for the budget check. Stored in: `narrative_blocks` table. Preserved even when the parent session is discarded or failed.

**Invariants:**
- `raw_summary_text` is write-once — the field is set at construction and never updated.
- `raw_summary_text` must be non-blank — a `NarrativeBlock` cannot be constructed with a null or whitespace-only summary. The domain constructor enforces this. The adapter's `@NotBlank` Bean Validation annotation is a first line of defence, not the sole gatekeeper. An empty summary would pass the token budget check (zero tokens) and produce meaningless extraction results.

### World State Entities (Actor, Space, Event, Relation)

Versioned domain entities. Each carries an append-only version history table (e.g., `actor_versions`). Every version row references the session that produced it. Current state = max `version_number` per entity. Point-in-time state = max version where `session_id ≤ target`. Each version stores both `changed_fields` (delta, D-006) and `full_snapshot` (complete state, for efficient reads). All carry `campaign_id` and `owner_id` (D-021). Stored in: `actors`, `actor_versions` (and equivalents for space, event, relation).

**Invariants:**
- Version history is append-only; no deletes or updates to existing version rows. Every world state fact is traceable to a session (D-003).
- The `name` field (and equivalent primary identifier) must be non-blank for all world-state entities. Domain constructors enforce this — an Actor, Space, Event, or Relation cannot be constructed with a null or blank name. The DB `NOT NULL` constraint and adapter Bean Validation are redundant defences, not the primary enforcers. A blank name in `EntityContext` passed to LLM ports produces degraded extraction and resolution results with no error signal.
- `version_number` must be strictly greater than the current highest version number for the entity. `Actor.applyVersion()` throws `DomainException` if the incoming version is not monotonically increasing.

### Entity Embeddings

Post-commit async: OpenAI `text-embedding-3-small` generates a 1536-dimension vector for each committed entity version snapshot. Stored in `entity_embeddings`. Entity versions without a corresponding embedding row are **excluded from Query Mode retrieval** until generation completes (D-063). Two retrieval uses: (1) entity resolution during ingestion (Stage 1 similarity search, D-041); (2) world state context retrieval for Query Mode (§6.4 ARCHITECTURE.md).

**Invariant:** embedding generation is fire-and-forget after commit. The commit endpoint never waits for it.

### Diff Payload

The structured extraction result stored in `sessions.diff_payload` (JSONB) while a session is in `draft` status. Enables failure recovery — users who close the browser mid-review can retrieve the diff on return. Cleared when the session is committed or discarded. The commit endpoint validates the payload before applying it via `CommitService` (D-081), which is the authoritative validation boundary. Eight preconditions must all pass before any world-state write occurs (see `session-ingestion-pipeline` skill §8 for the full list). Returns `422` on violation — defence in depth against UI bypass.

**Invariant:** `diff_payload` is null on any status other than `draft`.

### Refresh Token

SHA-256 hash of the raw token is stored; the raw token is never persisted. Tokens belong to a `family_id` chain. On reuse of an already-consumed token from a family, the entire family is revoked (rotation + reuse detection, D-059). Stored in: `refresh_tokens` table.

**Invariant:** `token_hash` only — raw token never stored. Reuse of a consumed token revokes the entire family.

### Proposals (Data Model Only in v1)

Schema present from day one to avoid a future breaking migration (D-016). The `proposals` and `proposal_votes` tables exist and are writable by domain code. No approval pipeline, no UI affordance activation, no expiry enforcement in v1. Do not implement proposal approval logic until v2.

---

## 7. Backend Conventions

### ARCH-01 — Domain core has zero framework imports

`com.bluesteel.domain` must import nothing from `org.springframework.*` or `jakarta.persistence.*`. Domain entities are plain Java classes or Records. JPA entities live in `adapters.out.persistence` only. Enforced by ArchUnit on every build (D-037). A violation that passes ArchUnit is a bug in the ArchUnit rule, not a green light.

### ARCH-02 — Ports are interfaces in the application layer

All driving ports live in `application.port.in`; all driven ports in `application.port.out`. They are Java interfaces. Application services implement driving ports. Adapter classes implement driven ports. Neither domain nor application code ever imports an adapter class directly. (D-038)

### ARCH-03 — Config classes are co-located with their adapter

`WebConfig` lives in `adapters.in.web`. `PersistenceConfig` in `adapters.out.persistence`. `AiConfig` in `adapters.out.ai`. `SecurityConfig` in `adapters.in.security`. The top-level `config/` package is reserved for cross-cutting beans only (D-039). All config classes are suffixed `Config`.

### ARCH-04 — Spring AI VectorStore is never used

pgvector similarity queries are native SQL via Spring Data JPA or `JdbcTemplate`. `VectorStore` cannot express the domain-specific query shapes required (filter by `entity_type`, `campaign_id`, join through `entity_versions → sessions`). Native queries give full control. (D-062)

### NAMING-01 — Naming conventions

- **Packages:** lowercase, dot-separated, domain-vocabulary. Never `infrastructure`, `api`, or `impl` at the top level.
- **Classes:** PascalCase. Ports suffixed `Port` (e.g., `NarrativeExtractionPort`). Services suffixed `Service`. Controllers suffixed `Controller`. Mappers suffixed `Mapper`. JPA entities suffixed `Entity` or `Jpa` (to distinguish from domain entities with the same name).
- **REST endpoints:** plural nouns, kebab-case path segments: `/campaigns/{id}/sessions/{sid}/diff`.
- **Database tables/columns:** snake_case. Table names plural. Foreign keys named `{table_singular}_id`. Timestamps: `created_at`, `updated_at`, `committed_at`.

### ERR-01 — Error response format

All error responses use the standard envelope:

```json
{
  "errors": [
    { "code": "MACHINE_READABLE_CONSTANT", "message": "Human-readable text", "field": "fieldName or null" }
  ]
}
```

HTTP status mapping: 400 validation/bad request; 401 unauthenticated; 403 unauthorized; 404 not found; 409 conflict; 422 business rule violation; 500 internal. Never leak stack traces or internal implementation details in error messages.

### ERR-02 — Exceptions in domain and application layers

Domain and application layers use unchecked domain exceptions (no `throws` declarations). Adapters catch these at the boundary and map them to the HTTP error model. No checked exceptions in domain or application code.

### LOG-01 — Logging

Every LLM call must be logged at INFO with: tokens in, tokens out, estimated cost, `session_id`, `user_id`, pipeline stage. Failed LLM calls are logged at ERROR with full context. Do not log raw LLM response content at INFO — it can contain sensitive narrative data. Structured logging (JSON) is the target format in prod via `logstash-logback-encoder`. Configuration lives in `logback-spring.xml` with profile-conditional appenders: human-readable pattern on the `local` profile; JSON appender in all other environments. LLM call logging uses MDC to carry `session_id` and `user_id` automatically; `stage`, `tokens_in`, `tokens_out`, and `cost_usd` are emitted as structured fields on every LLM log line. (D-071, D-072)

### DB-01 — Database access pattern

Persistence adapters use Spring Data JPA repositories for standard CRUD. Native SQL (`@Query(nativeQuery=true)` or `JdbcTemplate`) is used for all pgvector similarity queries and any query that cannot be expressed cleanly in JPQL. Transaction boundaries are owned by application services, not repositories. Repositories are never called from domain code.

### DB-02 — Migrations are append-only

Liquibase changelogs in `db/changelog/` are never modified after they have been applied to any environment. New schema changes always create a new changeset. Use Neon database branching to validate migrations before applying to production (D-047).

### AUTH-01 — Authorization at the use-case boundary

Controllers extract `user_id` and `is_admin` from the validated JWT. Campaign-level role resolution (`gm`, `editor`, `player`) happens at the use-case boundary via a DB read against `campaign_members`. Never authorize based on JWT claims alone for campaign-scoped operations — always verify against the live DB (D-043).

### VALID-01 — Three-tier validation model

Validation is distributed across three layers. All three must be present. No single tier is
the sole gatekeeper.

| Tier | Layer | Validates | Mechanism | HTTP status |
|---|---|---|---|---|
| **Format** | Adapter (REST controller) | Null checks, size bounds, enum membership, cross-field constraints (e.g., `matched_entity_id` required when `resolution = MATCH`) | Bean Validation (`@NotNull`, `@Size`, `@AssertTrue`, custom) | 400 |
| **Preconditions** | Application service | Cross-aggregate rules requiring DB reads (single active draft per campaign, UNCERTAIN cards in commit payload, conflict acknowledgment, card completeness against stored diff, entity ownership checks) | Unchecked domain exceptions caught by `GlobalExceptionHandler` | 409 / 422 |
| **Invariants** | Domain entity / aggregate | Single-object business rules derivable from the object's own state (non-blank name, valid state machine transitions, monotonically increasing version numbers, ACTIVE + non-expired check on token consume) | Unchecked domain exceptions thrown in constructors and methods | 422 (via handler) |

**Rules:**
- Domain invariants are enforced regardless of which adapter calls the domain. The adapter's
  Bean Validation is a convenience and the first line of defence; the domain invariant is
  authoritative and the last.
- A rule that requires a DB read cannot live in the domain — it belongs in the application service.
- A rule that can be evaluated from the object's own state **must** live in the domain.
- The controller never contains business logic. Use-case services never contain format validation.

### TEST-01 — Test requirements

Every domain class must have unit tests. Every use-case service must have unit tests with mocked ports. Persistence adapters require integration tests via Testcontainers (real PostgreSQL). The domain core must pass PITest mutation testing at a configured minimum threshold on every build; the application layer runs pre-merge; the full codebase nightly (D-036). No E2E tests — highest confidence tier is integration tests with external services mocked (D-056). ArchUnit rules run on every build.

### API-01 — Response envelope

All responses use:

```json
{ "data": {}, "meta": {}, "errors": [] }
```

`errors` is omitted on success. `meta` carries pagination info (`page`, `size`, `total`) for list responses. All IDs are UUIDs. All timestamps are ISO 8601 UTC.

### API-02 — Pagination strategy

Entity list endpoints (`/actors`, `/spaces`, `/events`, `/relations`) use offset-based pagination. The Timeline endpoint (`/timeline`) uses keyset pagination. (D-055)

---

## 8. Key Backend Files

> ⚠️ These files do not exist yet — they will be created in Phase 1. This section describes what each file will be and why it matters.

| File | Why it matters |
|---|---|
| `pom.xml` | Maven root — all dependency versions, plugin config (PITest, Surefire, Liquibase), Spring profiles |
| `config/ApplicationConfig.java` | Cross-cutting beans (e.g., `@Async` executor config, shared clock bean) |
| `adapters/in/security/SecurityConfig.java` | Spring Security filter chain definition, JWT validation filter wiring, public vs protected routes |
| `adapters/in/security/JwtAuthenticationFilter.java` | Per-request JWT validation; sets `SecurityContext` with `user_id` and `is_admin` |
| `adapters/in/web/WebConfig.java` | CORS config, Jackson serialization settings (ISO dates, UUID serialization) |
| `adapters/in/web/GlobalExceptionHandler.java` | Maps domain exceptions to HTTP error envelope; handles `@ControllerAdvice` |
| `adapters/out/persistence/PersistenceConfig.java` | Datasource, JPA/Hibernate settings, Liquibase integration |
| `adapters/out/ai/AiConfig.java` | Spring AI `ChatClient` and `EmbeddingModel` bean definitions; profile-conditional mock vs real |
| `adapters/out/ai/MockNarrativeExtractionAdapter.java` | Local dev mock — returns canned extraction results; zero API cost (D-049) |
| `application/service/SessionIngestionService.java` | Orchestrates the full ingestion pipeline: intake → extraction → resolution → conflict detection → diff generation |
| `application/service/CommitService.java` | Validates commit payload, writes world state, fires async embedding event |
| `application/service/QueryService.java` | Orchestrates query pipeline: embed → vector search → context assembly → LLM call |
| `domain/session/Session.java` | Session aggregate — owns state machine transitions, enforces terminal-status invariants |
| `domain/worldstate/actor/Actor.java` | Actor aggregate — owns version history, enforces append-only invariant |
| `test/architecture/ArchitectureTest.java` | ArchUnit rules — enforces layer boundaries on every build (D-037) |
| `resources/db/changelog/db.changelog-master.xml` | Liquibase root changelog — includes all versioned migration files |

---

## 9. Common Backend Workflows

### Adding a new API endpoint end-to-end

1. Define the request and response DTO Records in `adapters.in.web.<domain>/` (e.g., `CreateActorRequest`, `ActorResponse`).
2. Define the use-case interface (driving port) in `application.port.in/` (e.g., `CreateActorUseCase`).
3. Write a unit test for the use-case service (ports mocked) — TDD: write the failing test first.
4. Implement the use-case service in `application.service/`, implementing the driving port.
5. If new persistence is needed: define the driven port interface in `application.port.out/`, write an integration test (Testcontainers), implement the JPA adapter in `adapters.out.persistence/`.
6. Add the REST controller method in `adapters.in.web.<domain>/<Domain>Controller.java`. Controller calls the driving port — never the service class directly.
7. Apply role guard at the use-case boundary (not in the controller). Resolve campaign role via `CampaignMembershipPort` if the endpoint is campaign-scoped.
8. Register response envelope wrapping and any new error codes in `GlobalExceptionHandler`.
9. Verify the ArchUnit tests still pass.

### Adding a new database model and migration

1. Create a new Liquibase changeset file in `resources/db/changelog/` following the naming convention (e.g., `0012_add_proposals_table.xml`). Include it in `db.changelog-master.xml`.
2. Define UUIDs as PKs, explicit FKs, `created_at` and (if mutable) `updated_at` columns, `campaign_id` and `owner_id` on domain entities (D-021). No nullable columns without justification.
3. Create the JPA entity class in `adapters.out.persistence/` (JPA annotations here only — never in `domain/`).
4. Create the Spring Data repository interface.
5. Create the mapper (domain object ↔ JPA entity) in the same persistence adapter package.
6. Define the driven port interface in `application.port.out/`.
7. Write an integration test via Testcontainers before wiring the adapter.
8. Validate the changeset against a Neon branch before applying to production (D-047).

### Deleting an annotation

Annotations are immutable in content (no `updated_at`, no edit endpoint) but can be deleted by
the author or the GM.

1. Driving port: `DeleteAnnotationUseCase.delete(UUID campaignId, UUID annotationId, UUID callerId)`.
2. Authorization: resolve campaign role via `CampaignMembershipPort`. Allow if `callerId` matches
   `annotation.authorId` OR caller's campaign role is `GM`. Throw `UnauthorizedException` otherwise.
3. `DELETE /api/v1/campaigns/{id}/annotations/{aid}` → 204 No Content on success.
4. No world state impact — deleting an annotation never touches `actors`, `spaces`, `events`,
   `relations`, or any version history. The deletion is permanent (no soft-delete).
5. If the annotation does not exist or belongs to a different campaign, return 404.

---

### Adding a new background job

1. Define a Spring `ApplicationEvent` record in `application/` (e.g., `SessionCommittedEvent`).
2. Publish the event from the relevant application service using `ApplicationEventPublisher` (injected via driven port or directly — cross-cutting concern acceptable here).
3. Create an `@EventListener` + `@Async` method in `adapters.out.<relevant>/`. This is an adapter concern, not domain logic.
4. Configure the `@Async` executor in `ApplicationConfig` if custom thread-pool settings are needed.
5. Write an integration test that verifies the event is published and the listener is invoked with the correct payload.

### Integrating a new external service

1. Define the driven port interface in `application.port.out/` (e.g., `EmailPort`). The interface signature uses only domain types — no provider SDK types.
2. Create a mock adapter in `adapters.out.<service>/Mock<Service>Adapter.java` — activated on the `local` profile.
3. Create the real adapter in `adapters.out.<service>/<Service>Adapter.java` — activated on `llm-real` or `prod` profile.
4. Wire both in `AiConfig` (or a new `<Service>Config`) using `@Profile` or `@ConditionalOnProperty`.
5. Write unit tests for any non-trivial adapter logic (response mapping, error handling) with the provider SDK mocked.
6. Add the required environment variable to `.env.example` and the "Environment variables" section of root `CLAUDE.md`.

### Writing a backend test

1. **Domain unit test** — pure JUnit 5 + Mockito. No Spring context. No Testcontainers. Tests a single domain class or aggregate in isolation.
2. **Application unit test** — JUnit 5 + Mockito. Mock all driven ports. Test use-case service logic only: input validation, orchestration, output mapping.
3. **Persistence integration test** — JUnit 5 + Testcontainers (`@SpringBootTest` slice or `@DataJpaTest`). Spins up a real PostgreSQL container. Tests repository methods, native pgvector queries, and Liquibase migration correctness.
4. **ArchUnit test** — extends `ArchitectureTest`. Add a new rule to the existing rule set; do not create a separate test class.
5. Never use `@SpringBootTest` for domain or application layer tests — the full context is heavyweight and unnecessary for those layers.

---

## 10. Backend-Specific Gotchas

**Spring Boot 4.x compatibility gate (D-057).** Spring Boot 4.0.3 is a recent major release. Before Phase 1 begins, verify that Spring AI (`ChatClient`, `EmbeddingModel`), Testcontainers Spring Boot starter, Liquibase Spring Boot starter, and Spring Security 7 are all compatible with Boot 4.x. Any dependency lagging on Boot 4.x support must be resolved before the first line of production code is written.

**Authorization is not stateless (D-043).** The JWT carries only `user_id` and `is_admin`. Campaign role is resolved via DB read on every campaign-scoped request. Do not attempt to cache or encode campaign roles in the JWT — role changes must take effect immediately (a removed player retains read access for up to 15 minutes under the current TTL; encoding roles in the JWT would extend this to 15 minutes at minimum, which is unacceptable).

**Single active draft per campaign (D-054).** Attempting to submit a second session while one is in `processing` or `draft` returns `409`. This constraint is enforced at the application layer but also indirectly at the DB level via the unique constraint on `(campaign_id, status)` for draft states. Be aware of this when writing ingestion tests — each test campaign needs a clean session state.

**Embedding generation is async and not immediate (D-063).** Entity versions committed in a session will not appear in Query Mode vector retrieval until embedding generation completes (typically seconds). Tests that exercise the query pipeline after a commit must either wait for embedding completion or mock the `EmbeddingPort`. Do not assert query results synchronously after a commit without accounting for this.

**`CommitService` has eight mandatory validation preconditions (D-078–D-081).** Missing any one is a test gap, not a "nice-to-have." The full list: (1) all `card_id` values reference existing diff cards → `422 UNKNOWN_CARD_ID`; (2) no duplicate `card_id` entries → `422 DUPLICATE_CARD_DECISION`; (3) every non-UNCERTAIN diff card has an explicit decision → `422 INCOMPLETE_CARD_DECISIONS`; (4) all UNCERTAIN cards are resolved → `422 UNCERTAIN_ENTITIES_PRESENT`; (5) all ConflictCards acknowledged → `422 CONFLICTS_NOT_ACKNOWLEDGED`; (6) `matched_entity_id` non-null for MATCH resolutions → `400` (adapter); (7) `matched_entity_id` belongs to the campaign → `422 INVALID_ENTITY_REFERENCE`; (8) no `add` action in v1 → `422 UNSUPPORTED_ACTION`. A mandatory unit test suite must prove every check fires before `session.commit()` is called. See `session-ingestion-pipeline` skill §8.

**UNCERTAIN entities block commit at the backend (D-042).** The backend performs independent validation of the commit payload and returns `422` if `UNCERTAIN` entities are present — regardless of what the frontend sent. Tests for the commit endpoint must include a case where the payload contains `UNCERTAIN` entities and assert the `422` response with error code `UNCERTAIN_ENTITIES_PRESENT`.

**Conflict acknowledgment is required (D-033).** If the diff generation detected conflicts, the commit payload must include `acknowledged_conflicts` with entries for each detected conflict. An empty `acknowledged_conflicts` array on a session that had conflicts returns `422` with error code `CONFLICTS_NOT_ACKNOWLEDGED`. This is another defence-in-depth server check the frontend is expected to respect — but the backend enforces it independently.

**Proposal schema is present but inactive in v1 (D-016).** The `proposals` and `proposal_votes` tables exist from day one. Do not implement proposal approval logic, proposal status transitions beyond initial creation, TTL enforcement, or any UI-facing workflow until v2. The data model is a schema placeholder.

**`admin` is a singleton enforced at the DB level (D-025).** The `users` table has a partial unique index `WHERE is_admin = TRUE`. Attempting to create a second admin user will fail at the DB level with a unique constraint violation. This invariant must also be enforced at the application layer — the admin creation path (seeding or first-run bootstrap) must check before inserting.

**pgvector queries are native SQL only (D-062).** There is no Spring AI `VectorStore` in this codebase. If you add a vector similarity query, write it as native SQL via `@Query(nativeQuery=true)` or `JdbcTemplate`. JPQL cannot express `<->` (L2 distance) or `<=>` (cosine distance) operators.

**Campaign-level annotations cannot modify world state (D-010, D-011).** Annotations are non-canonical — any campaign member can write one, but they are never factored into query retrieval, extraction results, or entity version history. If you find yourself reading annotations in the extraction or query pipelines, that is a bug.

---

## 11. Relevant Skills

> The full skill index is at `skills/SKILLS_INDEX.md`. ✅ Created.

Backend-relevant skills:

- **`session-ingestion-pipeline`** — full extraction → resolution → conflict detection → diff → commit pipeline, including mock adapter usage.
- **`database-migration`** — Liquibase changeset creation, pgvector schema, Neon branch validation.
- **`backend-endpoint`** — end-to-end endpoint addition (controller → port → service → adapter).
- **`backend-domain-model`** — domain entity + world state versioning pattern.
- **`backend-testing`** — all four test tiers: domain unit, application unit, Testcontainers, ArchUnit, PITest (includes mutation testing scope and surviving-mutant interpretation).
- **`query-pipeline`** — Query Mode: embed → pgvector search → context assembly → LLM answer.
- **`auth`** — JWT issuance, refresh token rotation (D-059), Spring Security filter chain, admin bootstrap.
