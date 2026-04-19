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
- [x] Create CLAUDE.md operational section (build commands, paths, test commands)

**Gate:** All items above must be checked before any functional block in Phase 1 is started.

---

### Phase 1 — Core Infrastructure

> **Principle:** Walking skeleton first, test harness second, domain code third.
> Every feature written after F1.1 is written TDD against a fully wired test and deployment pipeline.
>
> **Ops prerequisites (human tasks, not agent tasks):** Oracle Cloud ARM VM provisioned; Neon PostgreSQL instance created with pgvector extension enabled; Vercel project connected to the repository; GitHub repository secrets populated (`GHCR_TOKEN`, `ORACLE_SSH_KEY`, `DATABASE_URL`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`).

#### Summary

| # | Feature | Status |
|---|---|---|
| F1.0 | Repo scaffold + CI/CD pipelines | ✅ |
| F1.1 | Test infrastructure baseline | ✅ |
| F1.2 | Backend health skeleton (deployable) | ✅ |
| F1.3 | Database connectivity + Liquibase baseline | ✅ |
| F1.4 | Core schema migration: auth tables | 🔲 |
| F1.5 | Admin bootstrap + platform invitation + password change | 🔲 |
| F1.6 | Login + JWT issuance + refresh token rotation + logout | 🔲 |
| F1.7 | Frontend: walking skeleton + auth scaffold | 🔲 |
| F1.8 | Campaign creation + membership API | 🔲 |
| F1.9 | Campaign-scoped invitation + role enforcement | 🔲 |

---

#### F1.0 — Repo scaffold + CI/CD pipelines

**Goal:** Establish the full monorepo structure, build tooling, and CI/CD pipeline before any application code exists. A green pipeline with no logic — just scaffold, config, and workflows.

**Scope (in):**
- Monorepo layout per ARCHITECTURE.md §2: `apps/api/`, `apps/web/`, `docs/`, `skills/`
- `apps/api/pom.xml`: all dependency versions, plugin config (Surefire, Spotless + google-java-format, PITest, Liquibase Maven plugin), Spring Boot 4.0.3 parent — no application code
- `apps/web/package.json`, `vite.config.ts`, `tsconfig.json`, ESLint + Prettier config — no application code
- `Dockerfile` for `linux/arm64` (Oracle Cloud ARM, D-046)
- `docker-compose.yml` for local PostgreSQL + pgvector (`pgvector/pgvector` image)
- `.env.example` with all env vars from root CLAUDE.md §4 plus `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `blue-steel.ingestion.processing-timeout-minutes`
- `.gitignore` covering `.env`, `.env.local`, build artifacts, IDE files
- `backend.yml` GitHub Actions: path-filtered `apps/api/**`; steps: Spotless check → compile → `mvn test` → build JAR → Docker buildx `linux/arm64` → push to `ghcr.io`; SSH deploy step stubbed (no secrets wired yet)
- `frontend.yml` GitHub Actions: path-filtered `apps/web/**`; steps: `npm ci` → type-check → lint → `npx vitest run` → `npm run build`

**Scope (out):** Any Spring Boot or React application code. Actual deployment to Oracle Cloud or Vercel. Secret provisioning.

**Skills:** `ci-cd`  
**Decisions:** D-022, D-028, D-048, D-065, D-066, D-071  
**Dependencies:** None

---

#### F1.1 — Test infrastructure baseline

**Goal:** Wire every test tool and enforce architecture boundaries from the first commit. ArchUnit rules must exist before the first domain class, not after.

**Scope (in):**
- `ArchitectureTest.java` in `test/architecture/` with four rules: (1) `com.bluesteel.domain` zero `org.springframework.*` imports, (2) zero `jakarta.persistence.*` imports, (3) adapters never imported by domain/application, (4) config classes live in their adapter's package
- `TestcontainersPostgresBaseIT.java` base class: starts PostgreSQL + pgvector container, applies Liquibase on boot, provides `DataSource` via `@DynamicPropertySource`
- Smoke integration test extending the base class: asserts `SELECT 1` and `SELECT * FROM pg_extension WHERE extname = 'vector'`
- PITest configured in `pom.xml`: scope `com.bluesteel.domain.*`, minimum threshold `80%`, excluded from standard `mvn test`
- `logback-spring.xml`: human-readable pattern on `local` profile; `LogstashEncoder` JSON on all other profiles (D-072)

**Scope (out):** Any domain code under test. Rules pass vacuously on empty packages — that is correct.

**Skills:** `backend-testing`  
**Decisions:** D-036, D-037, D-056, D-072  
**Dependencies:** F1.0

---

#### F1.2 — Backend health skeleton (deployable)

**Goal:** The minimal deployable Spring Boot application — one endpoint, all cross-cutting config wired, deployed to Oracle Cloud ARM VM.

**Scope (in):**
- `BlueSteelApplication.java` main class
- `ApplicationConfig.java`: `@EnableAsync`, `@Async` executor bean (core=2, max=10), shared `Clock` bean
- `WebConfig.java`: CORS (allow `VITE_API_BASE_URL` origin + `http://localhost:5173`), Jackson config (ISO 8601 dates, UUID as String)
- `SecurityConfig.java`: permit `/api/v1/health` without auth; all other routes require JWT (filter stub — real filter wired in F1.6); stateless session
- `GlobalExceptionHandler.java` (`@ControllerAdvice`): maps to error envelope (ERR-01); handles `MethodArgumentNotValidException` → 400, `AccessDeniedException` → 403, uncaught `RuntimeException` → 500 (no stack trace in response)
- `GET /api/v1/health` → `200 { "data": { "status": "UP" }, "meta": {}, "errors": [] }` — DB check not yet included (F1.3)
- Backend CI deploy step wired: SSH to Oracle Cloud VM, pull image from `ghcr.io`, restart container

**Scope (out):** DB connectivity (F1.3). JWT filter (F1.6). Any domain logic.

**Skills:** `backend-endpoint`  
**Decisions:** D-027, D-038, D-039, D-046  
**Dependencies:** F1.1

---

#### F1.3 — Database connectivity + Liquibase baseline

**Goal:** Connect the backend to Neon PostgreSQL, run the Liquibase pipeline from zero, confirm pgvector availability, and add a DB liveness check to the health endpoint.

**Scope (in):**
- `PersistenceConfig.java`: `DataSource` from `DATABASE_URL` env var; JPA/Hibernate (snake_case naming, DDL auto = `validate`)
- `application.yml` updated: `spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml`
- `db/changelog/db.changelog-master.xml` (root changelog file)
- `0001_baseline.xml`: empty changeset (comment only) — confirms migration runner works
- `GET /api/v1/health` enhanced: `SELECT 1` DB check; returns `{ "status": "UP", "db": "UP" }` or `{ "status": "DEGRADED", "db": "DOWN" }`
- Testcontainers integration test: Liquibase runs on startup, `SELECT 1` passes, `pg_extension.extname = 'vector'` confirmed

**Scope (out):** Any table creation (F1.4). Neon provisioning is a human ops task.

**Skills:** `database-migration`  
**Decisions:** D-029, D-031, D-047  
**Dependencies:** F1.2

---

#### F1.4 — Core schema migration: auth tables

**Goal:** Create all auth-layer tables in one set of migrations. All downstream features from F1.5 onward depend on this schema.

**Scope (in):** Liquibase changesets:
- `0002_create_users.xml` — `users` per ARCHITECTURE.md §5.1; adds `force_password_change BOOLEAN NOT NULL DEFAULT FALSE` (D-077); partial unique index `WHERE is_admin = TRUE` (D-025)
- `0003_create_campaigns.xml` — `campaigns` per §5.1
- `0004_create_campaign_members.xml` — `campaign_members` with `UNIQUE (campaign_id, user_id)`, partial unique index `WHERE role = 'gm'`, `CHECK (role IN ('gm', 'editor', 'player'))` (D-061)
- `0005_create_refresh_tokens.xml` — `refresh_tokens` with `family_id`, `used_at`, `expires_at` (D-059)
- Testcontainers integration test: all changesets applied; all tables, indexes, and CHECK constraints verified via `information_schema`; singleton admin partial unique index tested (double-insert fails)

**Scope (out):** JPA entities, repositories, domain classes. No `invitations` table — accounts are created directly by invitation endpoints with a temporary password (D-077).

**Skills:** `database-migration`  
**Decisions:** D-025, D-059, D-061, D-077  
**Dependencies:** F1.3

---

#### F1.5 — Admin bootstrap + platform invitation + password change

**Goal:** The singleton admin is seeded on startup. The admin can invite new users to the platform via a temporary password flow. Invited users must change their password on first login.

**Scope (in):**

*Domain:* `User` domain entity (pure Java); `CampaignRole` enum (`GM`, `EDITOR`, `PLAYER`)

*Application use cases:*
- `AdminBootstrapUseCase`: `ApplicationReadyEvent` listener; checks `users WHERE is_admin = TRUE`; inserts admin from `ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars if absent; idempotent (D-073)
- `InvitePlatformUserUseCase`: admin-only; generates random 16+ char temporary password; BCrypt-hashes it; creates `users` row with `force_password_change = TRUE`; sends email via `EmailPort`; if email already exists: updates `password_hash` and sets `force_password_change = TRUE` (re-invitation recovery, D-070, D-077)
- `ChangePasswordUseCase`: validates current password; updates `password_hash`; sets `force_password_change = FALSE`

*Adapters:*
- `MockEmailAdapter`: logs full `EmailMessage` (to, subject, body) at INFO; activated on `local` profile (D-075)
- Real `EmailAdapter` stub: activated on `email-real` profile; throws `UnsupportedOperationException` until provider is wired
- `UserJpaEntity` + `UserRepository` + `UserPersistenceAdapter` in `adapters.out.persistence`

*API:*
- `POST /api/v1/invitations` — admin-only; `{ "email": "..." }` → 201 (account created) or 200 (re-invitation: existing account refreshed)
- `PATCH /api/v1/users/me/password` — authenticated; `{ "currentPassword": "...", "newPassword": "..." }` → 200; clears `force_password_change`
- `GET /api/v1/users/me` — authenticated; returns `{ id, email, isAdmin, forcePasswordChange }`

*Startup recovery (D-074):* The `ApplicationReadyEvent` listener also transitions all sessions with `status = 'processing'` to `status = 'failed'` with `failure_reason = 'PIPELINE_INTERRUPTED'` — included here because it shares the same listener, even though sessions are a Phase 2 concept. If sessions table doesn't exist yet (Phase 1 execution order), the query returns an empty result; no error.

**Scope (out):** Campaign-scoped invitation (F1.9). Login/JWT (F1.6). Scheduled stuck-session TTL check (F2.3).

**Skills:** `auth`, `backend-endpoint`  
**Decisions:** D-051, D-060, D-070, D-073, D-074, D-075, D-077  
**Dependencies:** F1.4

---

#### F1.6 — Login + JWT issuance + refresh token rotation + logout

**Goal:** Complete stateless authentication. Access tokens (HS256, 15-min TTL) and rotating refresh tokens (30-day TTL) with family-based reuse detection.

**Scope (in):**

*Domain:* `RefreshToken` entity; `RefreshTokenRepository` driven port

*Application:*
- `LoginUseCase`: validate BCrypt credentials; issue access JWT + refresh token pair; set `force_password_change` in response if `TRUE`
- `RefreshTokenUseCase`: validate token hash; detect reuse (consumed token from same `family_id` → revoke entire family → 401); rotate on success
- `LogoutUseCase`: revoke refresh token family by `family_id`

*Adapters:*
- `JwtTokenService` in `adapters.in.security`: HS256 sign/verify using `JWT_SECRET`; claims: `user_id` (UUID), `is_admin` (boolean); 15-min TTL; startup validation that `JWT_SECRET` is ≥ 32 bytes
- `JwtAuthenticationFilter` extends `OncePerRequestFilter`: extract + validate Bearer token; set `SecurityContextHolder`; on invalid/missing token → write 401 response directly (do not throw)
- `SecurityConfig` updated: `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`; stateless session; `/api/v1/auth/**` public; all other routes authenticated
- `RefreshTokenJpaEntity` + `RefreshTokenRepository` + adapter in `adapters.out.persistence`

*API:*
- `POST /api/v1/auth/login` — `{ "email", "password" }` → 200 with `{ "accessToken": "...", "forcePasswordChange": bool }`; sets `httpOnly SameSite=Strict Secure` refresh token cookie
- `POST /api/v1/auth/refresh` — reads cookie; 200 with new access token + new cookie; 401 `REFRESH_TOKEN_REUSE_DETECTED` on reuse
- `POST /api/v1/auth/logout` — authenticated; revokes family; clears cookie → 200

**Scope (out):** Campaign-level authorization (F1.8+).

**Skills:** `auth`  
**Decisions:** D-043, D-059, D-060  
**Dependencies:** F1.5

---

#### F1.7 — Frontend: walking skeleton + auth scaffold

**Goal:** Minimal React app deployed to Vercel that completes the round-trip (browser → Spring Boot → Neon). Full auth UI including login, forced password change, and route guards. The definition of done for the walking skeleton.

**Scope (in):**

*Scaffold:*
- `main.tsx`: React root, `QueryClientProvider`, `BrowserRouter`, React Router routes
- Zustand auth store (`store/authStore.ts`): `accessToken` (in-memory only — never `localStorage`), `currentUser` (`id`, `email`, `isAdmin`, `forcePasswordChange`)
- Zustand campaign store (`store/campaignStore.ts`): `activeCampaignId`, `activeRole` (empty for now)
- Base HTTP client (`api/client.ts`): attaches `Authorization: Bearer`, handles 401 → silent refresh retry → redirect to login on second 401
- TypeScript types: `ApiEnvelope<T>`, `AuthLoginResponse`, `UserMeResponse`

*Auth UI:*
- `features/auth/LoginPage.tsx`: React Hook Form + shadcn/ui `Form`; submit to `POST /auth/login`; on success store token, check `forcePasswordChange` → redirect to change-password or campaign list
- `features/auth/ChangePasswordPage.tsx`: React Hook Form; submit to `PATCH /users/me/password`; on success redirect to campaign list; route guard: redirect here if `forcePasswordChange = true`
- `components/domain/RequireAuth.tsx`: redirects to `/login` if no access token in auth store

*Health check:*
- `/status` page: calls `GET /api/v1/health`, renders `"db": "UP"` — completes the round-trip proof

*Vercel:*
- `vercel.json` with SPA rewrite rule; `VITE_API_BASE_URL` documented in README as a required Vercel env var

**Scope (out):** Campaign list page. Any feature beyond auth.

**Skills:** `auth`, `frontend-api-resource`, `frontend-testing`  
**Decisions:** D-030, D-043, D-045, D-067, D-068, D-077  
**Dependencies:** F1.6

---

#### F1.8 — Campaign creation + membership API

**Goal:** Admin creates campaigns with an atomic GM assignment. Users retrieve campaigns they belong to. Establishes `CampaignMembershipPort` — the canonical authorization check used by all subsequent features.

**Scope (in):**

*Domain:* `Campaign` aggregate; invariant: exactly one GM at creation

*Application:*
- `CreateCampaignUseCase`: admin-only; inserts `campaigns` + `campaign_members` (GM row) in one `@Transactional` call (D-061)
- `GetCampaignUseCase`: returns campaign if caller is a member; 403 otherwise
- `ListCampaignsUseCase`: returns campaigns the caller belongs to (admin sees all)
- `CampaignMembershipPort` driven port: `Optional<CampaignRole> resolveRole(UUID campaignId, UUID userId)` — the canonical authorization check for every campaign-scoped use case (D-043)
- `CampaignRepository`, `CampaignMembershipRepository` driven ports

*Adapters:* JPA entities + repositories for `campaigns`, `campaign_members`; `CampaignMembershipAdapter` implements `CampaignMembershipPort`

*API:*
- `POST /api/v1/campaigns` — admin only; `{ "name": "...", "gmUserId": "..." }` → 201
- `GET /api/v1/campaigns` — returns campaigns where caller is a member (admin: all)
- `GET /api/v1/campaigns/{id}` — returns campaign + caller's role; 404/403 if not a member

**Scope (out):** Member add/remove (F1.9). Campaign-scoped invitation (F1.9).

**Skills:** `backend-endpoint`, `backend-domain-model`  
**Decisions:** D-024, D-025, D-043, D-061  
**Dependencies:** F1.6

---

#### F1.9 — Campaign-scoped invitation + role enforcement

**Goal:** GM manages campaign membership: invites members, changes roles, removes members. Role enforcement via `CampaignMembershipPort` applied to all campaign-scoped use cases.

**Scope (in):**

*Application:*
- `InviteCampaignMemberUseCase`: GM-only; generates temp password; creates user account if email is new, or resets credentials if account exists; adds to `campaign_members` with specified role atomically; sends email via `EmailPort`; returns 409 if user is already a campaign member (D-064)
- `ChangeMemberRoleUseCase`: GM-only; changes role for any non-GM member; cannot change GM's own role
- `RemoveMemberUseCase`: GM-only; removes non-GM member; returns 422 `CANNOT_REMOVE_GM` if target is GM
- `GET /api/v1/users` — admin + GM; search by email; used by GM to find existing platform users

*API:*
- `POST /api/v1/campaigns/{id}/invitations` — GM-only; `{ "email": "...", "role": "editor"|"player" }` → 201 (new user created) or 200 (existing user added to campaign); 409 if already a member (D-064)
- `PATCH /api/v1/campaigns/{id}/members/{uid}` — GM-only; `{ "role": "editor"|"player" }` → 200
- `DELETE /api/v1/campaigns/{id}/members/{uid}` — GM-only; 422 if target is GM → 200

*Role enforcement:* `CampaignMembershipPort.resolveRole` called at the use-case boundary in all campaign-scoped services from F1.8 onward. Pattern: `resolveRole(campaignId, callerId).orElseThrow(UnauthorizedException::new)` then assert the resolved role meets the minimum required.

**Scope (out):** Frontend campaign management UI (deferred — out of Phase 1 scope).

**Skills:** `auth`, `backend-endpoint`  
**Decisions:** D-015, D-043, D-061, D-064, D-075, D-077  
**Dependencies:** F1.8

---

### Phase 2 — Session Ingestion Pipeline

> **Principle:** Schema first, mocks second, backend pipeline third, frontend last.
> Every pipeline stage is developed TDD under the `local` profile (zero API cost) before the real adapters are wired.
>
> **Note on F2.1 placement:** The world state schema comes first in Phase 2 because entity resolution (F2.5) and conflict detection (F2.6) both require `entity_embeddings` to run integration tests. The original ordering (schema in the middle) created a false dependency.

#### Summary

| # | Feature | Status |
|---|---|---|
| F2.1 | World state + session schema migrations | 🔲 |
| F2.2 | Mock LLM + Email adapters (local profile) | 🔲 |
| F2.3 | Session submission + status machine | 🔲 |
| F2.4 | Knowledge extraction pipeline | 🔲 |
| F2.5 | Entity resolution pipeline | 🔲 |
| F2.6 | Conflict detection pipeline | 🔲 |
| F2.7 | Diff generation + draft API | 🔲 |
| F2.8 | Commit endpoint | 🔲 |
| F2.9 | Frontend: Input Mode — session submission + status polling | 🔲 |
| F2.10 | Frontend: Input Mode — diff review screen | 🔲 |
| F2.11 | Frontend: Input Mode — commit flow + draft recovery | 🔲 |

---

#### F2.1 — World state + session schema migrations

**Goal:** Create all Phase 2 domain tables before any pipeline code is written. Entity resolution and conflict detection integration tests require `entity_embeddings` to exist.

**Scope (in):** Liquibase changesets:
- `0006_create_sessions.xml` — `sessions` per ARCHITECTURE.md §5.3; partial unique index `WHERE status IN ('processing', 'draft')` (D-054); `sequence_number INTEGER NULL` (D-069)
- `0007_create_narrative_blocks.xml` — `narrative_blocks` per §5.3
- `0008_create_actors.xml` + `0009_create_actor_versions.xml` — versioning pattern per §5.4
- `0010_create_spaces.xml` + `0011_create_space_versions.xml`
- `0012_create_events.xml` + `0013_create_event_versions.xml`
- `0014_create_relations.xml` + `0015_create_relation_versions.xml`
- `0016_create_entity_embeddings.xml` — `vector(1536)` column; `USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)` index; `CHECK (entity_type IN ('actor', 'space', 'event', 'relation'))` (D-062)
- `0017_create_annotations.xml` — `annotations` per §5.6
- `0018_create_proposals.xml` + `0019_create_proposal_votes.xml` — schema-only; no application code touches these in v1 (D-016)
- Testcontainers integration test: all changesets applied; all tables, indexes (including IVFFlat), and constraints verified

**Scope (out):** JPA entities, domain classes. Proposal approval logic is permanently out of scope for v1.

**Skills:** `database-migration`  
**Decisions:** D-016, D-031, D-054, D-062, D-069  
**Dependencies:** F1.9

---

#### F2.2 — Mock LLM + Email adapters (local profile)

**Goal:** All AI-driven port adapters return deterministic canned responses under the `local` profile. Zero API cost. Unblocks TDD on the full ingestion pipeline.

**Scope (in):**

*Domain model records* (in `com.bluesteel.application.model`): `ExtractedMention`, `ExtractionResult` (includes `narrativeSummaryHeader`), `ResolvedEntity` (outcome: `MATCH|NEW|UNCERTAIN`), `ConflictWarning`, `QueryResponse`, `EntityContext` (per ARCHITECTURE.md §6.2)

*Mock adapters* (all in `adapters.out.ai`, activated `@Profile("local")`):
- `MockNarrativeExtractionAdapter`: returns canned `ExtractionResult` with 1 MATCH-candidate actor, 1 new actor, 1 space, 1 event, 1 relation, and a narrative summary header
- `MockEntityResolutionAdapter`: deterministic outcomes based on mention name: "Mira" → MATCH, "Thornwick" → NEW, "Stranger" → UNCERTAIN
- `MockConflictDetectionAdapter`: returns one `ConflictWarning` on first call; empty list thereafter
- `MockEmbeddingAdapter`: returns deterministic `float[1536]` (zeros except index 0 = 1.0f)
- `MockQueryAnsweringAdapter`: returns canned answer + one citation to `sequence_number = 1`

*Stub real adapters* (non-local profiles): throw `UnsupportedOperationException("Activate llm-real profile")` until F2.4-F2.6 wire real implementations.

`AiConfig.java` updated to register all mock beans under `local` profile.

**Scope (out):** Real Spring AI adapters (F2.4–F2.6). `EmailPort` mock already done in F1.5.

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-032, D-049  
**Dependencies:** F2.1

---

#### F2.3 — Session submission + status machine

**Goal:** Intake API for new sessions. Narrative block stored immutably. Async pipeline triggered. Status polling exposed. Stuck-processing TTL check wired.

**Scope (in):**

*Domain:* `Session` aggregate — state machine (`pending → processing → draft → committed | failed | discarded`); all transitions are methods with guard clauses; invalid transitions throw `InvalidSessionStateTransitionException`. `NarrativeBlock` entity (write-once invariant enforced in domain).

*Application:*
- `SubmitSessionUseCase`: `gm|editor` required; token budget check first (`400 SUMMARY_TOO_LARGE` if over `blue-steel.ingestion.max-tokens`, default 8000); 409 if another session is `processing|draft` for the campaign (D-054); store `NarrativeBlock`; create `Session` in `pending`; publish `SessionSubmittedEvent`; return `{ sessionId, status: "pending" }`
- `GetSessionStatusUseCase`: any campaign member; returns `{ sessionId, status, failureReason?, message? }`
- `DiscardSessionUseCase`: GM-only; from `draft` only → `discarded`; clears `diff_payload`
- `SessionSubmittedEvent` ApplicationEvent record
- `SessionIngestionEventListener` stub (`@EventListener + @Async`): transitions session `pending → processing`; immediately transitions to `failed` with `failureReason = 'PIPELINE_NOT_IMPLEMENTED'` — enables full submission + polling + failure path to be tested before the pipeline exists

*Stuck-processing recovery* (D-074): `@Scheduled(fixedDelayString = "${blue-steel.ingestion.processing-timeout-check-interval-ms:300000}")` — every 5 minutes; finds sessions in `processing` for longer than `blue-steel.ingestion.processing-timeout-minutes` (default 10); bulk-transitions to `failed` with `failure_reason = 'PIPELINE_TIMEOUT'`; logs at WARN per session

*API:*
- `POST /api/v1/campaigns/{id}/sessions` — `gm|editor`; body: `{ "summaryText": "..." }` → 202 with `{ sessionId, status }`; 409 `ACTIVE_SESSION_EXISTS` (response includes `existingSessionId`)
- `GET /api/v1/campaigns/{id}/sessions/{sid}/status` — any member → `{ sessionId, status, failureReason?, message? }`
- `POST /api/v1/campaigns/{id}/sessions/{sid}/discard` — GM-only → 200; 409 if not `draft`

**Scope (out):** The real pipeline logic (F2.4+). The `SessionIngestionEventListener` stub is replaced incrementally in F2.4–F2.7.

**Skills:** `session-ingestion-pipeline`, `backend-domain-model`  
**Decisions:** D-002, D-054, D-069, D-074  
**Dependencies:** F2.2

---

#### F2.4 — Knowledge extraction pipeline

**Goal:** LLM Call 1. Extract actors, spaces, events, and relations from the session summary. Generate the narrative summary header as a co-output (D-005). Wire into the `SessionSubmittedEvent` async listener.

**Scope (in):**

*Application:* `ExtractionPipelineService` (internal): calls `NarrativeExtractionPort`; transitions session `pending → processing` on start; on exception → transitions to `failed` with reason. `SessionIngestionEventListener` extended: calls extraction stage; passes `ExtractionResult` in-memory to the next stage (added in F2.5).

*Adapters:*
- `SpringAiNarrativeExtractionAdapter` (llm-real profile): `ChatClient` → Anthropic; structured output (`BeanOutputConverter`); produces `ExtractionResult`; logs tokens_in, tokens_out, cost_usd at INFO with MDC `stage = "extraction"` (LOG-01, D-072)

**Scope (out):** Entity resolution (F2.5). The listener exits after extraction in this feature.

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-005, D-032, D-034, D-049, D-072  
**Dependencies:** F2.3

---

#### F2.5 — Entity resolution pipeline

**Goal:** Two-stage entity resolution: pgvector similarity search (Stage 1) + LLM call 2 for borderline cases (Stage 2). Produces MATCH / NEW / UNCERTAIN outcomes (D-041, D-042).

**Scope (in):**

*Application:*
- `EntityResolutionService` (internal): for each `ExtractedMention`; Stage 1: embed mention via `EmbeddingPort` → `EntitySimilaritySearchPort` → if max score < `blue-steel.resolution.similarity-floor` (default 0.75): classify NEW immediately (no LLM call); Stage 2: if score ≥ floor: call `EntityResolutionPort` with mention + top-3 `EntityContext` candidates → MATCH / NEW / UNCERTAIN
- `EntitySimilaritySearchPort` driven port: `List<SimilarityResult> search(float[] vector, UUID campaignId, String entityType, int topN)`
- `EntitySimilaritySearchAdapter`: native pgvector query `1 - (embedding <=> $1::vector) AS similarity ... WHERE campaign_id = $2 AND entity_type = $3 ORDER BY embedding <=> $1::vector LIMIT $4` (D-062, ARCH-04)
- `SpringAiEntityResolutionAdapter` (llm-real): LLM call 2; structured output: outcome + matched entity ID; logs MDC `stage = "resolution"`

*`SessionIngestionEventListener` extended:* calls entity resolution after extraction; passes `List<ResolvedEntity>` to next stage.

**Scope (out):** Conflict detection (F2.6). Diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-041, D-042, D-062  
**Dependencies:** F2.4

---

#### F2.6 — Conflict detection pipeline

**Goal:** LLM Call 3. Compare extracted facts against current world state for hard contradictions. Produces non-blocking `ConflictWarning` cards (D-033).

**Scope (in):**

*Application:*
- `ConflictDetectionService` (internal): assembles `EntityContext` for MATCH-resolved entities only via `EntitySimilaritySearchPort` (embed `narrativeSummaryHeader` → top-N relevant snapshots); calls `ConflictDetectionPort`; if no MATCH entities: skip LLM call, return empty list
- `SpringAiConflictDetectionAdapter` (llm-real): pgvector context-scoped LLM call 3; structured output: `List<ConflictWarning>`; logs MDC `stage = "conflict_detection"`

*`SessionIngestionEventListener` extended:* calls conflict detection after entity resolution; passes `List<ConflictWarning>` to next stage.

**Scope (out):** Diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-033, D-034, D-062  
**Dependencies:** F2.5

---

#### F2.7 — Diff generation + draft API

**Goal:** Assemble the structured diff from pipeline outputs, persist as `diff_payload` JSONB, transition session to `draft`, expose the diff retrieval endpoint. Output: a browser-renderable diff conforming to the formal schema in ARCHITECTURE.md §7.6.

**Scope (in):**

*Application:*
- `DiffGenerationService` (internal): assembles `DiffPayload` from `ExtractionResult` + `List<ResolvedEntity>` + `List<ConflictWarning>`; MATCH → `ExistingEntityCard` (delta only, D-006); NEW → `NewEntityCard` (full profile, D-007); UNCERTAIN → `UncertainEntityCard`; conflicts → `ConflictWarningCard`; serializes to JSON; persists to `sessions.diff_payload`; transitions session → `draft`
- `GetSessionDiffUseCase` (driving port): validates `draft` status; deserializes and returns `DiffPayload`

*`SessionIngestionEventListener` completed:* calls diff generation as final stage; session transitions to `draft`.

*API:* `GET /api/v1/campaigns/{id}/sessions/{sid}/diff` — `gm|editor`; 404 if not `draft`; returns `DiffPayload` per formal schema (D-076)

**Scope (out):** Commit (F2.8). User-edited fields are in the commit payload — not persisted to `diff_payload`.

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-004, D-005, D-006, D-007, D-042, D-076  
**Dependencies:** F2.6

---

#### F2.8 — Commit endpoint

**Goal:** Validate commit payload, write world state synchronously, assign `sequence_number`, trigger async embedding generation. Returns 200 immediately.

**Scope (in):**

*Application:*
- `CommitSessionUseCase` (driving port): validates zero UNCERTAIN entities in payload (422 `UNCERTAIN_ENTITIES_PRESENT`, D-042); validates `acknowledged_conflicts` covers all detected conflicts (422 `CONFLICTS_NOT_ACKNOWLEDGED`, D-033); for each card: ACCEPT → create/update entity + append version row; EDIT → same with user-edited fields; DELETE → no world state change; assigns `sequence_number` (`MAX(sequence_number) + 1` inside `@Transactional` with lock, D-069); transitions session → `committed`; clears `diff_payload`; publishes `SessionCommittedEvent`
- `EmbeddingGenerationListener` (`@EventListener + @Async`): handles `SessionCommittedEvent`; for each committed entity version: calls `EmbeddingPort`; inserts row into `entity_embeddings`; failure per entity is logged at ERROR and swallowed — does not fail the listener (D-063)
- `WorldStateRepository` driven port; `WorldStateAdapter` in `adapters.out.persistence` (JPA entities + repositories for all versioned entity tables)

*Scheduled stuck-session TTL check* (D-074): wire `@Scheduled` task here since session + world state schema is now fully in place.

*API:* `POST /api/v1/campaigns/{id}/sessions/{sid}/commit` — `gm|editor`; commit payload per formal schema (D-076); 422 on validation failure; 200 on success

**Scope (out):** Embedding correctness tests (async — use mock `EmbeddingPort` in integration tests or wait for completion with a test spy).

**Skills:** `session-ingestion-pipeline`, `backend-domain-model`  
**Decisions:** D-001, D-002, D-033, D-042, D-063, D-069, D-076  
**Dependencies:** F2.7

---

#### F2.9 — Frontend: Input Mode — session submission + status polling

**Goal:** Session submission form and processing status view. The user submits a summary, sees the pipeline running, and is navigated to the diff review when `draft` is ready.

**Scope (in):**
- `api/sessions.ts`: typed client for `POST /campaigns/{id}/sessions`, `GET /sessions/{id}/status`; `useSubmitSession` mutation; `useSessionStatus` query with `refetchInterval: (data) => data?.status === 'processing' ? 2000 : false`
- TypeScript types mirroring session submission and status response shapes
- `features/input/SubmitSessionPage.tsx`: shadcn/ui `Textarea` + React Hook Form; disabled while in-flight; role guard: redirect if `player`
- `features/input/ProcessingStatusView.tsx`: polling state; navigates to `/sessions/{id}/diff` on `draft`
- Error states: `400 SUMMARY_TOO_LARGE` → inline message with `maxTokens`; `409 ACTIVE_SESSION_EXISTS` → recovery link using `existingSessionId` from response; `failed` status → `failureReason` and `message` displayed
- Route: `/campaigns/:campaignId/sessions/new`

**Scope (out):** Diff review (F2.10).

**Skills:** `frontend-api-resource`, `frontend-testing`  
**Decisions:** D-049, D-054, D-067  
**Dependencies:** F1.7, F2.3

---

#### F2.10 — Frontend: Input Mode — diff review screen

**Goal:** Render all diff card types, handle UNCERTAIN resolution, surface conflict warnings. The Commit button disabled state is the primary guard for D-042.

**Scope (in):**
- `api/sessions.ts` extended: `GET /sessions/{id}/diff` → `useSessionDiff`; TypeScript types mirroring `DiffPayload` per ARCHITECTURE.md §7.6 formal schema (D-076)
- `components/domain/DiffCard.tsx` variants: `ExistingEntityCard` (Accept/Edit/Delete, delta fields only), `NewEntityCard` (full profile), `UncertainEntityCard` (Same entity / Different entity radio — no defer, D-042), `ConflictWarningCard` (acknowledgment checkbox, non-blocking, D-033)
- `features/input/DiffReviewPage.tsx`: narrative summary header; sections by entity type; conflict warnings section at top; progress indicator `"N items require your decision"` when UNCERTAIN or unacknowledged conflicts remain; all card state in `useReducer` (accept/edit/delete decisions, UNCERTAIN resolutions, conflict acknowledgments)
- Commit button: `disabled` derived from `unresolvedUncertainCount + unacknowledgedConflictCount > 0`
- Route: `/campaigns/:campaignId/sessions/:sessionId/diff`

**Scope (out):** Commit API call (F2.11). "Propose a change" affordance is Exploration Mode (Phase 4, D-012).

**Skills:** `frontend-diff-review`, `frontend-testing`  
**Decisions:** D-004, D-005, D-006, D-007, D-033, D-042, D-076  
**Dependencies:** F1.7, F2.7

---

#### F2.11 — Frontend: Input Mode — commit flow + draft recovery

**Goal:** Assemble and submit the commit payload, handle 422 defence-in-depth responses, render success state, and provide draft recovery for users returning mid-review.

**Scope (in):**
- `api/sessions.ts` extended: `POST /sessions/{id}/commit` → `useCommitSession` mutation; commit payload builder (pure function: card decisions + UNCERTAIN resolutions + conflict acknowledgments → `CommitPayload` per ARCHITECTURE.md §7.6 formal schema, D-076)
- `DiffReviewPage` extended: Commit button → assemble payload → call `useCommitSession`; spinner while in-flight; on success: invalidate all campaign query keys; navigate to campaign home
- 422 error handling: `UNCERTAIN_ENTITIES_PRESENT` and `CONFLICTS_NOT_ACKNOWLEDGED` → UI notification (treated as UI bug, not user error); log to console
- Discard button: GM-only (from `campaignStore.activeRole`); confirmation dialog; calls `POST /sessions/{sid}/discard`; navigates to `/sessions/new` on success
- Draft recovery: on `/sessions/new`, check for existing draft; if found → show banner "You have an unfinished review" with link to `/sessions/{id}/diff`; uses the `existingSessionId` from the `409` response of `useSubmitSession`
- `api/sessions.ts` extended: `POST /sessions/{sid}/discard` → `useDiscardSession` mutation

**Scope (out):** World state exploration (Phase 4).

**Skills:** `frontend-diff-review`, `frontend-testing`  
**Decisions:** D-002, D-033, D-042, D-054, D-076  
**Dependencies:** F2.10, F2.8

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
