# ROADMAP — Blue Steel

---

## Phases

### Phase 0 — Pre-Development Validation (Gate)

**Purpose:** Eliminate the highest-risk unknown before writing production code.

**Required before Phase 1 can begin:**

- [x] Verify Spring Boot 4.0.3 compatibility for: Spring AI (`ChatClient`, `EmbeddingModel`), Testcontainers Spring Boot integration, Liquibase Spring Boot starter, Spring Security 7 (D-057) — note: Spring AI `VectorStore` is not used; see D-062
- [x] Log compatibility verification result in DECISIONS.md
- [x] Resolve OQ-B (JWT algorithm, token expiry, refresh token strategy) — D-059
- [x] Resolve OQ-6 (Q&A log — deferred to v2) — D-058
- [ ] Create CLAUDE.md operational section (build commands, paths, test commands)

**Gate:** All items above must be checked before any functional block in Phase 1 is started.

---

### Phase 1 — Core Infrastructure

> **Principle:** Walking skeleton first, test harness second, domain code third.
> Every feature written after block 2 is written TDD against a fully wired test and deployment pipeline.

| # | Block | Status |
|---|---|---|
| 1.1 | **Full walking skeleton** — every layer of the production stack is alive and connected before any real feature is written. Specifically: **(1) Backend** — Spring Boot app deployed to Oracle VM via GitHub Actions (compile → test → build JAR → arm64 Docker image → push to ghcr.io → SSH deploy); exposes `GET /api/v1/health` returning `200 OK`. **(2) Database** — Neon PostgreSQL instance provisioned, pgvector extension enabled, Liquibase runs on startup and applies the baseline changelog, health endpoint confirms DB connectivity. **(3) Frontend** — minimal React/Vite/TypeScript app deployed to Vercel via GitHub integration; displays a single status page that calls `GET /api/v1/health` and renders the response. **(4) CI/CD** — both `backend.yml` and `frontend.yml` GitHub Actions workflows path-filtered and green; Vercel PR preview URLs working. The definition of done: a browser hitting the Vercel URL makes a round trip through the frontend → Spring Boot → Neon and returns a visible result. Nothing is mocked. | 🔲 |
| 1.2 | **Test infrastructure baseline** — JUnit 5 wired, ArchUnit `ArchitectureTest` class with all hexagonal boundary rules committed (D-037), Testcontainers PostgreSQL integration test running, PITest configured with initial domain-core threshold. Rules exist and are enforced from the first real class. | 🔲 |
| 1.3 | **Database schema baseline** — Liquibase migrations for core tables (`users`, `campaigns`, `campaign_members`, `refresh_tokens`), verified via Testcontainers integration test. | 🔲 |
| 1.4 | **Auth** — invitation flow, login, JWT issue/refresh/logout (D-051, D-059, D-060). | 🔲 |
| 1.5 | **Campaign + membership management** — campaign creation with atomic GM assignment (D-061), member add/change/remove. | 🔲 |
| 1.6 | **Role enforcement** — campaign membership DB check at the use-case boundary on every authorized request (D-043). | 🔲 |

---

### Phase 2 — Session Ingestion Pipeline

| # | Block | Status |
|---|---|---|
| 2.1 | **Session submission + status machine** — schema and API skeleton; status transitions `pending → processing → draft → committed → failed → discarded` (D-054). | 🔲 |
| 2.2 | **Mock LLM ports** — all five ports (`NarrativeExtractionPort`, `EntityResolutionPort`, `ConflictDetectionPort`, `QueryAnsweringPort`, `EmbeddingPort`) return canned responses under the `local` Spring profile (D-049). Unblocks TDD on the full extraction pipeline at zero API cost. | 🔲 |
| 2.3 | **Knowledge extraction** — LLM call 1; actors, spaces, events, relations identified as raw mentions; narrative summary header generated as co-output (D-005). | 🔲 |
| 2.4 | **Entity resolution** — two-stage pgvector similarity search + LLM; MATCH / NEW / UNCERTAIN outcomes (D-041, D-042). | 🔲 |
| 2.5 | **Conflict detection** — pgvector retrieval scoped context → LLM call 3; hard contradiction warning cards (D-033). | 🔲 |
| 2.6 | **Diff generation + structured diff API** — delta-only for existing entities (D-006), full profile for new (D-007), UNCERTAIN resolution cards, conflict warning cards (D-004). | 🔲 |
| 2.7 | **World state entity tables + versioning schema** — `actors`, `actor_versions`, `spaces`, `space_versions`, `events`, `event_versions`, `relations`, `relation_versions`; Liquibase migrations (D-035). | 🔲 |
| 2.8 | **Commit** — synchronous world state write; async embedding generation triggered post-commit via Spring `@Async` / `ApplicationEvent` (D-040, D-063). | 🔲 |
| 2.9 | **Diff review + commit UI** — structured diff screen, editable cards, UNCERTAIN resolution flow, conflict acknowledgement, Commit button with progress indicator (frontend). | 🔲 |

---

### Phase 3 — Query Mode

| # | Block | Status |
|---|---|---|
| 3.1 | **Query endpoint skeleton** — synchronous pipeline, 504 on timeout (D-052). | 🔲 |
| 3.2 | **pgvector similarity retrieval** — embed question → retrieve top-N relevant entity versions from `entity_embeddings`. | 🔲 |
| 3.3 | **`QueryAnsweringPort` + LLM call + citation grounding** — context assembly, LLM call, structured response with `citations` (D-003). | 🔲 |
| 3.4 | **Query Mode UI** — question input, answer display, session citation links (frontend). | 🔲 |

---

### Phase 4 — Exploration Mode

| # | Block | Status |
|---|---|---|
| 4.1 | **Actors, Spaces, Events endpoints + views** — list (offset pagination) and detail with full version history (D-055). | 🔲 |
| 4.2 | **Timeline endpoint + view** — ordered event feed, filterable by actor/space/event type, keyset pagination (D-055, D-009). | 🔲 |
| 4.3 | **Relations graph** — React Flow graph view, actors and spaces as nodes, relations as edges (D-030, D-009). | 🔲 |
| 4.4 | **Annotations** — create, list, delete; non-canonical, visible to all campaign members (D-011). | 🔲 |
| 4.5 | **"Propose a change" affordance** — visible on every entity, space, and relation; pipeline inactive until v2 (D-012). | 🔲 |

---

### v2 — Proposal & Approval Pipeline + Enhancements

> To be designed after v1 ships. Key items:

- Player proposal submission UI
- Co-sign flow (D-017)
- GM approval / veto (D-018)
- Proposal expiry TTL enforcement (D-019)
- Commit payload "add" action — manually introduce missed entities (D-053)
- Q&A log — campaign history of queries and answers, history panel in Query Mode (D-058)
- Query streaming / SSE if synchronous model cannot meet latency target (D-052)

---

## Status Legend

| Symbol | Meaning |
|---|---|
| 🔲 | Not started |
| 🔄 | In progress |
| ✅ | Done |
| ⛔ | Blocked |
