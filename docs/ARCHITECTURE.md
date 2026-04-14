# ARCHITECTURE — Blue Steel

---

## 1. Philosophy

Blue Steel is built on five engineering references that govern every structural decision in this document:

| Reference | Principle applied |
|---|---|
| **Robert C. Martin** | SOLID, clean code, single responsibility at every layer |
| **Alistair Cockburn** | Hexagonal architecture — the domain is isolated from all infrastructure |
| **Joshua Bloch** | API design done right — minimal surface, clear contracts, no surprises |
| **Kent Beck** | TDD from day one — no production code without a failing test first |
| **Martin Fowler** | Evolutionary architecture — design for change, not for today's assumptions |

These are not aspirations. They are constraints. When a shortcut conflicts with them, the shortcut loses.

---

## 2. Repository Structure

Blue Steel is a single monorepo. Two independent toolchains share one repository — they do not share code.

```
blue-steel/
├── docs/                  ← all project documentation
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── ROADMAP.md
│   └── DECISIONS.md
├── apps/
│   ├── api/               ← Java 25 / Spring Boot 4.0.3 backend
│   └── web/               ← React / Vite / TypeScript frontend
├── CLAUDE.md              ← repo root; picked up automatically by tooling
├── README.md
└── .gitignore
```

`apps/api` and `apps/web` are separate projects with separate build tools, separate dependency managers, and separate test suites. There is no shared code layer between them. The contract between them is the HTTP API.

---

## 3. Backend Architecture

### 3.1 Stack

| Component | Choice | Version |
|---|---|---|
| Language | Java | 25 (LTS) |
| Framework | Spring Boot | 4.0.3 |
| Build tool | Maven | latest stable |
| LLM integration | Spring AI (`ChatClient`, `EmbeddingModel`) | aligned with Boot 4.x |
| LLM provider (generation) | Anthropic (Claude) | via Spring AI `ChatClient` |
| LLM provider (embeddings) | OpenAI (`text-embedding-3-small`) | via Spring AI `EmbeddingModel` |
| Vector similarity retrieval | Native pgvector queries via Spring Data JPA / `JdbcTemplate` | — |
| ORM | Spring Data JPA + Hibernate | aligned with Boot 4.x |
| Database driver | PostgreSQL JDBC | latest stable |
| Migration | Liquibase | latest stable |
| Auth | Spring Security | aligned with Boot 4.x |
| Testing | JUnit 5 + Mockito + Testcontainers + PITest + ArchUnit | latest stable |

### 3.2 Hexagonal Architecture

The backend follows Cockburn's Ports & Adapters pattern strictly. The domain never depends on adapters. Adapters always depend on the domain.

```
┌─────────────────────────────────────────────────────┐
│                   DRIVING ADAPTERS                  │
│         (REST controllers, scheduled tasks)         │
├─────────────────────────────────────────────────────┤
│                   DRIVING PORTS                     │
│         (use case interfaces — input ports)         │
├─────────────────────────────────────────────────────┤
│                                                     │
│                  DOMAIN CORE                        │
│    (entities, aggregates, domain services,          │
│     domain events — zero framework imports)         │
│                                                     │
├─────────────────────────────────────────────────────┤
│                   DRIVEN PORTS                      │
│   (repository interfaces, LLM port, email port)     │
├─────────────────────────────────────────────────────┤
│                   DRIVEN ADAPTERS                   │
│  (JPA repositories, Spring AI adapter, SMTP, etc.)  │
└─────────────────────────────────────────────────────┘
```

**Rules that are never violated:**

- The domain core has zero imports from `org.springframework.*`, `jakarta.persistence.*`, or any infrastructure library
- Ports are Java interfaces defined in the application layer — the domain has no knowledge of ports
- Adapters implement ports — they are never called directly by domain code
- Dependency inversion is enforced structurally, not by convention

### 3.3 Layer Map

| Layer | Package | Responsibility |
|---|---|---|
| Domain | `com.bluesteel.domain` | Entities, value objects, aggregates, domain events, domain services |
| Application | `com.bluesteel.application` | Use case orchestration, input port interfaces, output port interfaces |
| Adapters (driving) | `com.bluesteel.adapters.in` | REST controllers, request/response DTOs, exception handlers, Spring Security filter chain |
| Adapters (driven) | `com.bluesteel.adapters.out` | JPA adapters, Spring AI adapter |

### 3.4 Package Structure

```
apps/api/src/main/java/com/bluesteel/
├── domain/
│   ├── campaign/
│   ├── session/
│   ├── worldstate/
│   │   ├── actor/
│   │   ├── space/
│   │   ├── event/
│   │   └── relation/
│   ├── annotation/
│   └── proposal/
├── application/
│   ├── port/
│   │   ├── in/            ← driving ports (use case interfaces)
│   │   └── out/           ← driven ports (repository and service interfaces)
│   └── service/           ← use case implementations
├── adapters/
│   ├── in/
│   │   ├── web/           ← REST controllers, DTOs, exception handlers
│   │   │   └── WebConfig.java
│   │   └── security/      ← Spring Security filter chain, JWT validation filter
│   │       └── SecurityConfig.java
│   └── out/
│       ├── persistence/   ← JPA entities, repositories, mappers
│       │   └── PersistenceConfig.java
│       └── ai/            ← Spring AI adapters (extraction, embedding, conflict)
│           └── AiConfig.java
└── config/                ← cross-cutting config only (no adapter home)
    └── ApplicationConfig.java
```

### 3.5 Domain Model Conventions

- Domain entities are plain Java classes — no JPA annotations, no Spring annotations
- Value objects are Java Records — immutable by construction
- Aggregates enforce their own invariants — no anemic domain model
- JPA entities live exclusively in `adapters.out.persistence` — they are a persistence detail, not the domain model
- Mappers translate between domain objects and JPA entities at the adapter boundary

### 3.6 Java Conventions

- **Records** for immutable value objects, DTOs, and command/query objects
- **Lombok** only where Records are insufficient — primarily JPA entities requiring builders
- **Java 25 features** — pattern matching, sealed classes, virtual threads (enabled via Spring Boot 4.x defaults)
- Checked exceptions are not used in domain or application layers — domain uses unchecked domain exceptions
- All public API methods are documented; internal implementation methods are not

### 3.7 Configuration Conventions

Configuration classes are co-located with the adapter they configure — not gathered into a shared package. A config class belongs to the adapter it wires.

| Config class | Package | Configures |
|---|---|---|
| `WebConfig` | `adapters.in.web` | CORS, serialization, web filters |
| `SecurityConfig` | `adapters.in.security` | Spring Security filter chain, JWT validation filter, auth rules |
| `PersistenceConfig` | `adapters.out.persistence` | Datasource, JPA settings |
| `AiConfig` | `adapters.out.ai` | Spring AI beans, model config, API keys |
| `ApplicationConfig` | `config` | Cross-cutting beans with no adapter home |

**Rules:**
- All configuration classes are suffixed `Config` — making them findable by name regardless of package
- `config/` at the top level is reserved exclusively for cross-cutting configuration that cannot be attributed to a specific adapter
- If a config class can be attributed to an adapter, it lives in that adapter's package — never in `config/`

---

## 4. Frontend Architecture

### 4.1 Stack

| Component | Choice | Version |
|---|---|---|
| Language | TypeScript | latest stable |
| Framework | React | 18.x |
| Build tool | Vite | latest stable |
| Component library | shadcn/ui | latest stable |
| Server state | TanStack Query | v5 |
| Client state | Zustand | v5 |
| Routing | React Router | v6 |
| Graph visualization | React Flow | v12 |
| HTTP client | Fetch API + typed client | — |

### 4.2 Structure

```
apps/web/src/
├── api/               ← typed HTTP client, one file per API resource
├── components/
│   ├── ui/            ← shadcn/ui primitives (auto-generated, not hand-edited)
│   └── domain/        ← domain-aware components (DiffCard, EntityProfile, etc.)
├── features/
│   ├── input/         ← Input Mode screens and logic
│   ├── query/         ← Query Mode screens and logic
│   └── exploration/   ← Exploration Mode screens and logic
│       ├── timeline/
│       ├── entities/
│       ├── spaces/
│       └── relations/ ← React Flow graph
├── store/             ← Zustand stores (auth, campaign context, UI state)
├── hooks/             ← shared custom hooks
├── types/             ← TypeScript types mirroring API contracts
└── main.tsx
```

### 4.3 State Management

Two distinct state concerns are kept separate:

| Concern | Tool | Examples |
|---|---|---|
| Server state | TanStack Query | World state entities, session list, query responses |
| Client state | Zustand | Active campaign, current interaction mode, auth context, UI flags |

Zustand stores are small and focused — one store per concern, not one global store.

### 4.4 API Contract

The frontend consumes the backend REST API through a typed client layer in `src/api/`. Each resource maps to one file. All request and response shapes are typed as TypeScript interfaces that mirror the backend DTOs.

There is no code generation in v1 — types are hand-written and kept in sync manually. If drift becomes a problem in v2, OpenAPI generation is the upgrade path.

---

## 5. Database Architecture

### 5.1 Core Tables

These tables are the structural foundation. Every other domain table references `campaign_id` and/or `owner_id` — those FKs resolve here.

```
users
  id UUID PK
  email TEXT UNIQUE NOT NULL
  password_hash TEXT NOT NULL
  is_admin BOOLEAN NOT NULL DEFAULT FALSE
  created_at TIMESTAMP
  UNIQUE INDEX ON users (is_admin) WHERE is_admin = TRUE   ← enforces singleton admin at DB level

campaigns
  id UUID PK
  name TEXT NOT NULL
  created_by UUID FK → users.id   ← admin user who created the campaign
  created_at TIMESTAMP

campaign_members
  id UUID PK
  campaign_id UUID FK → campaigns.id
  user_id UUID FK → users.id
  role TEXT                        ← 'gm' | 'editor' | 'player'
  joined_at TIMESTAMP
  UNIQUE (campaign_id, user_id)
  UNIQUE INDEX ON campaign_members (campaign_id) WHERE role = 'gm'   ← exactly one GM per campaign

refresh_tokens
  id UUID PK
  user_id UUID FK → users.id
  token_hash TEXT NOT NULL         ← SHA-256 of the raw token; raw token never stored
  family_id UUID NOT NULL          ← groups a login session's rotation chain
  expires_at TIMESTAMP NOT NULL
  used_at TIMESTAMP                ← nullable; set when exchanged for a new token
  created_at TIMESTAMP
```

The `admin` role is platform-level and is not stored in `campaign_members`. Admin identity is resolved via the `is_admin` flag on the `users` record. Only one user may have `is_admin = TRUE` — this singleton invariant is enforced at both the DB level (partial unique index on `is_admin WHERE is_admin = TRUE`) and the application layer.

---

### 5.2 Stack

| Component | Choice |
|---|---|
| Database | PostgreSQL (latest stable) |
| Vector extension | pgvector |
| Migration tool | Liquibase |
| Access layer | Spring Data JPA + Hibernate |

A single PostgreSQL instance handles both relational world state and the vector/semantic layer for Query Mode. Polyglot persistence is not justified at this scale.

### 5.3 Sessions and Narrative Blocks

```
sessions
  id UUID PK
  campaign_id UUID FK → campaigns.id
  owner_id UUID FK → users.id   ← user who submitted the session
  sequence_number INTEGER NULL   ← ordinal within the campaign; assigned at commit, null until then (D-069)
  UNIQUE (campaign_id, sequence_number)   ← nulls are not considered duplicates; constraint applies to committed sessions only
  status TEXT                    ← 'pending' | 'processing' | 'draft' | 'committed' | 'failed' | 'discarded'
  UNIQUE INDEX sessions_one_active_per_campaign ON sessions (campaign_id) WHERE status IN ('processing', 'draft')
                                 ← enforces D-054: at most one active session per campaign at the DB level
  diff_payload JSONB             ← nullable; populated when status = 'draft'; cleared on commit or discard
  failure_reason TEXT            ← nullable; populated when status = 'failed'
  committed_at TIMESTAMP         ← nullable; set when status transitions to 'committed'
  created_at TIMESTAMP
  updated_at TIMESTAMP           ← updated on every status transition; use to determine when a session failed or was discarded

narrative_blocks
  id UUID PK
  session_id UUID FK → sessions.id
  raw_summary_text TEXT     ← the original submitted text, immutable after storage
  token_count INTEGER       ← estimated at intake, used for budget check
  created_at TIMESTAMP
```

### 5.4 World State Versioning

World state is versioned at the entity level. Each domain entity carries an append-only version history. This is the direct implementation of D-001, D-003, and OQ-3.

**Pattern applied to all versioned entities (Actor, Space, Relation, Event):**

```
actors
  id UUID PK
  campaign_id UUID FK
  owner_id UUID FK
  name TEXT
  created_at TIMESTAMP
  created_in_session_id UUID FK

actor_versions
  id UUID PK
  actor_id UUID FK → actors.id
  session_id UUID FK → sessions.id
  version_number INTEGER
  changed_fields JSONB        ← delta only (D-006)
  full_snapshot JSONB         ← complete state at this version
  created_at TIMESTAMP
```

- **Current state** = latest version row per entity (max version_number)
- **Point-in-time state** = latest version row where session_id ≤ target session
- `changed_fields` stores the delta — what changed in this session
- `full_snapshot` stores the complete entity state at this version for efficient point-in-time reads without reconstruction

### 5.5 Vector Layer

Embeddings are stored in PostgreSQL via pgvector. Each versioned entity snapshot generates an embedding after commit, stored alongside the entity version. Embedding generation is **asynchronous** — the commit endpoint returns immediately after writing world state; embeddings are generated in a background task (Spring `@Async` / `ApplicationEvent`) and inserted into `entity_embeddings` within seconds. Entity versions without a corresponding `entity_embeddings` row are excluded from Query Mode retrieval until generation completes (D-063).

```
entity_embeddings
  id UUID PK
  entity_type TEXT            ← 'actor' | 'space' | 'event' | 'relation'
  entity_id UUID
  entity_version_id UUID FK
  session_id UUID FK
  embedding vector(1536)      ← dimension matches chosen embedding model
  content_hash TEXT           ← detect unchanged content, skip re-embedding
  created_at TIMESTAMP
```

Similarity search via pgvector retrieves semantically relevant context before any LLM call — this is the primary cost control mechanism.

**Dual use:** `entity_embeddings` serves two retrieval purposes: (1) entity resolution during ingestion (Stage 1 similarity search, §6.3), and (2) world state context retrieval for Query Mode (§6.4). In both cases, the retrieval unit is an entity version snapshot. Session-level retrieval (e.g., finding which sessions are relevant to a question about a location) is done by joining through `entity_versions → sessions`.

A separate `session_chunk_embeddings` table is not required in v1. Session summaries are stored in `narrative_blocks` for reference but are not chunked and embedded independently — entity version snapshots are the retrieval unit for both pipelines. This may be revisited if query precision degrades on complex multi-session questions.

### 5.6 Annotations

Annotations work as a comment section — non-canonical, clearly marked as player commentary, not world state. Any campaign member can post; all members can read (D-011).

```
annotations
  id UUID PK
  campaign_id UUID FK
  entity_type TEXT          ← 'actor' | 'space' | 'relation' | 'event'
  entity_id UUID            ← references the annotated entity (polymorphic)
  author_id UUID FK → users.id
  content TEXT
  created_at TIMESTAMP      ← no updated_at; annotations are immutable after creation
```

### 5.7 Schema Conventions

- All primary keys are UUIDs — no auto-increment integers
- All tables carry `created_at` and, where mutable, `updated_at` timestamps
- All domain entities carry `owner_id` and `campaign_id` from day one (D-021)
- Foreign keys are always explicitly declared
- No nullable columns without deliberate justification
- All migrations are in `apps/api/src/main/resources/db/changelog/` using Liquibase changelog conventions

---

### 5.8 Proposals

Data model defined in v1; UI and approval logic ship in v2 (D-016). The schema is present from day one to avoid a future breaking migration.

```
proposals
  id UUID PK
  campaign_id UUID FK → campaigns.id
  target_entity_type TEXT   ← 'actor' | 'space' | 'event' | 'relation'
  target_entity_id UUID     ← references the entity being proposed against (polymorphic)
  author_id UUID FK → users.id
  proposed_delta JSONB      ← the change being proposed (field diffs)
  status TEXT               ← 'open' | 'cosigned' | 'approved' | 'rejected' | 'expired'
  created_at TIMESTAMP
  expires_at TIMESTAMP      ← TTL enforced in v2 (D-019); nullable in v1

proposal_votes
  id UUID PK
  proposal_id UUID FK → proposals.id
  voter_id UUID FK → users.id
  vote TEXT                 ← 'cosign' (player) | 'approve' | 'reject' (GM)
  created_at TIMESTAMP
  UNIQUE (proposal_id, voter_id)
```

---

## 6. LLM Integration Architecture

### 6.1 Providers

Blue Steel uses two LLM providers with distinct responsibilities. Both are behind ports. Both are swappable independently without domain changes.

| Concern | Provider | Model |
|---|---|---|
| Text generation (extraction, conflict detection, query answering) | Anthropic (Claude) | via Spring AI `ChatClient` |
| Embeddings (entity vectorisation, similarity retrieval) | OpenAI | `text-embedding-3-small`, 1536 dimensions |

**Why two providers:** Anthropic does not provide an embedding API — Claude models are generative only. The embedding provider is necessarily separate from the generation provider. This is not a compromise; it is how the ecosystem works. The `EmbeddingPort` abstraction means the application layer is unaware of which provider handles embeddings.

Spring AI is used for two concerns only: `ChatClient` (text generation) and `EmbeddingModel` (vector generation). Spring AI's `VectorStore` abstraction is **not used** — it provides a generic flat similarity search interface that cannot express the domain-specific retrieval queries this system requires (filtering by `entity_type`, joining through `entity_versions → sessions`, scoping by `campaign_id`). All pgvector similarity searches are written as native PostgreSQL queries via Spring Data JPA or `JdbcTemplate`, giving full control over query shape and index usage. See D-062.

The domain never references Anthropic, OpenAI, or any provider directly.

### 6.2 Port Definition

**Shared read model: `EntityContext`**

`EntityContext` is a Java Record defined in `com.bluesteel.application.model`. It is the unit of world state knowledge passed to AI-driven ports. It is assembled by use cases from repository data before any AI port call — it is never built inside an adapter. It carries no JPA annotations and no Spring types; it is a plain immutable value.

```java
// com.bluesteel.application.model
record EntityContext(
    UUID entityId,
    String entityType,      // "actor" | "space" | "event" | "relation"
    String name,
    String stateSnapshot,   // prose representation of the entity's current state (used as LLM context)
    UUID sessionId,         // session that produced this version — used for citation grounding (D-003)
    int versionNumber
) {}
```

`stateSnapshot` is a prose string derived from `full_snapshot` JSONB at the persistence layer and projected into this record by the use case before passing it to the port. The AI adapter never reads the DB directly to build context.

---

**Port interfaces** — defined in `com.bluesteel.application.port.out`, implemented in `adapters.out.ai`:

```java
interface NarrativeExtractionPort {
    ExtractionResult extract(NarrativeBlock narrativeBlock);
}

interface EntityResolutionPort {
    List<ResolvedEntity> resolve(List<ExtractedMention> mentions, List<EntityContext> candidateContext);
    // ResolvedEntity carries outcome: MATCH | NEW | UNCERTAIN
    // UNCERTAIN entities surface in diff for explicit user resolution
}

interface ConflictDetectionPort {
    List<ConflictWarning> detect(ExtractionResult extraction, List<EntityContext> relevantContext);
}

interface EmbeddingPort {
    float[] embed(String content);
}

interface QueryAnsweringPort {
    QueryResponse answer(String question, List<EntityContext> relevantContext);
}

interface EmailPort {
    void send(EmailMessage message);
    // EmailMessage carries: to, subject, body (plain text + HTML)
    // Adapter: transactional email provider (Resend / Brevo) via HTTP API
    // D-060: email delivery is the one infrastructure concern deliberately outsourced
}
```

Spring AI adapters implement these ports in `adapters.out.ai`. Swapping providers means replacing adapter implementations — zero domain changes.

### 6.3 Extraction Pipeline

Each session ingestion runs the following pipeline:

```
Session Summary (raw text)
       │
       ▼
 Token budget check ──── REJECT if oversized
       │
       ▼
 Knowledge Extraction ── LLM call 1  (Anthropic)
 (actors, spaces, events, relations identified as raw mentions;
  narrative summary header generated as co-output — D-005)
       │
       ▼
 Entity Resolution ────── two-stage
   Stage 1: pgvector similarity search per extracted mention
            → score below floor → classify NEW immediately (no LLM call)
            → score above floor → candidate(s) forwarded to Stage 2
   Stage 2: LLM call 2  (Anthropic, bounded — only high-score candidates)
            → MATCH:     attach mention to existing world state entity
            → NEW:       create new entity record
            → UNCERTAIN: surface as dedicated diff card for user resolution
       │
       ▼
 Conflict Detection ───── pgvector retrieval → LLM call 3  (Anthropic, bounded)
 (compare extracted facts against current world state for hard contradictions)
       │
       ▼
 Diff Generation
 (new entities: full profile — D-007; existing entities: delta only — D-006;
  uncertain entities: dedicated resolution card; conflicts: warning card)
       │
       ▼
 User Review (structured diff)
       │
       ▼
 Commit ── world state written; session status → 'committed'
       │    HTTP 200 returned to client immediately
       │
       ▼ (async, fire-and-forget)
 Embedding generation ── OpenAI EmbeddingModel called per committed
                          entity version; rows inserted into entity_embeddings
                          Entities without embeddings are excluded from
                          Query Mode context retrieval until generation completes
                          (typically completes within seconds of commit)
```

**Three bounded LLM calls maximum per session ingestion.** LLM call 2 (entity resolution) is bounded by the pgvector similarity floor — mentions that score below the floor are classified without an LLM call. The pipeline never passes unbounded world state to the LLM.

**Oversized input:** If the token budget check rejects the summary, the API returns `400` with error code `SUMMARY_TOO_LARGE` and a `max_tokens` field indicating the configured limit. No partial processing occurs. The client surfaces a user-facing message with the token count and the limit, and suggests splitting the summary into multiple sessions.

**Entity resolution outcomes in the diff:**
- `MATCH` — entity card shows delta only against the matched existing entity (D-006)
- `NEW` — entity card shows full extracted profile (D-007)
- `UNCERTAIN` — dedicated card presents the extracted mention alongside the candidate match; user must choose: **same entity** or **different entity**

**UNCERTAIN resolution is mandatory before commit.** There is no defer option. The Commit button is disabled until all `UNCERTAIN` cards are resolved. A progress indicator shows the count of unresolved items (*"2 items require resolution before commit"*). The backend additionally validates the commit payload and rejects with `422` if any `UNCERTAIN` entities are present — defence in depth against a UI bypass.

If the user is genuinely uncertain, the correct choice is **different entity** (create new). An incorrect split can be corrected through the proposal system in v2 (D-016). An unresolved entity creates an orphaned record that degrades the world state more than a recoverable wrong decision does.

**Stuck-processing recovery (D-074):** The async pipeline runs inside the JVM. A server restart while the pipeline is in flight leaves the session permanently in `processing`, which blocks all future submissions for that campaign (D-054). Two recovery mechanisms are in place:

1. **Startup recovery (`ApplicationReadyEvent` listener, D-073):** On every startup, all sessions with `status = 'processing'` are immediately transitioned to `failed` with `failure_reason = 'PIPELINE_INTERRUPTED'`. This handles crash-recovery atomically at boot.
2. **Scheduled TTL check:** A `@Scheduled` task runs every 5 minutes and transitions any session in `processing` for longer than `blue-steel.ingestion.processing-timeout-minutes` (default: 10 minutes, evaluated against `sessions.updated_at`) to `failed` with `failure_reason = 'PIPELINE_TIMEOUT'`. This handles silent hangs.

Both transitions log at WARN per session and preserve the `narrative_blocks` record. The user must re-submit the summary — no automatic retry.

### 6.4 Query Pipeline

Each natural language query runs the following pipeline:

```
Query (user question, free text)
       │
       ▼
 Embed question ─────────── EmbeddingPort (OpenAI)
       │
       ▼
 pgvector similarity search ─ retrieve top-N relevant entity versions
                                and session events from entity_embeddings
       │
       ▼
 Context assembly ─────────── collect matched entity snapshots + session
                                references; enforce context token budget
       │
       ▼
 QueryAnsweringPort ─────────  LLM call (Anthropic)
                                system: world state context + citation rule
                                user: original question
       │
       ▼
 Response + citations ──────── structured response: answer text +
                                list of session_ids that support each claim
       │
       ▼
 Returned to client
```

**Citation grounding:** The LLM is instructed to attribute each factual claim to a specific `session_id` from the provided context. Claims it cannot attribute to provided context are suppressed — consistent with D-003. The response envelope carries a `citations` field mapping claim spans to session references.

**Cost note:** Query Mode makes exactly one LLM call per query. Context is bounded by the pgvector retrieval result set (top-N chunks, configurable) and a token envelope applied before the call.

### 6.5 Cost Governance

Cost is controlled at four levels:

| Level | Mechanism |
|---|---|
| **Provider level** | Hard monthly spend cap configured in Anthropic console — non-negotiable before first production call |
| **Pre-call estimation** | Token count estimated before every LLM call; call rejected if it exceeds configured envelope |
| **Context bounding** | pgvector similarity search scopes LLM context to relevant chunks only — prevents context growth with campaign size |
| **Usage logging** | Every LLM call logged: tokens in, tokens out, estimated cost, session, user, pipeline stage |

Usage logs are queryable. Cost per campaign, per session, and per pipeline stage is observable from day one.

---

## 7. API Design

### 7.1 Conventions

The REST API follows Bloch's principle of minimal, unsurprising surfaces. Every endpoint does one thing clearly.

- Base path: `/api/v1/`
- All responses are JSON
- All timestamps are ISO 8601 UTC
- All IDs are UUIDs
- Resource names are plural nouns: `/campaigns`, `/sessions`, `/actors`
- Nested resources reflect ownership: `/campaigns/{id}/sessions`, `/campaigns/{id}/actors`

### 7.2 HTTP Semantics

| Operation | Method | Example |
|---|---|---|
| List | GET | `GET /api/v1/campaigns` |
| Get one | GET | `GET /api/v1/campaigns/{id}` |
| Create | POST | `POST /api/v1/campaigns` |
| Full update | PUT | `PUT /api/v1/campaigns/{id}` |
| Partial update | PATCH | `PATCH /api/v1/campaigns/{id}` |
| Delete | DELETE | `DELETE /api/v1/campaigns/{id}` |

### 7.3 Response Envelope

All responses use a consistent envelope:

```json
{
  "data": { },
  "meta": { },
  "errors": [ ]
}
```

- `data` contains the resource or list of resources
- `meta` contains pagination, total counts, and request tracing identifiers
- `errors` is present only on failure responses; absent on success

### 7.4 Error Model

```json
{
  "errors": [
    {
      "code": "ENTITY_NOT_FOUND",
      "message": "Actor with id '...' not found in campaign '...'",
      "field": null
    }
  ]
}
```

Error codes are machine-readable constants. Messages are human-readable. Field is populated for validation errors.

| HTTP Status | Meaning |
|---|---|
| 200 | Success |
| 201 | Created |
| 400 | Validation error or bad request |
| 401 | Unauthenticated |
| 403 | Unauthorized (authenticated but insufficient role) |
| 404 | Resource not found |
| 409 | Conflict (e.g. duplicate entity) |
| 422 | Unprocessable — valid request, business rule violation |
| 500 | Internal error |

### 7.5 Versioning

API versioning is path-based (`/api/v1/`). Version increments are reserved for breaking changes. Non-breaking additions (new fields, new optional parameters) are made without version increment.

### 7.6 Session Ingestion API

The session ingestion flow uses a staged endpoint sequence:

**Session management:**

| Method + Path | Description | Required role |
|---|---|---|
| `GET /api/v1/campaigns/{id}/sessions` | List all sessions ordered by `sequence_number`. Returns id, status, sequence_number, committed_at. Paginated. | campaign member |
| `GET /api/v1/campaigns/{id}/sessions/{sid}` | Session detail including `sequence_number`, `status`, `committed_at`, and narrative block reference. | campaign member |
| `DELETE /api/v1/campaigns/{id}/sessions/{sid}` | Discard a draft session. Sets `status = 'discarded'` and clears `diff_payload`. Only valid when `status = 'draft'`. The session row and `narrative_blocks` record are preserved — no physical deletion occurs. | gm |

**Ingestion flow:**

| Step | Method + Path | Description |
|---|---|---|
| 1. Submit summary | `POST /api/v1/campaigns/{id}/sessions` | Accepts raw summary text; triggers async ingestion pipeline; returns `session_id` and initial status `processing`. Rejected with `409` if another session is in `processing` or `draft` state. |
| 2. Poll status | `GET /api/v1/campaigns/{id}/sessions/{sid}/status` | Returns current pipeline status: `processing` \| `draft` \| `committed` \| `failed` |
| 3. Retrieve draft diff | `GET /api/v1/campaigns/{id}/sessions/{sid}/diff` | Returns the full structured diff payload when status is `draft` |
| 4. Commit | `POST /api/v1/campaigns/{id}/sessions/{sid}/commit` | Submits the reviewed diff payload. Backend validates: zero UNCERTAIN entities, caller is GM or editor. Returns `422` with specific error codes on violation. |

**DiffPayload — canonical schema (D-076)**

This is the formal, authoritative definition of the `DiffPayload` JSON contract. Backend Java records in `com.bluesteel.application.model` and frontend TypeScript types in `apps/web/src/types/sessions.ts` must mirror this schema exactly. This value is returned by `GET .../diff` and persisted as-is in `sessions.diff_payload` (JSONB). It is **never submitted back to the server** — it is read-only from the client's perspective. The commit payload (below) is a separate, derived shape.

```json
{
  "narrative_summary_header": "string — 1–3 sentence AI-generated session summary (D-005); display-only",
  "actors":    [ "<DiffCard>" ],
  "spaces":    [ "<DiffCard>" ],
  "events":    [ "<DiffCard>" ],
  "relations": [ "<DiffCard>" ],
  "detected_conflicts": [ "<ConflictCard>" ]
}
```

**DiffCard** — four variants, discriminated by `card_type`:

```json
// EXISTING — entity already in world state; shows delta only (D-006)
{
  "card_id":          "uuid — stable identifier used in commit payload to reference this card",
  "card_type":        "EXISTING",
  "entity_id":        "uuid — world state entity this card refers to",
  "entity_type":      "actor | space | event | relation",
  "name":             "string — current entity name",
  "changed_fields":   { "fieldName": "newValue", "...": "..." }
}

// NEW — entity appearing for the first time; shows full profile (D-007)
{
  "card_id":          "uuid",
  "card_type":        "NEW",
  "entity_type":      "actor | space | event | relation",
  "name":             "string — extracted name",
  "full_profile":     { "fieldName": "value", "...": "..." }
}

// UNCERTAIN — AI cannot determine if this is a new entity or an existing one (D-041, D-042)
// User must resolve to MATCH or NEW before commit. No defer option.
{
  "card_id":              "uuid",
  "card_type":            "UNCERTAIN",
  "entity_type":          "actor | space | event | relation",
  "extracted_mention":    "string — the raw text mention from the summary",
  "candidate_entity_id":  "uuid — best candidate match in world state",
  "candidate_entity_name":"string — name of the candidate for display"
}
```

**ConflictCard** — non-blocking; user must acknowledge before commit (D-033):

```json
{
  "conflict_id":      "uuid — referenced in commit payload acknowledged_conflicts",
  "entity_id":        "uuid — entity where the contradiction was detected",
  "entity_type":      "actor | space | event | relation",
  "description":      "string — human-readable description of the contradiction",
  "extracted_fact":   "string — what the current session claims",
  "existing_fact":    "string — what the world state currently holds"
}
```

---

**CommitPayload — canonical schema (D-076)**

This is the formal, authoritative definition of the body submitted to `POST .../commit`. Backend Java records and frontend TypeScript types must mirror this schema exactly.

Supported actions per card: `accept` (no change to extracted data), `edit` (user-modified fields), `delete` (discard AI-extracted item — no world state change). The `add` action (manually introducing entities the AI missed) is deferred to v2 (D-053).

```json
{
  "card_decisions": [
    {
      "card_id":      "uuid — references a DiffCard.card_id from the diff payload",
      "action":       "accept | edit | delete",
      "edited_fields": { "fieldName": "newValue" }
    }
  ],
  "uncertain_resolutions": [
    {
      "card_id":           "uuid — references an UNCERTAIN DiffCard.card_id",
      "resolution":        "MATCH | NEW",
      "matched_entity_id": "uuid — required when resolution = MATCH; null when NEW"
    }
  ],
  "acknowledged_conflicts": [
    {
      "conflict_id": "uuid — references a ConflictCard.conflict_id"
    }
  ]
}
```

**Validation rules enforced server-side (defence in depth):**
- `uncertain_resolutions` must include an entry for every `UNCERTAIN` card in the diff. Any missing resolution → `422 UNCERTAIN_ENTITIES_PRESENT` (D-042).
- `acknowledged_conflicts` must include an entry for every `ConflictCard` in `detected_conflicts`. An empty array is valid only when the diff had no conflicts → `422 CONFLICTS_NOT_ACKNOWLEDGED` (D-033).
- `edited_fields` is required and non-empty when `action = edit`. It is omitted or null for `accept` and `delete`.
- `card_decisions` must not be empty (at least one card must be in the diff for commit to be valid).

**Draft state persistence:** The draft diff is stored server-side in `sessions.diff_payload` (status `draft`). This enables failure recovery — if the user closes the browser mid-review, the diff is retrievable on return. Client-side edits to diff cards are submitted as part of the final commit payload, not persisted incrementally.

**Draft session policy (D-054):** At most one session per campaign may be in `draft` or `processing` state simultaneously. A new session submission is rejected with `409` if another session is already in one of those states. The GM may explicitly discard a draft session via `DELETE /api/v1/campaigns/{id}/sessions/{sid}` (GM role required; only valid when status is `draft`). Discarding sets `status = 'discarded'` and clears `diff_payload`. The session row and `narrative_blocks` record are both preserved — no physical deletion occurs. `discarded` is a terminal status; a discarded session cannot be reactivated.

**Failed status:** If the async pipeline fails (LLM error, token budget exceeded at extraction time, or unrecoverable processing error), the session status transitions to `failed` and `sessions.failure_reason` is populated. The status polling endpoint (Step 2) returns:

```json
{
  "data": {
    "session_id": "...",
    "status": "failed",
    "failure_reason": "EXTRACTION_FAILED | BUDGET_EXCEEDED | INTERNAL_ERROR",
    "message": "Human-readable description for display"
  }
}
```

Failed sessions are not retried automatically. The user must submit a new session (or re-submit the same summary). The original `narrative_blocks` record is preserved for reference.

### 7.7 Authentication & User Management Endpoints

**Authentication:**

| Method + Path | Description |
|---|---|
| `POST /api/v1/auth/login` | Accepts email + password; returns JWT access token and refresh token |
| `POST /api/v1/auth/refresh` | Accepts refresh token; returns new access token |
| `POST /api/v1/auth/logout` | Invalidates refresh token server-side |

Token details: HS256, 15-minute access token, 30-day rotating refresh token. See D-059.

**User management (D-051 — invitation model):**

There is no self-registration endpoint. User accounts are created exclusively via invitation. An invitation sends an email containing a system-generated temporary password. The recipient logs in with that password and must change it on first login.

Two distinct invitation endpoints, one per caller context (D-051, D-064):

| Method + Path | Description | Required role |
|---|---|---|
| `POST /api/v1/invitations` | Platform-level invitation. Creates a user account only. Used by admin to provision users before campaign assignment. Returns `409` if the email address already has an account. | admin |
| `POST /api/v1/campaigns/{id}/invitations` | Campaign-scoped invitation. Creates a user account AND adds the user to the campaign as `player` in a single transaction. If the email already has an account, adds them to the campaign directly without creating a new account (no `409`). Returns `409` if the user is already a campaign member. | gm |
| `GET /api/v1/users/me` | Get the current authenticated user's profile | authenticated |
| `GET /api/v1/users` | Search users by email (used by GM to find existing users to add to campaign) | admin, gm |
| `PATCH /api/v1/users/me/password` | Change own password (required flow after first login with temporary password) | authenticated |

---

### 7.8 Campaign & Membership Endpoints

| Method + Path | Description | Required role |
|---|---|---|
| `POST /api/v1/campaigns` | Create campaign and atomically assign the initial GM (D-061) | admin |
| `GET /api/v1/campaigns` | List campaigns (admin: all; members: own only) | authenticated |
| `GET /api/v1/campaigns/{id}` | Get campaign detail | campaign member |
| `POST /api/v1/campaigns/{id}/members` | Add a user to the campaign with a role | gm |
| `PATCH /api/v1/campaigns/{id}/members/{uid}` | Change a member's role | gm |
| `DELETE /api/v1/campaigns/{id}/members/{uid}` | Remove a member | gm |

**Campaign creation request shape (outline):**

```json
{
  "name": "The Shattered Crown",
  "gm_user_id": "<uuid of existing user to assign as GM>"
}
```

`gm_user_id` is required. The request is rejected with `422` if the referenced user does not exist. On success, the campaign row is created and a `campaign_members` row is inserted in the same transaction with `role = 'gm'` for the specified user. A campaign can never exist in a GM-less state (D-061).

---

### 7.9 Exploration Mode Endpoints

All endpoints are read-only. All require campaign membership. Pagination follows the strategy defined in OQ-D.

| Method + Path | Description |
|---|---|
| `GET /api/v1/campaigns/{id}/actors` | List all actors (filterable by name, status; paginated) |
| `GET /api/v1/campaigns/{id}/actors/{aid}` | Get actor with full version history |
| `GET /api/v1/campaigns/{id}/spaces` | List all spaces (paginated) |
| `GET /api/v1/campaigns/{id}/spaces/{sid}` | Get space with full version history |
| `GET /api/v1/campaigns/{id}/events` | List events (filterable by actor, space, session; paginated) |
| `GET /api/v1/campaigns/{id}/events/{eid}` | Get event with full version history. Used for timeline click-through and citation resolution from Query Mode responses. |
| `GET /api/v1/campaigns/{id}/relations` | List relations (filterable by actor; paginated) |
| `GET /api/v1/campaigns/{id}/relations/{rid}` | Get relation with full version history. Used for Relations graph edge click-through. |
| `GET /api/v1/campaigns/{id}/timeline` | Ordered event feed across all sessions (filterable) |
| `POST /api/v1/campaigns/{id}/annotations` | Create annotation on an entity |
| `GET /api/v1/campaigns/{id}/annotations` | List annotations (filterable by entity_type + entity_id) |
| `DELETE /api/v1/campaigns/{id}/annotations/{aid}` | Delete own annotation (author) or any annotation (gm) |

---

### 7.10 Query Mode Endpoint

| Method + Path | Description |
|---|---|
| `POST /api/v1/campaigns/{id}/queries` | Submit a natural language question; returns answer text and session citations |

**Request shape (outline):**
```json
{ "question": "What does Seraphine know about the cursed artifact?" }
```

**Response shape (outline):**
```json
{
  "data": {
    "answer": "...",
    "citations": [
      { "session_id": "...", "session_number": 4, "claim": "..." }
    ]
  }
}
```

**Execution model:** Query requests are handled synchronously. The server holds the connection open and returns the complete response when the answer is assembled. Target P95 latency is <5s (NFR). If the LLM call exceeds a configurable server-side timeout, the server returns `504` with error code `QUERY_TIMEOUT`. Streaming (SSE) is not implemented in v1 and may be revisited in v2 if the synchronous model cannot reliably meet the latency target.

Queries are stateless — each call is independent. No conversation history is maintained between queries.

**Q&A persistence:** Queries are stateless in v1 — no query history is stored. A campaign Q&A log with a history panel inside Query Mode is deferred to v2 (D-058).

---

## 8. Authentication & Authorization

### 8.1 Authentication

Spring Security handles authentication. JWT tokens are issued on login and validated on each request (D-059).

| Property | Value |
|---|---|
| Algorithm | HS256 — symmetric, single secret stored in `.env` |
| Access token TTL | 15 minutes |
| Refresh token TTL | 30 days |
| Rotation | Rotating — each refresh call issues a new token and invalidates the previous |
| Reuse detection | If an already-used token from a family is presented, the entire family is revoked |

**Authentication is stateless** — Spring Security validates the JWT signature and expiry on each request with no DB call. The access token carries only `user_id` and `is_admin`.

**Authorization is not stateless** — campaign-level role (`gm`, `editor`, `player`) is resolved at the use-case boundary via a DB read against `campaign_members` on every request that requires it. This is a deliberate design choice: it ensures that role changes (e.g., a player removed from a campaign) take effect immediately, with no stale-token window beyond the 15-minute access token TTL for authentication itself.

Refresh token validation requires a DB lookup against the `refresh_tokens` table (§5.1).

### 8.2 Role Model

Two dimensions: platform-level and campaign-level.

**Platform level:**

| Role | Scope |
|---|---|
| `admin` | Singleton super-user. Creates campaigns, assigns GMs, full platform access |

**Campaign level:**

| Role | Permissions |
|---|---|
| `gm` | Full write access within campaign. Session upload, diff review, commit, player management |
| `editor` | Session upload and diff review. Promoted by GM per campaign |
| `player` | Read-only. Query and Exploration modes |

Role checks are enforced at the application layer (use case level), not only at the controller level. A use case that requires `gm` access fails with a domain exception if the caller is not a GM — regardless of how it was invoked.

---

## 9. Testing Strategy

### 9.1 Philosophy

TDD from day one (Kent Beck). No production code is written without a failing test first. Tests are the first users of every API.

### 9.2 Backend Test Pyramid

```
        ┌──────────────────────────────────────────────────┐
        │  Integration (top level)                         │
        │  Spring Boot Test + Testcontainers (real Postgres)│
        │  External services (LLM) mocked at port boundary │
        ├──────────────────────────────────────────────────┤
        │  Unit                                            │
        │  domain logic, use cases — no Spring context     │
        └──────────────────────────────────────────────────┘
```

There is no E2E test layer (D-056). The backend test pyramid's highest confidence tier is integration tests — real PostgreSQL via Testcontainers, full Spring Boot context, all LLM-backed ports mocked. This gives high confidence on the full request path without the operational overhead of maintaining a full-stack E2E environment.

**Unit tests** — the majority. Domain entities, use cases, and domain services are tested with plain JUnit 5 and Mockito. No Spring context loaded. Fast feedback loop.

**Integration tests** — Spring Boot Test with Testcontainers. A real PostgreSQL container runs for every integration test suite. JPA adapters, migration scripts, Spring AI adapter wiring, and controller behavior are tested here. LLM ports are always mocked — no real API calls in CI.

**LLM mock binding in integration tests:** Mock LLM adapters are registered under the `local` Spring profile (D-049). All integration test classes extend a shared `BaseIntegrationTest` abstract class annotated with `@SpringBootTest` and `@ActiveProfiles("local")`. This ensures mock adapters are active in the full Spring context without any per-test configuration. The `BaseIntegrationTest` class also holds the shared Testcontainers PostgreSQL instance (started once per test suite, not per test method). No `@MockBean` on LLM ports — the real mock adapter implementations are wired by the profile, keeping the test Spring context identical to local dev.

### 9.3 Mutation Testing

Mutation testing is performed with **PIT (PITest)** via the Maven plugin. PIT modifies bytecode — flips conditionals, removes return values, changes operators — and runs the test suite against each mutant. A surviving mutant is a gap in test coverage that line coverage would never surface.

Mutation testing is scoped by build phase to manage execution time:

| Scope | When | Rationale |
|---|---|---|
| Domain core only | Every build | Fast, highest value — business logic lives here |
| Application layer | Pre-merge | Use case orchestration, port contracts |
| Full codebase | Nightly / on-demand | Adapter layer (out) is lower-value for mutation testing |

A minimum mutation score threshold is configured from day one and fails the build if not met. The threshold is set at a level achievable on day one and raised deliberately as the suite matures — never aspirationally.

### 9.4 Architecture Tests

Architectural boundaries are enforced as executable tests using **ArchUnit**. Rules are written once in a dedicated `ArchitectureTest` class and run on every build alongside unit tests — ArchUnit is bytecode analysis, no Spring context required.

The following rules are enforced:

| Rule | What it prevents |
|---|---|
| Domain has no Spring imports | `org.springframework.*` never appears in `com.bluesteel.domain` |
| Domain has no JPA imports | `jakarta.persistence.*` never appears in `com.bluesteel.domain` |
| Driving adapters only call application ports | `adapters.in` never reaches into `adapters.out` or `domain` directly |
| Driven adapters never imported by domain or application | Dependency direction is always inward |
| Ports are interfaces | Everything in `application.port.in` and `application.port.out` is an interface |
| No Spring annotations on domain classes | `@Service`, `@Component`, `@Repository` never appear in `com.bluesteel.domain` |

These tests are the executable specification of the hexagonal architecture. A layer boundary violation that slips through code review does not slip through the build.

### 9.5 Test Conventions

- Test classes mirror the class under test: `ActorService` → `ActorServiceTest`
- Integration test classes are suffixed `IT`: `SessionRepositoryIT`
- Architecture tests live in a single `ArchitectureTest` class
- No shared mutable state between tests
- Testcontainers instances are shared at the suite level for performance — not started per test
- LLM ports are always mocked in tests — cost governance applies to tests too

### 9.6 Frontend Testing

| Layer | Tool | Scope |
|---|---|---|
| Unit | Vitest | Hooks, Zustand store logic, utility functions, typed API client |
| Component | Vitest + React Testing Library | Individual components and multi-component feature flows |
| Accessibility | axe-core (`vitest-axe`) | All critical UI components — diff review cards, entity profiles, relation graph |

No E2E layer on the frontend (consistent with D-056). The highest frontend confidence tier is component-level tests covering the feature flows (diff review, query submission, exploration navigation).

**Accessibility rationale:** The target audience includes non-technical users. The diff review and exploration views are complex, interactive surfaces. Axe-core runs inline with Vitest — zero additional CI tooling overhead.

Frontend tests run in the `frontend.yml` GitHub Actions workflow as part of the pre-build step (type check → lint → Vitest → vite build).

---

## 10. Open Architecture Questions

| # | Question | Priority | Status |
|---|---|---|---|
| OQ-A | Entity resolution strategy | High | ✅ Resolved — see §6.3 |
| OQ-B | JWT token details — algorithm, expiry, refresh token strategy | Medium | ✅ Resolved — see DECISIONS.md D-059 |
| OQ-C | Embedding model selection — dimension, provider, cost profile | Medium | ✅ Resolved — see §6.1 |
| OQ-D | Pagination strategy for Exploration views — cursor-based vs offset | Low | ✅ Resolved — see DECISIONS.md D-055 |
| OQ-E | Uncertain entity card: behaviour of defer action | Medium | ✅ Resolved — see §6.3 |

All open architecture questions are resolved. OQ-B is documented in D-059.

**Pre-Phase 1 gate (D-057):** Before writing any production code, verify Spring Boot 4.0.3 compatibility for: Spring AI (`ChatClient`, `EmbeddingModel`), Testcontainers Spring Boot integration, Liquibase Spring Boot starter, and Spring Security 7. Log the verification result in DECISIONS.md. Any incompatible dependency must be resolved before Phase 1 begins.

---

## 11. Deployment & Infrastructure

All decisions in this section are captured in DECISIONS.md as D-044 through D-050.

---

### 11.1 Environment Model

Two environments only: **local** and **prod** (D-044).

| Environment | Purpose | Infrastructure |
|---|---|---|
| `local` | Development and manual testing | Docker Compose on developer machine |
| `prod` | Live deployment | Oracle Cloud + Vercel + Neon |

No staging environment. The project scope and team size do not justify consuming free-tier quota on a third environment. Local development is the pre-production validation layer.

---

### 11.2 Hosting

| Component | Platform | Tier | Notes |
|---|---|---|---|
| **Frontend** | Vercel | Free (Hobby) | Auto-deploy on push to `main`; branch preview URLs per PR |
| **Backend** | Oracle Cloud Always Free | Free (always) | ARM VM — 4 OCPUs / 24GB RAM, configurable allocation; Docker + Compose managed by VM owner |
| **Database** | Neon | Free (always) | Serverless PostgreSQL + pgvector; never pauses; DB branching available for migration testing |

**Oracle Cloud ARM note:** The Oracle VM uses an ARM (`linux/arm64`) architecture. All Docker images for the backend must be built for `linux/arm64`. GitHub Actions runners are `x86_64` — multi-arch builds use `docker buildx` with cross-compilation. Spring Boot on ARM is fully supported and performs well (D-046).

**Docker image registry:** Images are pushed to **GitHub Container Registry (ghcr.io)**, which is free for public repositories and included in GitHub free plan allowances for private repositories. The deploy step pulls from `ghcr.io` to the Oracle VM.

---

### 11.3 Local Development Setup

Local development follows a clear separation: infrastructure in Docker, application code running natively.

```
docker-compose.yml  (repo root)
  └── postgres:latest with pgvector extension
        port 5432 exposed to localhost
```

The Spring Boot backend and Vite dev server run natively — not in Docker. This preserves hot reload, debugger attachment, and fast test feedback loops.

**Spring profiles for local dev:**

| Profile | Behaviour |
|---|---|
| `local` (default) | All LLM ports use in-memory mock implementations. Zero API cost. Canned responses for extraction, resolution, and query pipelines. |
| `llm-real` | LLM ports use real Anthropic and OpenAI API calls. Activate when testing the extraction or query pipelines end-to-end. |

Activation: `--spring.profiles.active=local,llm-real`

Mock implementations live in `adapters.out.ai` alongside their real counterparts. They implement the same ports — no domain changes required to switch (D-049).

---

### 11.4 CI/CD Pipeline

GitHub Actions. Two independent workflow files with `paths` filters — each workflow triggers only when its project changes (D-048).

#### Backend pipeline (`.github/workflows/backend.yml`)

Triggered on push/PR when `apps/api/**` changes.

```
1. Compile (Maven)
2. Unit tests + ArchUnit architecture tests  ← fast, no containers
3. Integration tests (Testcontainers — real Postgres)
4. PIT mutation tests — domain core only
5. Build JAR
6. Build Docker image (linux/arm64 via docker buildx)
7. Push image to ghcr.io
8. Deploy: SSH into Oracle VM → docker pull → docker compose up -d
```

Steps 6–8 run only on push to `main`, not on PRs.

#### Frontend pipeline (`.github/workflows/frontend.yml`)

Triggered on push/PR when `apps/web/**` changes.

```
1. Type check (tsc --noEmit)
2. Lint (ESLint)
3. Unit tests (Vitest)
4. Build (vite build)
```

Deployment to Vercel is handled natively by Vercel's GitHub integration — a push to `main` triggers production deployment automatically. PR pushes generate preview URLs automatically. No deploy step is needed in the workflow.

---

### 11.5 Secret Management

Production secrets live in a `.env` file on the Oracle VM — never committed to the repository (D-050).

Secrets required at runtime:

| Secret | Consumer |
|---|---|
| `DATABASE_URL` | Spring Boot (Neon connection string) |
| `ANTHROPIC_API_KEY` | Spring AI adapter |
| `OPENAI_API_KEY` | Spring AI embedding adapter |
| `JWT_SECRET` | Spring Security |

The `.env` file is read by Docker Compose at container start via the `env_file` directive. Rotating a secret requires SSH access to the VM and a container restart.

Vercel secrets (frontend environment variables) are managed through the Vercel dashboard and are not stored in the repository.

---

### 11.6 Deployment Flow (end to end)

```
Developer pushes to main
        │
        ├── apps/api/** changed?
        │       │
        │       ▼
        │   GitHub Actions backend pipeline
        │   compile → test → mutation test → build JAR
        │   → build arm64 Docker image
        │   → push to ghcr.io
        │   → SSH into Oracle VM
        │   → docker pull + docker compose up -d
        │   → Liquibase migrations run on startup (auto)
        │
        └── apps/web/** changed?
                │
                ▼
            Vercel detects push
            → vite build
            → deploy to Vercel CDN
            → production URL live
```

Liquibase migrations run automatically at Spring Boot startup. The migration changelog lives in `apps/api/src/main/resources/db/changelog/`. This means a backend deploy that includes schema changes applies them on startup — no separate migration step required.

---

### 11.7 Open Infrastructure Questions

All four original open questions from the §11 placeholder have been resolved. No open questions remain in this section.
