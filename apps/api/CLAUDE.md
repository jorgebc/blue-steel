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
| LLM generation | Spring AI `ChatClient` → Anthropic (`llm-real`) or Ollama (`llm-ollama`, D-088) |
| LLM embeddings | Spring AI `EmbeddingModel` → OpenAI `text-embedding-3-small` (1536) or Ollama `bge-m3` (1024) per profile (D-088) |
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
# Infra (from repo root)
podman compose up -d                              # start Postgres+pgvector (or: docker compose up -d)

# All Maven commands run from apps/api/ (no root pom.xml — -pl does not work)
cd apps/api

mvn spring-boot:run -Dspring-boot.run.profiles=local                  # mock LLMs (zero cost)
mvn spring-boot:run -Dspring-boot.run.profiles=local,llm-real         # real Anthropic+OpenAI
mvn spring-boot:run -Dspring-boot.run.profiles=local,llm-ollama       # real LOCAL models via Ollama — offline, zero cost (D-088)

mvn spotless:check                               # format check (google-java-format)
mvn spotless:apply                               # auto-fix formatting
mvn test                                         # unit + ArchUnit (fast; also runs spotless:check)
mvn verify                                       # + Testcontainers IT (Podman/Docker required)
mvn test-compile pitest:mutationCoverage         # mutation tests — domain core (slow)
mvn package -DskipTests                          # production JAR

# SonarQube — local-only quality gate, not part of CI. Requires the
# sonarqube-local Podman container to be running and $SONAR_TOKEN in env.
mvn sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=$SONAR_TOKEN -Dsonar.projectKey=blue-steel-api
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
- Provider + dimension are per-profile: OpenAI@1536 (`llm-real`/prod), Ollama `bge-m3`@1024 (`llm-ollama`, offline). Column is `vector(${embeddingDimension})` via a Liquibase parameter; vectors from different models are not comparable — never mix models in one DB (D-088)

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

**Testing — Mockito Matchers (TEST-02):** Never use `any()`, `anyString()`, `anyBoolean()`, or other wildcard matchers — not even `eq()` wrappers. Use the actual expected value directly in the stub or verify call. When the argument is constructed internally by the SUT (not passed in from outside), use `ArgumentCaptor` and assert on the captured value. The only exception is `never()` verifications and genuinely unknowable arguments (e.g. randomly-generated values): use a typed class matcher there (`any(MyClass.class)`).

**SonarQube (local-only):** AI-pipeline BE-engineer gate — `mvn sonar:sonar` (command in §3, setup in repo-root `CLAUDE.md` §6). Filters issues to branch-modified files; ≤2 fix attempts; not in CI.

**LLM profiles (D-049, D-088):** three provider selections layered on `local` — mock (`@Profile("!llm-real & !llm-ollama")`, default dev), `llm-real` (Anthropic + OpenAI), `llm-ollama` (Ollama, offline). Adapters are provider-neutral (`SpringAi*`, `@Profile("llm-real | llm-ollama")`); `AiConfig` picks the active `ChatModel`/`EmbeddingModel` bean per profile. Ollama models + dimension are env-overridable (`OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `EMBEDDING_DIMENSION`). Changing the Ollama embedding model to a different dimension → update `EMBEDDING_DIMENSION` and recreate the local DB (`docker compose down -v`).

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
