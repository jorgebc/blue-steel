# CLAUDE.md — Backend (`apps/api`)

> Read repo-root `CLAUDE.md` first. This file covers backend-only concerns.
> For any non-trivial task, also check `skills/SKILLS_INDEX.md`.

---

## 1. Stack

| Concern | Choice |
|---|---|
| Language | Java 25 (LTS) |
| Framework | Spring Boot 4.0.3 |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Liquibase |
| Auth | Spring Security (JWT HS256) |
| LLM generation | Spring AI `ChatClient` → Anthropic |
| LLM embeddings | Spring AI `EmbeddingModel` → OpenAI `text-embedding-3-small` (1536 dims) |
| Vector retrieval | **Native pgvector SQL only** — `VectorStore` never used (D-062) |
| Testing | JUnit 5, Mockito, Testcontainers, PITest, ArchUnit |
| Style | Spotless + google-java-format |

---

## 2. Directory Structure

```
apps/api/src/main/java/com/bluesteel/
├── domain/            ← Pure Java; zero Spring/JPA imports (ARCH-01)
│   ├── campaign/ session/ worldstate/{actor,space,event,relation}/ annotation/ proposal/
├── application/
│   ├── port/in/       ← Driving ports (use-case interfaces)
│   ├── port/out/      ← Driven ports (repository + service interfaces)
│   └── service/       ← Use-case implementations (orchestration only)
├── adapters/
│   ├── in/web/        ← REST controllers, DTOs, GlobalExceptionHandler, WebConfig
│   ├── in/security/   ← SecurityConfig, JwtAuthenticationFilter
│   └── out/
│       ├── persistence/ ← JPA entities, repositories, mappers, PersistenceConfig
│       └── ai/          ← Spring AI adapters + mock adapters, AiConfig
└── config/            ← Cross-cutting beans only (ApplicationConfig)

src/main/resources/
├── application.yml / application-local.yml / application-llm-real.yml
└── db/changelog/      ← Liquibase changelogs — NEVER edit after applying

src/test/java/com/bluesteel/
├── architecture/      ← ArchUnit rules (ArchitectureTest.java)
├── domain/            ← Domain unit tests (no Spring)
├── application/       ← Use-case unit tests (Mockito)
└── adapters/in/ + out/ ← @WebMvcTest + Testcontainers integration tests
```

---

## 3. Run Commands

```bash
# All commands from repo root; -pl apps/api scopes Maven to backend

podman compose up -d                                          # start Postgres+pgvector (or: docker compose up -d)

mvn spring-boot:run -pl apps/api \
  -Dspring-boot.run.profiles=local                           # mock LLMs (zero cost)
mvn spring-boot:run -pl apps/api \
  -Dspring-boot.run.profiles=local,llm-real                  # real Anthropic+OpenAI

mvn spotless:check -pl apps/api                              # format check (google-java-format)
mvn spotless:apply -pl apps/api                              # auto-fix formatting
mvn test -pl apps/api                                        # unit + ArchUnit (fast; also runs spotless:check)
mvn verify -pl apps/api                                      # + Testcontainers IT (Podman/Docker required)
mvn test-compile pitest:mutationCoverage -pl apps/api        # mutation tests — domain core (slow)
mvn package -DskipTests -pl apps/api                         # production JAR
```

**CI step order** (mirrors `backend.yml`): `spotless:check → compile → test → verify → pitest`

---

## 4. Architecture Rules (ArchUnit-enforced)

| Rule | Package constraint |
|---|---|
| **ARCH-01** | `domain` has zero imports from `org.springframework.*` or `jakarta.persistence.*` |
| **ARCH-02** | Ports are interfaces in `application.port.in/out`; adapters implement them; domain never imports adapters |
| **ARCH-03** | Config classes co-located with their adapter (`WebConfig` in `adapters.in.web`, etc.) |
| **ARCH-04** | Spring AI `VectorStore` never used; all pgvector = native SQL (D-062) |
| **ARCH-05** | `adapters.in` never imports from `application.port.out` — controllers must call driving port interfaces (`port/in`) only; only application services may inject driven ports (`port/out`) |
| **ARCH-06** | `adapters.in` never imports from `adapters.out` — no direct controller-to-adapter wiring |
| **ARCH-07** | Everything in `application.port.in.*` and `application.port.out.*` must be an interface — shared value types go in `application.model.*` |
| **ARCH-08** | All port interfaces must live in a domain concept sub-package, never at the root of `port/in` or `port/out` |

**Dependency flow (never deviate):** `adapter/in → port/in → application/service → port/out → adapter/out`

**port/out sub-packages:** `application.port.out` must be organized into domain sub-packages (e.g., `port/out/health/`, `port/out/session/`). Never dump interfaces at the root of `port/out`. Mirror the sub-package structure in `port/in` and `application/service`.

A failing ArchUnit test is a layer violation in production code — fix the code, not the rule.

---

## 5. Domain Concepts & Key Invariants

**Session lifecycle:** `pending → processing → draft → committed | failed | discarded`
- At most 1 session per campaign in `processing` or `draft` (D-054) — new submission → `409`
- `committed` is immutable; `discarded` is terminal (GM only, from `draft`)
- `diff_payload` is populated only in `draft` status; cleared on commit or discard

**World State Entities (Actor, Space, Event, Relation):**
- Two-table versioning: `actors` (head) + `actor_versions` (append-only history)
- Current state = max `version_number`; point-in-time = max version where `session_id ≤ target`
- Version history is **never updated or deleted** (D-001, D-003)
- All carry `campaign_id` + `owner_id` (D-021); name must be non-blank

**Commit validation — 8 mandatory checks** (CommitService, D-078–D-081):
1. All `card_id` values exist in stored diff → `422 UNKNOWN_CARD_ID`
2. No duplicate `card_id` entries → `422 DUPLICATE_CARD_DECISION`
3. Every non-UNCERTAIN card has an explicit decision → `422 INCOMPLETE_CARD_DECISIONS`
4. All UNCERTAIN cards resolved → `422 UNCERTAIN_ENTITIES_PRESENT`
5. All ConflictCards acknowledged → `422 CONFLICTS_NOT_ACKNOWLEDGED`
6. `matched_entity_id` non-null for MATCH → `400` (adapter Bean Validation)
7. `matched_entity_id` belongs to same campaign → `422 INVALID_ENTITY_REFERENCE`
8. No `add` action in v1 → `422 UNSUPPORTED_ACTION`

**Auth:**
- JWT carries only `user_id` + `is_admin`. Campaign role resolved from `campaign_members` via DB on every authorized request (AUTH-01, D-043).
- Refresh token: SHA-256 hash stored only; family-based reuse detection (D-059)
- Singleton admin enforced by partial unique index `WHERE is_admin = TRUE` (D-025)

**Entity Embeddings:**
- Generated async post-commit via `@Async` / `ApplicationEvent` (D-063)
- Entity versions without embeddings excluded from Query Mode retrieval
- Commit returns `200` before embeddings are generated

---

## 6. Validation Model (VALID-01)

| Tier | Layer | Mechanism | Status |
|---|---|---|---|
| Format | REST controller | Bean Validation (`@NotNull`, `@Size`, `@AssertTrue`) | 400 |
| Preconditions | Application service | DB-read cross-aggregate checks | 409/422 |
| Invariants | Domain entity | Constructor + method enforcement | 422 (via handler) |

Never put business logic in controllers. Never put format validation in services.

---

## 7. Other Key Conventions

**Naming (NAMING-01):** Use-case interfaces suffixed `UseCase`; services `Service`; controllers `Controller`; JPA entities `Entity` or `Jpa`; ports `Port` or `Repository`; mappers `Mapper`.

**Errors (ERR-01):** All errors use `{ "errors": [{ "code": "...", "message": "...", "field": null }] }`. Never leak stack traces.

**DB (DB-01/02):** JPA for standard CRUD; native SQL for all pgvector queries. Liquibase changelogs are **append-only**.

**Logging (LOG-01):** Every LLM call logged at INFO with `tokens_in`, `tokens_out`, `cost_usd`, `session_id`, `user_id`, `stage`. No raw LLM response content at INFO (may contain narrative data). JSON appender in prod via `logstash-logback-encoder`.

**Logging (LOG-02):** One static `Logger` per class (`LoggerFactory.getLogger`). Domain layer: no logging — domain must not depend on infrastructure. Application services: INFO on use-case entry/exit with minimum business IDs; ERROR on unhandled exceptions. Adapters out: ERROR on infrastructure failures with full exception; never silently swallow a caught exception. Never log at INFO on high-throughput paths (e.g. health check polling). Never log passwords, tokens, or PII. Local profile: `com.bluesteel` packages at DEBUG; third-party at WARN. Prod profile: project at INFO; third-party at WARN; DEBUG disabled.

**Testing (TEST-01):** Every domain class → unit tests. Every use-case service → unit tests with mocked ports. Persistence adapters → Testcontainers IT. Domain core → PITest on every build.

**Proposals schema (D-016):** `proposals` + `proposal_votes` tables exist from day one but the approval pipeline ships in v2. Do NOT implement proposal approval logic.

---

## 8. Relevant Skills

- **`session-ingestion-pipeline`** — full ingestion pipeline (intake → extraction → resolution → conflict → diff → commit)
- **`query-pipeline`** — Query Mode: embed → pgvector → LLM answer → citations
- **`backend-endpoint`** — end-to-end endpoint addition (controller → port → service → adapter)
- **`backend-domain-model`** — domain entity + world state versioning pattern
- **`backend-testing`** — all test tiers: domain unit, application unit, Testcontainers IT, ArchUnit, PITest
- **`database-migration`** — Liquibase changeset creation, pgvector schema, Neon branch validation
- **`auth`** — JWT issuance, refresh rotation, Spring Security filter chain, admin bootstrap, invitation flow
- **`security-hardening`** — HTTP security headers, CORS policy, BCrypt DoS protection, password policy, credential logging rules
- **`spring-ai-llm-adapter`** — ChatClient + EmbeddingModel usage, mock adapter wiring, structured output, cost logging
- **`error-handling`** — GlobalExceptionHandler, domain exception hierarchy, three-tier validation
