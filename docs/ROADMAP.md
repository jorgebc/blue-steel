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
| F1.4 | Core schema migration: auth tables | ✅ |
| F1.5 | Admin bootstrap + platform invitation + password change | ✅ |
| F1.6 | Login + JWT issuance + refresh token rotation + logout | ✅ |
| F1.7 | Frontend: walking skeleton + auth scaffold | 🔲 |
| F1.7-SETUP | Frontend scaffolding — Vite alias, Tailwind v4 + theme, shadcn/ui, Vitest setup (human step) | 👤 |
| F1.7.1 | Frontend: shared API envelope + auth + health TypeScript types | ✅ |
| F1.7.2 | Frontend: Zustand auth + campaign stores | ✅ |
| F1.7.3 | Frontend: base HTTP client with silent-refresh on 401 | ✅ |
| F1.7.4 | Frontend: auth + user API resource hooks | ✅ |
| F1.7.5 | Frontend: RequireAuth route guard (+ forced-password-change redirect) | ✅ |
| F1.7.6 | Frontend: InlineBanner feedback component (no-toast) | ✅ |
| F1.7.7 | Frontend: LoginPage form | ✅ |
| F1.7.8 | Frontend: ChangePasswordPage form | ✅ |
| F1.7.9 | Frontend: /status round-trip health page (skeleton loading) | ✅ |
| F1.7.10 | Frontend: app wiring (routes + providers) + Vercel config | ✅ |
| F1.8 | Campaign creation + membership API | ✅ |
| F1.8.1 | Backend: Campaign + CampaignMember domain + CampaignNotFoundException | ✅ |
| F1.8.2 | Backend: campaign driven ports + command/read-model records | ✅ |
| F1.8.3 | Backend: CreateCampaignUseCase + service (admin-only, atomic GM) | ✅ |
| F1.8.4 | Backend: GetCampaignUseCase + service (member-or-admin) | ✅ |
| F1.8.5 | Backend: ListCampaignsUseCase + service (caller's campaigns; admin all) | ✅ |
| F1.8.6 | Backend: campaign persistence adapter (JPA + Testcontainers IT) | ✅ |
| F1.8.7 | Backend: campaign membership persistence adapter + CampaignMembershipPort | ✅ |
| F1.8.8 | Backend: CampaignController + DTOs + 404 mapping | ✅ |
| F1.9 | Campaign-scoped invitation + role enforcement | ✅ |
| F1.9.1 | Backend: CampaignMember.withRole + membership exceptions (CANNOT_REMOVE_GM, ALREADY_CAMPAIGN_MEMBER) | ✅ |
| F1.9.2 | Backend: extend CampaignMembershipRepository + adapter (find/delete/existsByRole) | ✅ |
| F1.9.3 | Backend: TemporaryPasswordGenerator shared component (de-duplicate invite flows) | ✅ |
| F1.9.4 | Backend: InviteCampaignMemberUseCase + service (GM-only, create-or-add, 409 if member) | ✅ |
| F1.9.5 | Backend: ChangeMemberRoleUseCase + service (GM-only, GM role protected) | ✅ |
| F1.9.6 | Backend: RemoveMemberUseCase + service (GM-only, 422 CANNOT_REMOVE_GM) | ✅ |
| F1.9.7 | Backend: SearchUsersUseCase + service (admin or GM-anywhere; search by email) | ✅ |
| F1.9.8 | Backend: CampaignMembershipController + DTOs + 409/422 handler mappings | ✅ |
| F1.9.9 | Backend: UserSearchController + DTO (GET /api/v1/users?email=) | ✅ |
| F1.10 | Frontend: campaign list, selection + home | ✅ |
| F1.10.1 | Frontend: campaign DTO TypeScript types (+ CampaignRole) | ✅ |
| F1.10.2 | Frontend: campaigns API client + list/detail hooks | ✅ |
| F1.10.3 | Frontend: CampaignContextGuard (loads campaign → sets activeRole) | ✅ |
| F1.10.4 | Frontend: CampaignListPage (authenticated `/`) | ✅ |
| F1.10.5 | Frontend: CampaignHomePage (campaign hub + commit target) | ✅ |
| F1.10.6 | Frontend: campaign route wiring (`/`, `/campaigns/:campaignId`) | ✅ |
| F1.11 | Frontend: campaign app-shell + sidebar | ✅ |
| F1.11.1 | Frontend: uiStore (sidebar expand/collapse, persisted) | ✅ |
| F1.11.2 | Frontend: Sidebar component (mode nav, role-gated, collapse) | ✅ |
| F1.11.3 | Frontend: AppShell layout + campaign route restructure | ✅ |
| F1.11.4 | Frontend: admin-create data layer (user search + create campaign) | ✅ |
| F1.11.5 | Frontend: CreateCampaignPage + `/campaigns/new` route (admin) | ✅ |
| F1.11.6 | Frontend: Brand component (reusable wordmark/mark) | ✅ |
| F1.11.7 | Frontend: AppBar global top bar + relocate identity/logout from Sidebar | ✅ |
| F1.11.8 | Frontend: AuthenticatedLayout + authenticated route restructure | ✅ |
| F1.11.9 | Frontend: branded auth surfaces (Login + ChangePassword) | ✅ |
| F1.11.10 | Frontend: warmer empty + welcome surfaces | ✅ |

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

> **Umbrella task — run the F1.7.N sub-tasks below, not this.**

**Goal:** Minimal React app deployed to Vercel that completes the round-trip (browser → Spring Boot → Neon). Full auth UI including login, forced password change, and route guards. The definition of done for the walking skeleton. The original scope is split across `F1.7-SETUP` (human scaffolding) and the ordered `F1.7.1`–`F1.7.10` sub-tasks.

**Scope (out):** Campaign list page. Any feature beyond auth.

**Skills:** `auth`, `frontend-api-resource`, `frontend-testing`  
**Decisions:** D-030, D-043, D-045, D-067, D-068, D-077  
**Dependencies:** F1.6

---

#### F1.7-SETUP — Frontend scaffolding (human runs by hand, once)

> **Human step — not a pipeline sub-task.** A checklist of exact, deterministic commands. All
> sub-tasks assume this is done and import the **real** generated symbols
> (`@/components/ui/button`, `@/lib/utils`, etc.) — never a hallucinated `@shadcn/ui` package.

```bash
cd apps/web
npm install                                   # deps already declared in package.json

# 1. '@/' path alias  — edit apps/web/tsconfig.app.json compilerOptions:
#      "baseUrl": ".",  "paths": { "@/*": ["./src/*"] }
#    and apps/web/vite.config.ts: add  resolve: { alias: { '@': path.resolve(__dirname, './src') } }
#    (import path from 'node:path')

# 2. Tailwind v4 Vite plugin (not yet installed)
npm install -D @tailwindcss/vite
#    vite.config.ts: add tailwindcss() to plugins

# 3. shadcn/ui  (New York style, Slate base, CSS variables)
npx shadcn@latest init                        # creates components.json, src/lib/utils.ts (cn),
                                              #   src/index.css, wires tailwind + index.css import
npx shadcn@latest add button input label form card
#    -> installs @hookform/resolvers, zod, @radix-ui/react-label, @radix-ui/react-slot
#       and writes src/components/ui/{button,input,label,form,card}.tsx

# 4. Blue Steel theme — merge the EXACT @theme block from docs/UX_CONSTITUTION.md §8
#    into the shadcn-generated apps/web/src/index.css (keep shadcn base layer; add the
#    Blue Steel colour/radius/shadow/font tokens). Ensure main.tsx imports './index.css'.

# 5. Vitest setup file  (installed accessibility lib is vitest-axe@0.1.0, NOT jest-axe)
#    create apps/web/src/test/setup.ts  — matcher registration is TWO parts; the
#    extend-expect import is types-only / a RUNTIME NO-OP in this version:
#       import '@testing-library/jest-dom';
#       import 'vitest-axe/extend-expect';            // TS: adds toHaveNoViolations to expect's types
#       import * as axeMatchers from 'vitest-axe/matchers';
#       import { expect } from 'vitest';
#       expect.extend(axeMatchers);                   // registers the matcher at RUNTIME
#    vite.config.ts: set  test.setupFiles: ['./src/test/setup.ts']
#    Tests import `{ axe } from 'vitest-axe'` and assert
#       expect(await axe(container)).toHaveNoViolations().
```

> **Note for all sub-tasks:** the `frontend-testing` skill shows `jest-axe` / `configureAxe` —
> the repo actually installs **`vitest-axe`** (`0.1.0`). Runtime matcher registration requires
> `import * as matchers from 'vitest-axe/matchers'; expect.extend(matchers)` (see SETUP step 5) —
> `import 'vitest-axe/extend-expect'` alone is a **runtime no-op** (TypeScript types only).
> Installed stack is React 19 / `react-router-dom` v7 / Tailwind v4 / Vitest v4 (package.json is
> authoritative over the "React 18 / Router v6" text in `apps/web/CLAUDE.md`).

> **Backend contract (verified against `apps/api`) — every response uses the envelope
> `{ data, meta, errors: [{ code, message, field }] }`:**
> - `POST /api/v1/auth/login` `{ email, password }` → `data: { accessToken, forcePasswordChange }` (**no** userId/isAdmin)
> - `POST /api/v1/auth/refresh` (httpOnly cookie) → `data: { accessToken }`
> - `POST /api/v1/auth/logout` → `data: null`
> - `GET  /api/v1/users/me` → `data: { id, email, isAdmin, forcePasswordChange }`
> - `PATCH /api/v1/users/me/password` `{ currentPassword, newPassword }` → `data: null` (newPassword 12–128 chars)
> - `GET  /api/v1/health` → `data: { status: "UP"|"DEGRADED", db: "UP"|"DOWN" }` (only unauthenticated endpoint)
>
> Because login returns only `{ accessToken, forcePasswordChange }`, `currentUser` is populated
> by calling `GET /users/me` **after** login — not from the login response.

---

#### F1.7.1 — Shared API, auth, and health types

**Goal:** Hand-written TypeScript mirrors of the backend DTOs so every later sub-task imports real, compiling symbols. No runtime logic.

**Scope (in):**
- `apps/web/src/types/api.ts` — `ApiEnvelope<T>` (`{ data: T; meta: unknown; errors: ApiError[] }`), `ApiError` (`{ code: string; message: string; field: string | null }`)
- `apps/web/src/types/auth.ts` — `AuthLoginResponse` (`{ accessToken; forcePasswordChange }`), `RefreshResponse` (`{ accessToken }`), `UserMeResponse` (`{ id; email; isAdmin; forcePasswordChange }`), `CurrentUser`
- `apps/web/src/types/health.ts` — `OverallStatus` (`'UP'|'DEGRADED'`), `ComponentStatus` (`'UP'|'DOWN'`), `HealthResponse` (`{ status; db }`)

**Scope (out):** No fetch logic, hooks, or components. Campaign/session/actor types (later phases). No runtime test — `npm run type-check` is the verification for this types-only sub-task.

**Skills:** `frontend-api-resource`  **Decisions:** D-030  **Dependencies:** F1.7-SETUP

---

#### F1.7.2 — Zustand auth + campaign stores

**Goal:** In-memory client-state stores. Access token never touches `localStorage` (D-030).

**Scope (in):**
- `apps/web/src/types/campaign.ts` — `CampaignRole` (`'gm' | 'editor' | 'player'` — lowercase, mirrors the wire `role`). Minimal stub the store imports; **extended with the campaign DTO types by F1.10.1.**
- `apps/web/src/store/authStore.ts` (+ `authStore.test.ts`) — `accessToken: string | null`; `currentUser: CurrentUser | null`; `setAccessToken`, `setCurrentUser`, `logout`
- `apps/web/src/store/campaignStore.ts` (+ `campaignStore.test.ts`) — `activeCampaignId: string | null`; `activeRole: CampaignRole | null` (imported from `@/types/campaign`; populated by `CampaignContextGuard`, F1.10.3); `setCampaign(campaignId, role)`, `clearCampaign`

**Scope (out):** No fetching, no persistence middleware. `uiStore` (F1.11). Campaign DTO types beyond `CampaignRole` (F1.10.1). Role is never read from the JWT.

**Skills:** `auth`, `frontend-testing`  **Decisions:** D-030, D-043  **Dependencies:** F1.7-SETUP, F1.7.1

---

#### F1.7.3 — Base HTTP client with silent refresh

**Goal:** Single `fetch` wrapper that attaches `Authorization: Bearer`, parses the `{ data, meta, errors }` envelope (throwing on `errors`), and on `401` does one silent `POST /auth/refresh` → retry → on second `401` calls `authStore.logout()` and redirects to `/login`. Concurrent 401s deduped to a single refresh.

**Scope (in):**
- `apps/web/src/api/client.ts` (+ `client.test.ts`) — exports `apiClient.get<T>()`, `apiClient.post<T>()`, `apiClient.patch<T>()`; base URL from `import.meta.env.VITE_API_BASE_URL`; `credentials: 'include'`

**Scope (out):** Per-resource fetch functions and hooks (F1.7.4). Component-facing error UI.

**Skills:** `frontend-api-resource`, `auth`, `frontend-testing`  **Decisions:** D-030, D-043  **Dependencies:** F1.7-SETUP, F1.7.1, F1.7.2

---

#### F1.7.4 — Auth + user API resource hooks

**Goal:** Typed fetch functions + TanStack Query hooks for the auth/user endpoints. Login flow fetches `GET /users/me` after token storage to populate `currentUser` (login response lacks it).

**Scope (in):**
- `apps/web/src/api/auth.ts` (+ `auth.test.ts`) — `login({email,password})`, `logout()`, `getCurrentUser()`; hooks `useLogin` (on success: `setAccessToken` → `getCurrentUser` → `setCurrentUser`), `useLogout`
- `apps/web/src/api/users.ts` (+ `users.test.ts`) — `changePassword({currentPassword,newPassword})`; hook `useChangePassword`

**Scope (out):** `api/health.ts` (lives with F1.7.9). `api/campaigns.ts` (F1.8). Form/UI.

**Skills:** `frontend-api-resource`, `auth`, `frontend-testing`  **Decisions:** D-030, D-043, D-077  **Dependencies:** F1.7-SETUP, F1.7.1, F1.7.3

---

#### F1.7.5 — RequireAuth route guard

**Goal:** Guard component: redirect to `/login` if no `accessToken`; additionally redirect to `/change-password` when `currentUser.forcePasswordChange === true` and not already there (D-077).

**Scope (in):**
- `apps/web/src/components/domain/RequireAuth.tsx` (+ `RequireAuth.test.tsx`, incl. axe assertion)

**Scope (out):** `RequireAdmin` (later, when admin routes exist). Role-based gating. Page content.

**Skills:** `auth`, `frontend-testing`  **Decisions:** D-043, D-077  **Dependencies:** F1.7-SETUP, F1.7.2

---

#### F1.7.6 — InlineBanner feedback component (no-toast)

**Goal:** Shared `components/domain/` banner for all form/system feedback per UX Constitution §5 (toasts forbidden, D-083). Four variants (`success|warning|error|info`) with the exact token classes from §5; `success/warning/info` auto-clear after 8s, `error` never auto-clears; `role="alert"` + `aria-live="polite"`; enter animation `slide-in-from-top-2 duration-200`.

**Scope (in):**
- `apps/web/src/components/domain/InlineBanner.tsx` (+ `InlineBanner.test.tsx`, incl. axe assertion + auto-clear/dismiss behavior)

**Scope (out):** `FocusedOverlay`, skeletons (separate concerns/skills). Page wiring.

**Skills:** `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-083, D-087  **Dependencies:** F1.7-SETUP

---

#### F1.7.7 — LoginPage

**Goal:** Email/password form (React Hook Form + shadcn `Form`/`Input`/`Button`, in a `rounded-2xl` card). Submit via `useLogin`; map `400` field errors with `setError`; surface auth failure through `InlineBanner` (error variant); submit button shows in-button `Loader2` while pending. On success redirect to `/change-password` if `forcePasswordChange`, else `/` (the authenticated home — the F1.7.10 placeholder until F1.10.6 makes `/` the campaign list).

**Scope (in):**
- `apps/web/src/features/auth/LoginPage.tsx` (+ `LoginPage.test.tsx`, incl. axe assertion)

**Scope (out):** ChangePasswordPage (F1.7.8). Route registration (F1.7.10).

**Skills:** `react-hook-form`, `frontend-api-resource`, `auth`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-067, D-077, D-083, D-087  **Dependencies:** F1.7-SETUP, F1.7.1, F1.7.2, F1.7.4, F1.7.6

---

#### F1.7.8 — ChangePasswordPage

**Goal:** Forced/voluntary password-change form (RHF + shadcn, card layout). `newPassword` client-validated to min 12 chars (mirrors backend); submit via `useChangePassword`; `InlineBanner` for success/error; in-button `Loader2` while pending. On success clear `forcePasswordChange` in `currentUser` and redirect to `/` (the authenticated home — campaign list once F1.10.6 lands).

**Scope (in):**
- `apps/web/src/features/auth/ChangePasswordPage.tsx` (+ `ChangePasswordPage.test.tsx`, incl. axe assertion)

**Scope (out):** The redirect-here guard logic (in F1.7.5). Route registration (F1.7.10).

**Skills:** `react-hook-form`, `frontend-api-resource`, `auth`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-067, D-077, D-083, D-087  **Dependencies:** F1.7-SETUP, F1.7.2, F1.7.4, F1.7.6

---

#### F1.7.9 — `/status` round-trip health page (skeleton loading)

**Goal:** Public page that calls `GET /api/v1/health` and renders `db: "UP"` — the walking skeleton's browser → Spring Boot → Neon round-trip proof. Loading state is a DTO-derived **skeleton** (no spinner, D-086); fetch failure surfaces via `InlineBanner` error variant.

**Scope (in):**
- `apps/web/src/api/health.ts` — `getHealth()` + `useHealth` query hook
- `apps/web/src/features/status/StatusPage.tsx` (+ `StatusPage.test.tsx`, incl. axe assertion)

**Scope (out):** Auth gating (this route is public — health is the only unauthenticated endpoint). Skeleton-crafting polish beyond the health DTO.

**Skills:** `frontend-api-resource`, `ux-skeleton-crafting`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-030, D-086, D-087  **Dependencies:** F1.7-SETUP, F1.7.1, F1.7.3, F1.7.6

---

#### F1.7.10 — App wiring + Vercel config

**Goal:** Compose everything: `QueryClientProvider` + `BrowserRouter` + routes (`/login`, `/change-password`, and `/` behind `RequireAuth`; `/status` public). Add Vercel SPA rewrite and document `VITE_API_BASE_URL`.

**Scope (in):**
- `apps/web/src/main.tsx` — replace the placeholder `<div>` with providers + `<Routes>`
- `apps/web/vercel.json` — SPA rewrite (`/(.*) → /index.html`)
- `apps/web/README.md` — document `VITE_API_BASE_URL` as a required Vercel env var

**Scope (out):** Campaign list / dashboard routes (F1.8+). CI deploy step (Vercel GitHub integration handles it, D-045).

**Skills:** `frontend-api-resource`, `auth`  **Decisions:** D-030, D-045  **Dependencies:** F1.7-SETUP, F1.7.2, F1.7.5, F1.7.7, F1.7.8, F1.7.9

---

#### F1.8 — Campaign creation + membership API

> **Umbrella task — run the F1.8.N sub-tasks below, not this.**
> No human SETUP step: the `campaigns` + `campaign_members` schema already exists (migrations
> 0003/0004, F1.4). F1.8 writes no Liquibase changesets.

**Goal:** Admin creates campaigns with an atomic GM assignment. Users retrieve campaigns they belong to. Establishes `CampaignMembershipPort` — the canonical authorization check used by all subsequent features.

**API surface (delivered across the sub-tasks):**
- `POST /api/v1/campaigns` — admin only; `{ "name": "...", "gmUserId": "..." }` → 201
- `GET /api/v1/campaigns` — returns campaigns where caller is a member (admin: all)
- `GET /api/v1/campaigns/{id}` — returns campaign + caller's role; 404/403 if not a member

**Scope (out):** Member add/remove (F1.9). Campaign-scoped invitation (F1.9).

**Skills:** `backend-endpoint`, `backend-domain-model`  
**Decisions:** D-024, D-025, D-043, D-061  
**Dependencies:** F1.6

---

#### F1.8.1 — Campaign + CampaignMember domain

**Goal:** Pure-Java domain for the campaign aggregate and a membership value object, plus the not-found exception the read services raise. Name must be non-blank (mirrors `User`'s invariant).

**Scope (in):**
- `domain/campaign/Campaign.java` (+ `domain/campaign/CampaignTest.java`) — fields `id, name, createdBy, createdAt`; static `create(...)`; constructor rejects blank `name`
- `domain/campaign/CampaignMember.java` (+ its test) — `id, campaignId, userId, role (CampaignRole), joinedAt`; rejects null `role`
- `domain/exception/CampaignNotFoundException.java` — own exception type (mapped to 404 in F1.8.8, not the 422 `DomainException` default)

**Scope (out):** Persistence/JPA (F1.8.6/7). Use-case orchestration (F1.8.3–5). The "exactly one GM" rule is enforced atomically by the create service (F1.8.3) + the existing DB singleton-GM index, not in the domain constructor.

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-061  **Dependencies:** F1.6

---

#### F1.8.2 — Campaign driven ports + command/read-model records

**Goal:** The application boundary for campaigns — driven-port interfaces and the command/read-model value types every later sub-task imports. Declaration-only; verified by `mvn compile` + ArchUnit.

**Scope (in):**
- `application/port/out/campaign/CampaignRepository.java` — `void save(Campaign)`, `Optional<Campaign> findById(UUID)`, `List<Campaign> findAll()`, `List<Campaign> findAllByMemberId(UUID userId)`
- `application/port/out/campaign/CampaignMembershipRepository.java` — `void save(CampaignMember)`
- `application/port/out/campaign/CampaignMembershipPort.java` — `Optional<CampaignRole> resolveRole(UUID campaignId, UUID userId)` (the canonical authz check, D-043)
- `application/model/campaign/CreateCampaignCommand.java` — `record(UUID callerId, boolean callerIsAdmin, String name, UUID gmUserId)`
- `application/model/campaign/CampaignView.java` — `record(UUID id, String name, UUID createdBy, Instant createdAt, CampaignRole role)`; `role` nullable (null when admin lists a campaign they are not a member of)

**Scope (out):** Implementations (services F1.8.3–5; adapters F1.8.6/7). No test — interfaces and records carry no logic; ArchUnit (ARCH-07/08) guards their placement.

**Skills:** `backend-endpoint`, `auth`  **Decisions:** D-043  **Dependencies:** F1.6, F1.8.1

---

#### F1.8.3 — CreateCampaignUseCase + service

**Goal:** Admin-only campaign creation that inserts the `campaigns` row and the GM `campaign_members` row in one `@Transactional` call (D-061). Validates `gmUserId` resolves to a real user.

**Scope (in):**
- `application/port/in/campaign/CreateCampaignUseCase.java` — `CampaignView create(CreateCampaignCommand)`
- `application/service/campaign/CreateCampaignService.java` (+ its test, mocked ports) — throws `UnauthorizedException` when `!command.callerIsAdmin()`; verifies `gmUserId` via `UserRepository` (else a not-found/domain error); builds `Campaign` + GM `CampaignMember`; saves both; returns `CampaignView` with `role = GM`

**Scope (out):** Read use cases (F1.8.4/5). Real persistence (F1.8.6/7) — the service test mocks `CampaignRepository`, `CampaignMembershipRepository`, `UserRepository`. HTTP/controller (F1.8.8).

**Skills:** `backend-endpoint`, `backend-testing`, `auth`  **Decisions:** D-024, D-025, D-061  **Dependencies:** F1.6, F1.8.1, F1.8.2

---

#### F1.8.4 — GetCampaignUseCase + service

**Goal:** Return one campaign with the caller's role. Members and admins succeed; non-members get 403; a missing campaign gives 404.

**Scope (in):**
- `application/port/in/campaign/GetCampaignUseCase.java` — `CampaignView get(UUID campaignId, UUID callerId, boolean callerIsAdmin)`
- `application/service/campaign/GetCampaignService.java` (+ its test, mocked ports) — load campaign or throw `CampaignNotFoundException` (404); resolve role via `CampaignMembershipPort`; non-admin non-member → `UnauthorizedException` (403); admin gets `role` = resolved-or-null

**Scope (out):** List (F1.8.5). Create (F1.8.3). Persistence (F1.8.6/7). Controller (F1.8.8).

**Skills:** `backend-endpoint`, `backend-testing`, `auth`  **Decisions:** D-043  **Dependencies:** F1.6, F1.8.1, F1.8.2

---

#### F1.8.5 — ListCampaignsUseCase + service

**Goal:** Return the campaigns the caller belongs to; an admin caller gets all campaigns.

**Scope (in):**
- `application/port/in/campaign/ListCampaignsUseCase.java` — `List<CampaignView> list(UUID callerId, boolean callerIsAdmin)`
- `application/service/campaign/ListCampaignsService.java` (+ its test, mocked ports) — admin → `CampaignRepository.findAll()` (role per item resolved or null); else `findAllByMemberId(callerId)` with each item's role from `CampaignMembershipPort`

**Scope (out):** Single-campaign get (F1.8.4). Create (F1.8.3). Persistence (F1.8.6/7). Controller (F1.8.8).

**Skills:** `backend-endpoint`, `backend-testing`, `auth`  **Decisions:** D-024, D-043  **Dependencies:** F1.6, F1.8.1, F1.8.2

---

#### F1.8.6 — Campaign persistence adapter

**Goal:** JPA-backed `CampaignRepository` over the existing `campaigns` table, verified with a Testcontainers integration test.

**Scope (in):**
- `adapters/out/persistence/campaign/CampaignJpaEntity.java` — `@Entity @Table(name="campaigns")`, package-private, mirroring `UserJpaEntity` style
- `adapters/out/persistence/campaign/CampaignJpaRepository.java` — `extends JpaRepository<…, UUID>`; derived query (or `@Query` joining `campaign_members`) backing `findAllByMemberId`
- `adapters/out/persistence/campaign/CampaignPersistenceAdapter.java` (+ Testcontainers IT extending `TestcontainersPostgresBaseIT`) — implements `CampaignRepository`; `toDomain`/`toEntity` mappers

**Scope (out):** Membership table mapping + `CampaignMembershipPort` (F1.8.7). No new migration — schema is from F1.4.

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-024  **Dependencies:** F1.6, F1.8.1, F1.8.2

---

#### F1.8.7 — Campaign membership persistence adapter

**Goal:** JPA-backed `CampaignMembershipRepository` + `CampaignMembershipPort` over the existing `campaign_members` table — the canonical DB-resolved authz check (D-043).

**Scope (in):**
- `adapters/out/persistence/campaign/CampaignMemberJpaEntity.java` — `@Entity @Table(name="campaign_members")`; `role` persisted as the lowercase text the CHECK constraint expects
- `adapters/out/persistence/campaign/CampaignMemberJpaRepository.java` — `extends JpaRepository`; `Optional<…> findByCampaignIdAndUserId(UUID, UUID)`
- `adapters/out/persistence/campaign/CampaignMembershipAdapter.java` (+ Testcontainers IT) — implements both `CampaignMembershipRepository` (`save`) and `CampaignMembershipPort` (`resolveRole`, mapping stored role text to `CampaignRole`); IT asserts the MATCH/empty outcomes

**Scope (out):** Campaign-table mapping (F1.8.6). Member add/remove/role-change (F1.9).

**Skills:** `backend-endpoint`, `backend-testing`, `auth`  **Decisions:** D-043, D-061  **Dependencies:** F1.6, F1.8.1, F1.8.2

---

#### F1.8.8 — CampaignController + DTOs

**Goal:** REST surface for campaign create/get/list, plus the 404 mapping for a missing campaign.

**Scope (in):**
- `adapters/in/web/campaign/CampaignController.java` (+ `@WebMvcTest`) — `POST /api/v1/campaigns` (`@PreAuthorize("hasRole('ADMIN')")`, `callerId` from `SecurityContextHolder`, → 201); `GET /api/v1/campaigns` (→ 200 list); `GET /api/v1/campaigns/{id}` (→ 200; 403/404 surfaced from the service); calls `port/in` use cases only (ARCH-05)
- `adapters/in/web/campaign/CreateCampaignRequest.java` — `record(@NotBlank String name, @NotNull UUID gmUserId)`
- `adapters/in/web/campaign/CampaignResponse.java` — `record(UUID id, String name, UUID createdBy, Instant createdAt, String role)` (role lowercased; null when absent), built from `CampaignView`
- `adapters/in/web/GlobalExceptionHandler.java` — **edit existing**: add `@ExceptionHandler(CampaignNotFoundException.class)` → 404 `CAMPAIGN_NOT_FOUND`

**Scope (out):** Invitation/member endpoints (F1.9). Frontend campaign UI (deferred). Service logic (F1.8.3–5).

**Skills:** `backend-endpoint`, `backend-testing`, `error-handling`  **Decisions:** D-024, D-043, D-061, D-064  **Dependencies:** F1.6, F1.8.3, F1.8.4, F1.8.5

---

#### F1.9 — Campaign-scoped invitation + role enforcement

> **Umbrella task — run the F1.9.N sub-tasks below, not this.**
> **No human SETUP step:** the `campaign_members` schema already exists (migrations
> 0003/0004, F1.4); `EmailPort` + `PasswordEncoder` + the temp-password / create-or-refresh
> pattern already exist (F1.6, `InvitePlatformUserService`). F1.9 writes no Liquibase changesets.

**Goal:** GM manages campaign membership: invites members, changes roles, removes members. Role enforcement via `CampaignMembershipPort` applied to all campaign-scoped use cases.

**API surface (delivered across the sub-tasks):**
- `POST /api/v1/campaigns/{id}/invitations` — GM-only; `{ "email": "...", "role": "editor"|"player" }` → 201 (new user created) or 200 (existing user added to campaign); 409 if already a member (D-064)
- `PATCH /api/v1/campaigns/{id}/members/{uid}` — GM-only; `{ "role": "editor"|"player" }` → 200
- `DELETE /api/v1/campaigns/{id}/members/{uid}` — GM-only; 422 if target is GM, else → 200
- `GET /api/v1/users?email=...` — admin + GM; search existing platform users by email

**Role enforcement (realized inside each new service, not a separate retrofit):** the F1.8 read services already self-authorize, so F1.9 applies the canonical pattern only to its own GM-only services — `resolveRole(campaignId, callerId).orElseThrow(UnauthorizedException::new)` then assert the resolved role is `GM` (D-043).

**Scope (out):** Frontend campaign management UI (deferred — out of Phase 1 scope).

**Skills:** `auth`, `backend-endpoint`  
**Decisions:** D-015, D-043, D-061, D-064, D-075, D-077  
**Dependencies:** F1.8

---

#### F1.9.1 — CampaignMember.withRole + membership exceptions

**Goal:** Add the domain mutation method and the two signal exceptions the membership services raise.

**Scope (in):**
- `domain/campaign/CampaignMember.java` (edit, F1.8.1; + update `CampaignMemberTest`) — add `CampaignMember withRole(CampaignRole newRole)` returning a copy with the changed role (same `id`/`campaignId`/`userId`/`joinedAt`)
- `domain/exception/CannotRemoveGmException.java` (new) — raised by the remove/role-change services; mapped to 422 `CANNOT_REMOVE_GM` in F1.9.8
- `domain/exception/AlreadyCampaignMemberException.java` (new) — raised by the invite service; mapped to 409 `ALREADY_CAMPAIGN_MEMBER` in F1.9.8 (D-064)

**Scope (out):** Persistence (F1.9.2). Handler → HTTP mapping (F1.9.8). Service orchestration (F1.9.4–6).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-064  **Dependencies:** F1.8.1

---

#### F1.9.2 — Extend CampaignMembershipRepository + adapter

**Goal:** Give the membership persistence port the read/delete/role-existence operations the mutation services (F1.9.4–6) and the user-search authz (F1.9.7) need, over the existing `campaign_members` table.

**Scope (in):**
- `application/port/out/campaign/CampaignMembershipRepository.java` (edit, F1.8.2) — add `Optional<CampaignMember> findByCampaignIdAndUserId(UUID campaignId, UUID userId)`, `void deleteByCampaignIdAndUserId(UUID campaignId, UUID userId)`, `boolean existsByUserIdAndRole(UUID userId, CampaignRole role)`
- `adapters/out/persistence/campaign/CampaignMemberJpaRepository.java` (edit, F1.8.7) — add the derived `deleteByCampaignIdAndUserId` + `existsByUserIdAndRole` queries (reuse the existing `findByCampaignIdAndUserId`)
- `adapters/out/persistence/campaign/CampaignMembershipAdapter.java` (edit, F1.8.7; + extend its Testcontainers IT) — implement the three new port methods; IT asserts the find/delete/exists outcomes

**Scope (out):** `resolveRole` / `save` (already F1.8.2 / F1.8.7). Domain method (F1.9.1).

**Skills:** `backend-endpoint`, `backend-testing`, `auth`  **Decisions:** D-043  **Dependencies:** F1.9.1, F1.8.2, F1.8.7

---

#### F1.9.3 — TemporaryPasswordGenerator shared component

**Goal:** Extract the `SecureRandom` temp-password generation duplicated by the platform invite into one reusable component, so the campaign invite (F1.9.4) reuses it instead of copying the block (keeps the Sonar duplication gate green).

**Scope (in):**
- `application/service/user/TemporaryPasswordGenerator.java` (new, `@Component`; + its test) — `String generate()` (16 chars, `SecureRandom`), lifted verbatim from `InvitePlatformUserService`
- `application/service/user/InvitePlatformUserService.java` (edit, F1.6) — inject and delegate to the generator; remove the inline `generateTemporaryPassword` method + the char/length constants

**Scope (out):** The campaign invite service itself (F1.9.4). Per-service email body templating (stays where it is).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-077  **Dependencies:** F1.8

---

#### F1.9.4 — InviteCampaignMemberUseCase + service

**Goal:** GM invites a member by email: create the account if new (force-password-change) or refresh credentials if it exists, then add them to `campaign_members` with the requested role — all in one transaction; email the temp password; 409 if the user is already a member of this campaign (D-064).

**Scope (in):**
- `application/port/in/campaign/InviteCampaignMemberUseCase.java` — `boolean invite(InviteCampaignMemberCommand)` (`true` when a new account was created → controller maps to 201, else 200)
- `application/model/campaign/InviteCampaignMemberCommand.java` — `record(UUID campaignId, UUID callerId, String email, CampaignRole role)`
- `application/service/campaign/InviteCampaignMemberService.java` (+ test, mocked ports) — `@Transactional`; `resolveRole(campaignId, callerId)` must be `GM` else `UnauthorizedException`; find user by email → create (via `User.create`) or refresh (`withRefreshedInvitation`) using `TemporaryPasswordGenerator` + `PasswordEncoder`; if `findByCampaignIdAndUserId` is present → `AlreadyCampaignMemberException`; save the `CampaignMember` with the role; `emailPort.send(...)`

**Scope (out):** REST/controller (F1.9.8). Role change / removal (F1.9.5/6). `role == GM` rejection is enforced at the adapter in F1.9.8 (invitable roles are editor/player only).

**Skills:** `auth`, `backend-endpoint`, `backend-testing`  **Decisions:** D-015, D-064, D-075, D-077  **Dependencies:** F1.9.1, F1.9.2, F1.9.3, F1.8.2

---

#### F1.9.5 — ChangeMemberRoleUseCase + service

**Goal:** GM changes a non-GM member's role within their campaign. A GM's role cannot be changed.

**Scope (in):**
- `application/port/in/campaign/ChangeMemberRoleUseCase.java` — `void change(ChangeMemberRoleCommand)`
- `application/model/campaign/ChangeMemberRoleCommand.java` — `record(UUID campaignId, UUID callerId, UUID targetUserId, CampaignRole newRole)`
- `application/service/campaign/ChangeMemberRoleService.java` (+ test, mocked ports) — caller must resolve to `GM` (else `UnauthorizedException`); load target via `findByCampaignIdAndUserId` (else `CampaignNotFoundException`, 404 per F1.8.1); if the target's current role is `GM` → `CannotRemoveGmException` (422); else save `member.withRole(newRole)`

**Scope (out):** Invite (F1.9.4). Remove (F1.9.6). Controller (F1.9.8).

**Skills:** `auth`, `backend-endpoint`, `backend-testing`  **Decisions:** D-015, D-043  **Dependencies:** F1.9.1, F1.9.2, F1.8.2

---

#### F1.9.6 — RemoveMemberUseCase + service

**Goal:** GM removes a non-GM member from their campaign; removing the GM is rejected with 422.

**Scope (in):**
- `application/port/in/campaign/RemoveMemberUseCase.java` — `void remove(RemoveMemberCommand)`
- `application/model/campaign/RemoveMemberCommand.java` — `record(UUID campaignId, UUID callerId, UUID targetUserId)`
- `application/service/campaign/RemoveMemberService.java` (+ test, mocked ports) — caller must resolve to `GM` (else `UnauthorizedException`); load target via `findByCampaignIdAndUserId`; if the target's role is `GM` → `CannotRemoveGmException` (422 `CANNOT_REMOVE_GM`); else `deleteByCampaignIdAndUserId(campaignId, targetUserId)`

**Scope (out):** Invite (F1.9.4). Role change (F1.9.5). Controller (F1.9.8).

**Skills:** `auth`, `backend-endpoint`, `backend-testing`  **Decisions:** D-043, D-061  **Dependencies:** F1.9.1, F1.9.2, F1.8.2

---

#### F1.9.7 — SearchUsersUseCase + service

**Goal:** Let an admin, or any user who is a GM of at least one campaign, look up existing platform users by email (so a GM can invite an existing account). Reuses the `UserProfile` read model.

**Scope (in):**
- `application/port/in/user/SearchUsersUseCase.java` — `List<UserProfile> searchByEmail(String email, UUID callerId, boolean callerIsAdmin)`
- `application/service/user/SearchUsersService.java` (+ test, mocked ports) — authorize when `callerIsAdmin || campaignMembershipRepository.existsByUserIdAndRole(callerId, GM)`, else `UnauthorizedException`; `userRepository.findByEmail(email)` → 0/1 `UserProfile` (empty list if none)

**Scope (out):** Controller/DTO (F1.9.9). Partial/fuzzy search (exact email match only in v1).

**Skills:** `auth`, `backend-endpoint`, `backend-testing`  **Decisions:** D-043, D-064  **Dependencies:** F1.9.2, F1.8.2

---

#### F1.9.8 — CampaignMembershipController + DTOs + handler mappings

**Goal:** REST surface for campaign-scoped invitation, role change, and removal, plus the new exception → HTTP mappings.

**Scope (in):**
- `adapters/in/web/campaign/CampaignMembershipController.java` (+ `@WebMvcTest`) — `POST /api/v1/campaigns/{id}/invitations` (201 new / 200 existing), `PATCH /api/v1/campaigns/{id}/members/{uid}` (200), `DELETE /api/v1/campaigns/{id}/members/{uid}` (200); `callerId` from `SecurityContextHolder`; calls `port/in` use cases only (ARCH-05); no `@PreAuthorize` (GM authz is in-service)
- `adapters/in/web/campaign/InviteCampaignMemberRequest.java` — `record(@NotBlank @Email String email, @NotNull CampaignRole role)`; reject `role == GM` (Bean Validation `@AssertTrue`) so only editor/player are invitable
- `adapters/in/web/campaign/ChangeMemberRoleRequest.java` — `record(@NotNull CampaignRole role)`
- `adapters/in/web/GlobalExceptionHandler.java` (edit existing) — add `@ExceptionHandler(AlreadyCampaignMemberException.class)` → 409 `ALREADY_CAMPAIGN_MEMBER` and `@ExceptionHandler(CannotRemoveGmException.class)` → 422 `CANNOT_REMOVE_GM`

**Scope (out):** `GET /api/v1/users` (F1.9.9). Service logic (F1.9.4–6). Frontend UI (deferred).

**Skills:** `backend-endpoint`, `backend-testing`, `error-handling`  **Decisions:** D-015, D-064  **Dependencies:** F1.9.1, F1.9.4, F1.9.5, F1.9.6

---

#### F1.9.9 — UserSearchController + DTO

**Goal:** Expose `GET /api/v1/users?email=` for admin/GM user lookup, separate from the existing `/api/v1/users/me` controller.

**Scope (in):**
- `adapters/in/web/user/UserSearchController.java` (+ `@WebMvcTest`) — `@RequestMapping("/api/v1/users")`, `@GetMapping(params = "email")`; resolves `callerId` + `callerIsAdmin` (from authentication authorities, e.g. `ROLE_ADMIN`); delegates to `SearchUsersUseCase`; no `@PreAuthorize` (admin-or-GM authz is in-service)
- `adapters/in/web/user/UserSearchResponse.java` — `record(UUID id, String email)` built from `UserProfile`

**Scope (out):** Membership endpoints (F1.9.8). Service logic (F1.9.7).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-064  **Dependencies:** F1.9.7

---

#### F1.10 — Frontend: campaign list, selection + home

> **Umbrella task — run the F1.10.N sub-tasks below, not this.**

**Goal:** The frontend campaign surface the Input-Mode flow depends on: list the caller's campaigns, enter one, and land on a campaign home. Critically, entering a campaign is what **populates `campaignStore` (`activeCampaignId` + `activeRole`)** — the role context every campaign-scoped feature (F2.9–F2.11) reads. Campaign home is also the navigation target after a successful commit.

> **No SETUP required.** Uses only the shadcn primitives F1.7 already installed (`card`, `button`); no new component, package, or config. The backend campaign API is F1.8.

**Scope (out):** The full sidebar/app-shell + mode navigation + `uiStore` + admin campaign-creation UI — all handled by **F1.11** (the next task). Member management UI for F1.9 endpoints (not in v1 Input-Mode slice).

**Skills:** `frontend-api-resource`, `ux-skeleton-crafting`, `ux-inline-feedback`, `auth`, `frontend-testing`  
**Decisions:** D-043, D-086, D-087  
**Dependencies:** F1.7, F1.8

> **Backend contract (verified against F1.8.8 `CampaignController` + `CampaignResponse`). Envelope
> `{ data, meta, errors: [{ code, message, field }] }`; all IDs UUID, timestamps ISO-8601:**
> - `GET /api/v1/campaigns` → `data: CampaignResponse[]` (caller's campaigns; admin → all).
> - `GET /api/v1/campaigns/{campaignId}` → `data: CampaignResponse`; **403 `FORBIDDEN`** (non-member), **404 `CAMPAIGN_NOT_FOUND`**.
> - `CampaignResponse` = `{ id: string, name: string, createdBy: string, createdAt: string, role: 'gm' | 'editor' | 'player' | null }`. **`role` is LOWERCASE** (F1.8.8 "role lowercased") — matches the FE `CampaignRole` union and F2.9.4's `activeRole === 'player'` check; `null` only when an admin lists a campaign they don't belong to.
> - `POST /api/v1/campaigns` is **admin-only** — no self-create UI in v1 (D-051); the admin campaign-creation screen is deferred to F1.11.

---

#### F1.10.1 — Campaign DTO TypeScript types

**Goal:** Hand-written mirrors of the F1.8.8 campaign DTO so the client + pages import real, compiling symbols. Extends the `CampaignRole` stub F1.7.2 already created.

**Scope (in):**
- `apps/web/src/types/campaign.ts` (**extend**, F1.7.2) — add `CampaignResponse` / `Campaign` (`{ id: string; name: string; createdBy: string; createdAt: string; role: CampaignRole | null }`), reusing the existing `CampaignRole` union (`'gm' | 'editor' | 'player'`, lowercase).

**Scope (out):** Fetch logic/hooks (F1.10.2); components (F1.10.3+). Member/invitation types (F1.9 UI, deferred). No runtime test — `npm run type-check` verifies this types-only sub-task.

**Skills:** `frontend-api-resource`  **Decisions:** D-043  **Dependencies:** F1.8, F1.7.2

---

#### F1.10.2 — campaigns API client + list/detail hooks

**Goal:** Typed fetch functions + TanStack Query hooks for the two campaign reads.

**Scope (in):**
- `apps/web/src/api/campaigns.ts` (+ `campaigns.test.ts`) — `campaignKeys` query-key factory (`all`, `detail(id)`); `getCampaigns(): Promise<CampaignResponse[]>` → `GET /api/v1/campaigns`; `getCampaign(campaignId): Promise<CampaignResponse>` → `GET .../{id}`; hooks `useCampaigns()` and `useCampaign(campaignId, enabled?)`. All via `apiClient` (F1.7.3). Tests mock `apiClient`; assert URLs, the parsed list/detail, and that the 403/404 error path propagates.

**Scope (out):** Components/pages/guard (F1.10.3+). Create/mutation (admin UI, F1.11).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-043  **Dependencies:** F1.10.1, F1.7.3

---

#### F1.10.3 — CampaignContextGuard (loads campaign → sets activeRole)

**Goal:** The keystone: a route wrapper that loads the campaign for `:campaignId` and writes the active campaign + role into `campaignStore`, so every nested campaign-scoped route (F2.9–F2.11) can read `activeRole`. Loading is a skeleton (D-086); membership failure redirects out.

**Scope (in):**
- `apps/web/src/components/domain/CampaignContextGuard.tsx` (+ `CampaignContextGuard.test.tsx`, incl. axe) — reads `:campaignId` via `useParams`; `useCampaign(campaignId)`; while loading render a DTO-derived `animate-pulse` skeleton; on success call `useCampaignStore.setCampaign(campaignId, role)` then render `<Outlet />`; on `403`/`404` render an error `InlineBanner` and `<Navigate to="/" replace />`. Clears/overwrites stale store context when `campaignId` changes.

**Scope (out):** The pages it wraps (F1.10.4/F1.10.5). Route registration (F1.10.6). Per-feature role gating (the consuming features, e.g. F2.9.4).

**Skills:** `frontend-api-resource`, `auth`, `ux-skeleton-crafting`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-043, D-086, D-087  **Dependencies:** F1.10.2, F1.7.2, F1.7.6

---

#### F1.10.4 — CampaignListPage (authenticated `/`)

**Goal:** The post-login landing: list the caller's campaigns as cards linking into each campaign home.

**Scope (in):**
- `apps/web/src/features/campaigns/CampaignListPage.tsx` (+ `CampaignListPage.test.tsx`, incl. axe) — `useCampaigns()`; renders a `rounded-2xl` card per campaign (`name`, role badge) linking to `/campaigns/{id}`; DTO-derived skeleton while loading (D-086); empty state ("No campaigns yet — ask your GM or an admin to add you"); fetch error → error `InlineBanner`.

**Scope (out):** Route registration (F1.10.6). Campaign home (F1.10.5). Create-campaign affordance (admin, F1.11).

**Skills:** `frontend-api-resource`, `ux-skeleton-crafting`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-086, D-087  **Dependencies:** F1.10.2, F1.7.6

---

#### F1.10.5 — CampaignHomePage (campaign hub + commit target)

**Goal:** The per-campaign landing at `/campaigns/:campaignId` — the hub the user reaches on entry and after a successful commit (F2.11.5). Minimal in this slice.

**Scope (in):**
- `apps/web/src/features/campaigns/CampaignHomePage.tsx` (+ `CampaignHomePage.test.tsx`, incl. axe) — reads `:campaignId`; uses the cached `useCampaign(campaignId)` (populated by `CampaignContextGuard`); shows the campaign name + entry cards: a **"New session"** card linking to `/campaigns/{campaignId}/sessions/new` (Input Mode), plus **disabled "Coming soon"** stubs for Query Mode (Phase 3) and Exploration (Phase 4).

**Scope (out):** The persistent sidebar/app-shell + mode nav (F1.11). The session pages themselves (F2.9+). Route registration (F1.10.6).

**Skills:** `frontend-api-resource`, `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.10.2

---

#### F1.10.6 — campaign route wiring (`/`, `/campaigns/:campaignId`)

**Goal:** Register the campaign routes and the guarded campaign subtree that all campaign-scoped features nest under.

**Scope (in):**
- `apps/web/src/main.tsx` (**edit**, F1.7.10) — set the authenticated `/` route's element to `CampaignListPage` (replacing the F1.7.10 placeholder); add a campaign subtree `/campaigns/:campaignId` whose element is `CampaignContextGuard` (so `activeRole` is populated for everything nested), with `CampaignHomePage` as its index route. All under the existing `RequireAuth` (F1.7.5). The Input-Mode session routes are nested **inside** this `:campaignId` guarded subtree by F2.9.5 / F2.10.12. No new providers.

**Scope (out):** Session routes (F2.9.5/F2.10.12 nest into this subtree). Sidebar/app-shell (F1.11).

**Skills:** `frontend-api-resource`, `auth`  **Decisions:** D-087  **Dependencies:** F1.10.3, F1.10.4, F1.10.5, F1.7.5, F1.7.10

---

#### F1.11 — Frontend: campaign app-shell + sidebar

> **Umbrella task — run the F1.11.N sub-tasks below, not this.**

**Goal:** Wrap the campaign-scoped routes in the persistent **app-shell + Sidebar** from UX Constitution §6 (`uiStore`-driven collapse, role-gated mode nav), and add the admin campaign-creation flow. Builds now: Query/Exploration nav render as disabled "coming soon" stubs (like F1.10.5) until their phases ship. Extended (F1.11.6–F1.11.10) with a **global top bar (AppBar)** wrapping every authenticated page — giving the campaign list/create pages persistent chrome and an always-reachable logout — plus a reusable Blue Steel **brand mark** and branded login/empty/welcome surfaces (UX Constitution §3 "top bar").

> **No SETUP required.** Uses the shadcn primitives F1.7 already installed (`card`, `button`, `form`/`input`/`label`) + `lucide-react` icons (already a dep) + `zustand/middleware` `persist`. No new component or package.

**Scope (out):** Activating the Query (Phase 3) / Exploration (Phase 4) nav links — they stay disabled stubs here; a real Settings page (stubbed). Member-management UI for F1.9 endpoints. The campaign list/context guard/home already shipped by F1.10 (F1.11 wraps + trims them).

**Skills:** `ux-navigation-logic`, `frontend-api-resource`, `react-hook-form`, `auth`, `frontend-testing`  
**Decisions:** D-051, D-087  
**Dependencies:** F1.10, F1.9.9

> **Note — role field:** the campaign role lives in `campaignStore.activeRole` (F1.7.2). The
> `ux-navigation-logic` skill's `currentUserRole` is an older name — use `activeRole` (and
> `apps/web/CLAUDE.md` §4 is corrected to match).

> **Backend contract (verified):**
> - `GET /api/v1/users?email=` → `data: { id, email }[]` (F1.9.9 `UserSearchResponse`; admin-or-GM).
> - `POST /api/v1/campaigns` body `{ name, gmUserId }` → **201** `CampaignResponse` (admin-only, F1.8.8).
> - Admin is detected from `authStore.currentUser.isAdmin` (F1.7.1 `CurrentUser`); never from a route.

---

#### F1.11.1 — uiStore (sidebar expand/collapse, persisted)

**Goal:** The client-state store for sidebar layout, persisted across reloads. Mirrors the `ux-navigation-logic` skill snippet.

**Scope (in):**
- `apps/web/src/store/uiStore.ts` (+ `uiStore.test.ts`) — Zustand + `persist` (`zustand/middleware`); `sidebarExpanded: boolean` (default `true`), `toggleSidebar()`, `setSidebarExpanded(value)`; localStorage key `blue-steel-sidebar`. Test asserts toggle + persisted key (never read `localStorage` directly in components).

**Scope (out):** The `Sidebar` that consumes it (F1.11.2). Any non-sidebar UI flags.

**Skills:** `ux-navigation-logic`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.7.2

---

#### F1.11.2 — Sidebar component (mode nav, role-gated, collapse)

**Goal:** The persistent campaign navigation per UX §6 — campaign switcher, mode nav (Input live; Query/Exploration/Settings stubbed), collapse toggle, user/logout — with `player` role gating.

**Scope (in):**
- `apps/web/src/components/domain/Sidebar.tsx` (+ `Sidebar.test.tsx`, incl. axe) — reads `useUiStore(s => s.sidebarExpanded)` + `useCampaignStore(s => s.activeRole)` + `activeCampaignId`; campaign-switcher label from the cached `useCampaign(activeCampaignId)` (F1.10.2) + a "Switch campaign" link to `/`; `lucide-react` icons; `NavLink`s — **Input Mode** → `/campaigns/{activeCampaignId}/sessions/new` (live), **Query / Exploration / Settings** → disabled "coming soon" items; **Input item hidden (not just disabled) when `activeRole === 'player'`** (D); User + Logout at the bottom (`authStore.logout()` → navigate `/login`); `w-16` collapsed / `w-64` expanded with `transition-all duration-200`; active-route accent `bg-blue-50 text-blue-600 border-r-2 border-blue-500` via `NavLink`. Collapsed → icons only (text removed, not truncated).

**Scope (out):** The `AppShell` that mounts it + route wiring (F1.11.3). The `uiStore` itself (F1.11.1). Real Query/Exploration routes (their phases).

**Skills:** `ux-navigation-logic`, `frontend-api-resource`, `auth`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.11.1, F1.7.2, F1.7.4, F1.10.2

---

#### F1.11.3 — AppShell layout + campaign route restructure

**Goal:** Mount the sidebar as a persistent shell around every campaign-scoped page by inserting an `AppShell` layout route inside the existing `CampaignContextGuard` subtree.

**Scope (in):**
- `apps/web/src/components/domain/AppShell.tsx` (+ `AppShell.test.tsx`, incl. axe) — flex layout: `<Sidebar />` + `<main className="flex-1 …"><Outlet /></main>` (page background `slate-50`).
- `apps/web/src/main.tsx` (**edit**, F1.10.6) — insert `AppShell` as a **layout route nested inside the `/campaigns/:campaignId` `CampaignContextGuard`** (guard stays the parent rendering `<Outlet/>`; `AppShell` becomes the child layout whose `<Outlet/>` holds the campaign pages). `CampaignHomePage` is the index route. All existing/future campaign children (`sessions/new` F2.9.5, `…/diff` F2.10.12) render inside the shell automatically — no change to those sub-tasks.
- `apps/web/src/features/campaigns/CampaignHomePage.tsx` (**edit**, F1.10.5) — **trim** the Input/Query/Exploration entry cards now that the sidebar owns navigation; keep the campaign-name welcome/landing content.

**Scope (out):** The `Sidebar` (F1.11.2). The list `/` and create `/campaigns/new` pages — they are **not** campaign-scoped and stay outside the shell.

**Skills:** `ux-navigation-logic`, `frontend-api-resource`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.11.2, F1.10.3, F1.10.5, F1.10.6

---

#### F1.11.4 — admin-create data layer (user search + create campaign)

**Goal:** The typed client surface the admin create flow needs: search users (for the GM picker) and create a campaign.

**Scope (in):**
- `apps/web/src/types/auth.ts` (**extend**, F1.7.1) — `UserSearchResult` (`{ id: string; email: string }`).
- `apps/web/src/api/users.ts` (**extend**, F1.7.4) + `users.test.ts` (**extend**) — `searchUsers(email): Promise<UserSearchResult[]>` → `GET /api/v1/users?email=`; hook `useUserSearch(email, enabled?)`.
- `apps/web/src/api/campaigns.ts` (**extend**, F1.10.2) + `campaigns.test.ts` (**extend**) — `createCampaign({ name, gmUserId }): Promise<CampaignResponse>` → `POST /api/v1/campaigns`; hook `useCreateCampaign()` (invalidate `campaignKeys.all` on success). Tests mock `apiClient`.

**Scope (out):** The create page/form + admin gating (F1.11.5). Member-management endpoints (F1.9 UI).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-024  **Dependencies:** F1.7.4, F1.10.2, F1.7.3

---

#### F1.11.5 — CreateCampaignPage + `/campaigns/new` route (admin)

**Goal:** The admin-only campaign-creation page, reachable from the campaign list, that creates a campaign with its GM and lands on the new campaign home.

**Scope (in):**
- `apps/web/src/features/campaigns/CreateCampaignPage.tsx` (+ `CreateCampaignPage.test.tsx`, incl. axe) — admin gate: `<Navigate to="/" replace />` when `!useAuthStore(s => s.currentUser?.isAdmin)`; React Hook Form over `{ name, gmUserId }`; GM picker = email input → `useUserSearch` results list → select sets `gmUserId`; submit via `useCreateCampaign`; `400` field errors via `setError`, other errors via error `InlineBanner`; in-button `Loader2` while pending; on success `navigate('/campaigns/{id}')`.
- `apps/web/src/main.tsx` (**edit**, F1.11.3) — add `/campaigns/new` behind `RequireAuth`, **outside** `AppShell` (not campaign-scoped).
- `apps/web/src/features/campaigns/CampaignListPage.tsx` (**edit**, F1.10.4) — an admin-only "New campaign" link to `/campaigns/new`.

**Scope (out):** The data-layer hooks (F1.11.4). The shell/sidebar (F1.11.2/F1.11.3). Editing an existing campaign (not in v1).

**Skills:** `react-hook-form`, `frontend-api-resource`, `auth`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-024, D-051, D-087  **Dependencies:** F1.11.4, F1.7.2, F1.7.6, F1.10.4

---

#### F1.11.6 — Brand component (reusable wordmark/mark)

**Goal:** A single presentational brand lockup — a `lucide-react` mark in accent `blue-500` + the "Blue Steel" wordmark — reused by the global top bar and the auth pages so the product reads as branded, not a bare form. No router/store dependency.

**Scope (in):**
- `apps/web/src/components/domain/Brand.tsx` (+ `Brand.test.tsx`, incl. axe) — mark + wordmark; optional `size` variant (`sm` for the AppBar, `lg` for auth pages). Pure presentational.

**Scope (out):** The AppBar that consumes it (F1.11.7); auth-page placement (F1.11.9). No SVG/logo asset — Lucide icon + text only.

**Skills:** `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.7-SETUP

---

#### F1.11.7 — AppBar global top bar + relocate identity/logout from Sidebar

**Goal:** The persistent top bar (UX Constitution §3 "top bar", elevation 4) on every authenticated page: `<Brand>` linking home (`/`) on the left; current-user email + **Log out** on the right. Logout (clear `authStore` → navigate `/login`) moves here from the Sidebar so it is reachable everywhere — closing the gap where the campaign list had no way to sign out.

**Scope (in):**
- `apps/web/src/components/domain/AppBar.tsx` (+ `AppBar.test.tsx`, incl. axe) — `h-14 bg-white border-b border-slate-200 shadow-sm`; left `<Link to="/"><Brand size="sm"/></Link>`; right `currentUser.email` (from `authStore`) + secondary-style Log out button (`LogOut` icon) calling `authStore.logout()` then `navigate('/login')`. Inline only — no dropdown.
- `apps/web/src/components/domain/Sidebar.tsx` (**edit**, F1.11.2) — remove the user-identity row + Log out button (now global); drop the now-unused `useNavigate`/`handleLogout`/`User`/`LogOut` imports. Keep campaign switcher, mode nav, collapse toggle.
- `apps/web/src/components/domain/Sidebar.test.tsx` (**edit**) — move/remove the "logs out and navigates to /login" assertion (now covered by `AppBar.test.tsx`).

**Scope (out):** Mounting the bar in a layout (F1.11.8). A user profile/dropdown menu (not in v1).

**Skills:** `ux-navigation-logic`, `auth`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.11.6, F1.11.2, F1.7.4

---

#### F1.11.8 — AuthenticatedLayout + authenticated route restructure

**Goal:** Mount the AppBar as a shell around **all** authenticated pages (not just campaign-scoped ones) via an `AuthenticatedLayout` layout route, so the campaign list and create pages gain the bar. The campaign Sidebar now sits below the bar.

**Scope (in):**
- `apps/web/src/components/domain/AuthenticatedLayout.tsx` (+ `AuthenticatedLayout.test.tsx`, incl. axe) — `flex min-h-screen flex-col bg-slate-50`: `<AppBar/>` over a `flex flex-1` region holding `<Outlet/>`.
- `apps/web/src/main.tsx` (**edit**, F1.11.3/F1.11.5) — wrap `/`, `/campaigns/new`, and the `/campaigns/:campaignId` subtree in one `<Route element={<RequireAuth><AuthenticatedLayout/></RequireAuth>}>` layout route. `/login`, `/status`, `/change-password` stay outside the shell.
- `apps/web/src/components/domain/AppShell.tsx` (**edit**, F1.11.3) — drop the `min-h-screen bg-slate-50` wrapper (now owned by the layout); reduce to `<div className="flex flex-1"><Sidebar/><main className="flex-1"><Outlet/></main></div>`.
- `apps/web/src/components/domain/Sidebar.tsx` (**edit**, F1.11.2) — `h-screen` → `sticky top-14 h-[calc(100vh-3.5rem)]` so it sits flush below the bar while content scrolls.

**Scope (out):** The AppBar itself (F1.11.7); branded auth surfaces (F1.11.9); page-content copy (F1.11.10).

**Skills:** `ux-navigation-logic`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.11.7, F1.11.3, F1.10.6

---

#### F1.11.9 — Branded auth surfaces (Login + ChangePassword)

**Goal:** Make the first impression branded — place the `<Brand>` lockup on the login and change-password pages (which sit outside the shell) so they share the product identity instead of a bare card.

**Scope (in):**
- `apps/web/src/features/auth/LoginPage.tsx` (**edit**, F1.7.7) — add `<Brand size="lg"/>` above the form card; form/validation/`InlineBanner` unchanged.
- `apps/web/src/features/auth/ChangePasswordPage.tsx` (**edit**, F1.7.8) — same Brand header for consistency.
- `LoginPage.test.tsx` / `ChangePasswordPage.test.tsx` (**edit**) — adjust for the new heading structure; retain existing axe assertions.

**Scope (out):** AppBar/shell (these pages stay shell-less); empty/welcome states (F1.11.10).

**Skills:** `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.11.6, F1.7.7, F1.7.8

---

#### F1.11.10 — Warmer empty + welcome surfaces

**Goal:** Replace flat empty/welcome copy with intentional empty-state and welcome treatments so the campaign list and campaign home feel designed, within constitution tokens.

**Scope (in):**
- `apps/web/src/features/campaigns/CampaignListPage.tsx` (**edit**, F1.10.4) — centered empty-state block (muted Lucide icon + `text-base font-medium` heading + supportive body); **admin** gets a prominent `New campaign` CTA inside the block, **non-admin** keeps the "ask your GM or an admin to add you" guidance. Header `New campaign` button shows only when the list is non-empty.
- `apps/web/src/features/campaigns/CampaignHomePage.tsx` (**edit**, F1.11.3) — warmer welcome (icon/heading + supportive copy pointing to the sidebar), keeping the campaign-name landing role.
- `CampaignListPage.test.tsx` / `CampaignHomePage.test.tsx` (**edit**) — update empty-state/welcome copy assertions; add the admin-empty-state CTA assertion.

**Scope (out):** Card/hover restyle, graph or deeper visual work (not in this pass).

**Skills:** `ux-skeleton-crafting`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-087  **Dependencies:** F1.11.8, F1.10.4, F1.10.5

---

### Phase 2 — Session Ingestion Pipeline

> **Principle:** Schema first, mocks second, backend pipeline third, frontend last.
> Every pipeline stage is developed TDD under the `local` profile (zero API cost) before the real adapters are wired.
>
> **Note on F2.1 placement:** The world state schema comes first in Phase 2 because entity resolution (F2.5) and conflict detection (F2.6) both require `entity_embeddings` to run integration tests. The original ordering (schema in the middle) created a false dependency.

#### Summary

| # | Feature | Status |
|---|---|---|
| F2.1 | World state + session schema migrations | ✅ |
| F2.1.1 | Session + narrative-block schema migrations (0006–0007) | ✅ |
| F2.1.2 | Actor + space versioned-table migrations (0008–0011) | ✅ |
| F2.1.3 | Event + relation versioned-table migrations (0012–0015) | ✅ |
| F2.1.4 | Entity-embeddings migration — vector(1536) + IVFFlat (0016) | ✅ |
| F2.1.5 | Annotations table migration (0017) | ✅ |
| F2.1.6 | Proposals + proposal-votes schema-only migrations (0018–0019) | ✅ |
| F2.2 | Mock LLM + Email adapters (local profile) | ✅ |
| F2.2.1 | AI value model — extraction + shared EntityContext | ✅ |
| F2.2.2 | AI value model — entity-resolution outcomes | ✅ |
| F2.2.3 | Narrative-extraction port + mock + llm-real stub | ✅ |
| F2.2.4 | Entity-resolution port + mock + llm-real stub | ✅ |
| F2.2.5 | Conflict-detection port + mock + llm-real stub | ✅ |
| F2.2.6 | Embedding port + mock + llm-real stub | ✅ |
| F2.2.7 | Query-answering port + mock + llm-real stub | ✅ |
| F2.2.8 | AiConfig + local-profile mock-adapter wiring IT | ✅ |
| F2.3 | Session submission + status machine | 🔲 |
| F2.3.1 | Session aggregate + status state machine + InvalidSessionStateTransitionException | 🔲 |
| F2.3.2 | NarrativeBlock write-once domain entity | 🔲 |
| F2.3.3 | Session driven ports + SessionSubmittedEvent + timeout-recovery port method | 🔲 |
| F2.3.4 | Session persistence adapter (JPA entity + repo + adapter) | 🔲 |
| F2.3.5 | NarrativeBlock persistence adapter (JPA entity + repo + adapter) | 🔲 |
| F2.3.6 | SubmitSessionUseCase + service (token budget, 409, role, publish event) | 🔲 |
| F2.3.7 | GetSessionStatus + DiscardSession use cases + services | 🔲 |
| F2.3.8 | SessionIngestionEventListener stub (@EventListener + @Async) | 🔲 |
| F2.3.9 | Timeout-recovery query — extend SessionRecoveryAdapter (PIPELINE_TIMEOUT) | 🔲 |
| F2.3.10 | Scheduled stuck-processing TTL checker (@Scheduled + @EnableScheduling) | 🔲 |
| F2.3.11 | SessionController + DTOs + GlobalExceptionHandler mappings | 🔲 |
| F2.4 | Knowledge extraction pipeline | 🔲 |
| F2.4.1 | LLM cost logger + token-budget utilities (shared LLM infra) | 🔲 |
| F2.4.2 | AiConfig real llm-real ChatClient bean + provider config | 🔲 |
| F2.4.3 | Real SpringAiNarrativeExtractionAdapter (replaces F2.2.3 stub) | 🔲 |
| F2.4.4 | NarrativeBlockRepository.findBySessionId (port + adapter extension) | 🔲 |
| F2.4.5 | ExtractionPipelineService (transitions + extract + MDC + fail handling) | 🔲 |
| F2.4.6 | Wire extraction into SessionIngestionEventListener | 🔲 |
| F2.5 | Entity resolution pipeline | 🔲 |
| F2.5.1 | SimilarityResult model + EntitySimilaritySearchPort | 🔲 |
| F2.5.2 | EntitySimilaritySearchAdapter (native pgvector retrieval) | 🔲 |
| F2.5.3 | Real SpringAiEntityResolutionAdapter (LLM call 2, replaces F2.2.4 stub) | 🔲 |
| F2.5.4 | EntityResolutionService (two-stage orchestration) | 🔲 |
| F2.5.5 | Wire resolution into SessionIngestionEventListener | 🔲 |
| F2.6 | Conflict detection pipeline | 🔲 |
| F2.6.1 | Real SpringAiEmbeddingAdapter (fills F2.2.6 stub; enables real embeddings) | 🔲 |
| F2.6.2 | Real SpringAiConflictDetectionAdapter (LLM call 3, replaces F2.2.5 stub) | 🔲 |
| F2.6.3 | ConflictDetectionService (MATCH-scoped pgvector context + LLM call) | 🔲 |
| F2.6.4 | Wire conflict detection into SessionIngestionEventListener | 🔲 |
| F2.7 | Diff generation + draft API | 🔲 |
| F2.7.1 | DiffCard sealed interface + EXISTING/NEW/UNCERTAIN card records | 🔲 |
| F2.7.2 | ConflictCard + DiffPayload aggregate record | 🔲 |
| F2.7.3 | DiffGenerationService (assemble + persist diff_payload + draft transition) | 🔲 |
| F2.7.4 | Wire diff generation into SessionIngestionEventListener (final stage) | 🔲 |
| F2.7.5 | GetSessionDiffUseCase + service (draft-only read) | 🔲 |
| F2.7.6 | Diff retrieval endpoint (GET .../diff) | 🔲 |
| F2.8 | Commit endpoint | 🔲 |
| F2.8.1 | Commit application model (CommitPayload + nested records, camelCase) | 🔲 |
| F2.8.2 | Session.commit(sequenceNumber) domain transition | 🔲 |
| F2.8.3 | Session sequence-number assignment query (port + adapter + IT) | 🔲 |
| F2.8.4 | World-state write contract (WorldStatePort + command/result records) | 🔲 |
| F2.8.5 | WorldStateAdapter (native-SQL head+version write, D-089) + IT | 🔲 |
| F2.8.6 | EntityEmbeddingWritePort + write adapter (native INSERT) + IT | 🔲 |
| F2.8.7 | CommitPayloadValidator (8 checks) + CommitValidationException | 🔲 |
| F2.8.8 | CommitService + CommitSessionUseCase + command + SessionCommittedEvent | 🔲 |
| F2.8.9 | EmbeddingGenerationListener (async post-commit, D-063) | 🔲 |
| F2.8.10 | Commit endpoint (controller + request DTO + 422 mapping) | 🔲 |
| F2.9 | Frontend: Input Mode — session submission + status polling | 🔲 |
| F2.9-SETUP | Frontend scaffolding — `shadcn add textarea` (human step) | 👤 |
| F2.9.1 | Frontend: session submit + status TypeScript types | 🔲 |
| F2.9.2 | Frontend: sessions API client + submit/status-polling hooks | 🔲 |
| F2.9.3 | Frontend: ProcessingStatusView (poll → skeleton → draft/failed) | 🔲 |
| F2.9.4 | Frontend: SubmitSessionPage form (role guard + error states) | 🔲 |
| F2.9.5 | Frontend: register `/sessions/new` route behind RequireAuth | 🔲 |
| F2.10 | Frontend: Input Mode — diff review screen | 🔲 |
| F2.10-SETUP | Frontend scaffolding — `shadcn add badge checkbox radio-group` (human step) | 👤 |
| F2.10.1 | Frontend: DiffPayload read TypeScript types (§7.6) | 🔲 |
| F2.10.2 | Frontend: useSessionDiff query hook | 🔲 |
| F2.10.3 | Frontend: FocusedOverlay primitive + useEscapeKey (no-modal) | 🔲 |
| F2.10.4 | Frontend: useDiffState reducer hook (decisions/resolutions/acks) | 🔲 |
| F2.10.5 | Frontend: DeltaCard + NewEntityCard (entity decision cards) | 🔲 |
| F2.10.6 | Frontend: UncertainCard (inline MATCH/NEW resolution) | 🔲 |
| F2.10.7 | Frontend: ConflictWarningCard (acknowledge, non-blocking) | 🔲 |
| F2.10.8 | Frontend: EditCardOverlay (FocusedOverlay-based field edit) | 🔲 |
| F2.10.9 | Frontend: NarrativeSummaryHeader + DiffCategorySection | 🔲 |
| F2.10.10 | Frontend: CommitButton (controlled disabled guard) | 🔲 |
| F2.10.11 | Frontend: DiffReviewPage container (fetch + skeleton + assemble) | 🔲 |
| F2.10.12 | Frontend: register `/sessions/:sessionId/diff` route | 🔲 |
| F2.11 | Frontend: Input Mode — commit flow + draft recovery | 🔲 |
| F2.11.1 | Frontend: CommitPayload wire TypeScript types (§7.6) | 🔲 |
| F2.11.2 | Frontend: buildCommitPayload pure builder (state → payload) | 🔲 |
| F2.11.3 | Frontend: commit + discard API client + mutation hooks | 🔲 |
| F2.11.4 | Frontend: DiscardConfirmOverlay (FocusedOverlay confirm) | 🔲 |
| F2.11.5 | Frontend: DiffReviewPage commit wiring (+ 422 handling) | 🔲 |
| F2.11.6 | Frontend: DiffReviewPage GM-only discard wiring | 🔲 |
| F2.12 | Local LLM via Ollama (offline real pipeline) | 🔲 |
| F2.12-SETUP | Human: add Ollama starter, install Ollama, pull models | 🔲 |
| F2.12.1 | Ollama profile config + AiConfig model-bean wiring | 🔲 |
| F2.12.2 | Offline pipeline smoke test (env-gated, manual/local) | 🔲 |

---

#### F2.1 — World state + session schema migrations

> **Umbrella task — run the F2.1.N sub-tasks below, not this.**

**Goal:** Create all Phase 2 domain tables before any pipeline code is written. Entity resolution and conflict detection integration tests require `entity_embeddings` to exist.

**Scope (out):** JPA entities, domain classes. Proposal approval logic is permanently out of scope for v1.

> **No SETUP required.** Liquibase is already wired (`db.changelog-master.xml`, changesets 0001–0005, pgvector extension in 0001) and the Testcontainers harness exists (`TestcontainersPostgresBaseIT`). Each sub-task only appends new changeset files + `<include>` lines and adds one `*SchemaIT`.

**Skills:** `database-migration`  
**Decisions:** D-016, D-031, D-054, D-062, D-069

---

#### F2.1.1 — Session + narrative-block schema

**Goal:** Create the `sessions` and `narrative_blocks` tables per ARCHITECTURE.md §5.3, including the single-active-session partial index (D-054) and the nullable `sequence_number` with its committed-only uniqueness (D-069).

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0006_create_sessions.xml` — `sessions`: UUID PK; `campaign_id` FK→campaigns, `owner_id` FK→users; `sequence_number INTEGER` nullable; `status TEXT` + raw-SQL `CHECK (status IN ('pending','processing','draft','committed','failed','discarded'))`; `diff_payload JSONB` nullable; `failure_reason TEXT` nullable; `committed_at` nullable; `created_at`, `updated_at`; `UNIQUE (campaign_id, sequence_number)`; raw-SQL partial unique index `sessions_one_active_per_campaign ON sessions (campaign_id) WHERE status IN ('processing','draft')` (D-054); `<rollback>` for the raw-SQL items.
- `apps/api/src/main/resources/db/changelog/0007_create_narrative_blocks.xml` — `narrative_blocks`: UUID PK; `session_id` FK→sessions; `raw_summary_text TEXT`; `token_count INTEGER`; `created_at`.
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml` — append the two `<include>` lines (in order).
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/SessionSchemaIT.java` — extends `TestcontainersPostgresBaseIT`; asserts both tables exist, the status CHECK rejects a bad value, the partial index blocks a 2nd `processing|draft` session for one campaign but allows it once the first is `committed`, and `sequence_number` nulls are not treated as duplicates.

**Scope (out):** All versioned world-state tables (F2.1.2/3); embeddings (F2.1.4); JPA entities and the `Session` domain aggregate (F2.3).

**Skills:** `database-migration`  **Decisions:** D-031, D-054, D-069  **Dependencies:** F1.9

---

#### F2.1.2 — Actor + space versioned tables

**Goal:** Create the head + append-only version tables for actors and spaces using the D-035 two-table versioning pattern (ARCHITECTURE.md §5.4).

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0008_create_actors.xml` — `actors`: UUID PK; `campaign_id` FK, `owner_id` FK (D-021); `name TEXT NOT NULL`; `created_at`; `created_in_session_id` FK→sessions.
- `apps/api/src/main/resources/db/changelog/0009_create_actor_versions.xml` — `actor_versions`: UUID PK; `actor_id` FK→actors; `session_id` FK→sessions; `version_number INTEGER`; `changed_fields JSONB`; `full_snapshot JSONB`; `created_at`; index on `actor_id`.
- `apps/api/src/main/resources/db/changelog/0010_create_spaces.xml` — `spaces` (mirror of `actors`).
- `apps/api/src/main/resources/db/changelog/0011_create_space_versions.xml` — `space_versions` (mirror of `actor_versions`, FK→spaces).
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml` — append the four `<include>` lines.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/WorldStateActorSpaceSchemaIT.java` — asserts all four tables + the version FKs to `actors`/`spaces` and `sessions` exist, and that `campaign_id`/`owner_id` columns are present (D-021).

**Scope (out):** Event/relation tables (F2.1.3); current-state/point-in-time read logic and JPA mappers (later phases).

**Skills:** `database-migration`, `backend-domain-model`  **Decisions:** D-001, D-021, D-035  **Dependencies:** F2.1.1

---

#### F2.1.3 — Event + relation versioned tables

**Goal:** Create the head + version tables for events and relations, completing the four versioned world-state entities (ARCHITECTURE.md §5.4).

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0012_create_events.xml` — `events` (versioning head: `campaign_id`, `owner_id`, `name`, `created_at`, `created_in_session_id` FK→sessions).
- `apps/api/src/main/resources/db/changelog/0013_create_event_versions.xml` — `event_versions` (FK→events + sessions; `version_number`, `changed_fields JSONB`, `full_snapshot JSONB`).
- `apps/api/src/main/resources/db/changelog/0014_create_relations.xml` — `relations` (head).
- `apps/api/src/main/resources/db/changelog/0015_create_relation_versions.xml` — `relation_versions`.
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml` — append the four `<include>` lines.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/WorldStateEventRelationSchemaIT.java` — asserts all four tables + their FKs and the versioning columns exist.

**Scope (out):** entity_embeddings (F2.1.4); JPA mappers and read logic.

**Skills:** `database-migration`, `backend-domain-model`  **Decisions:** D-001, D-021, D-035  **Dependencies:** F2.1.2

---

#### F2.1.4 — Entity-embeddings migration (pgvector + IVFFlat)

**Goal:** Create `entity_embeddings` with a **configurable-dimension** `vector` column, the IVFFlat cosine index, and the `entity_type` CHECK constraint (ARCHITECTURE.md §5.5, D-062, D-088). The pgvector extension already exists (changeset 0001) — do NOT re-create it.

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0016_create_entity_embeddings.xml` — UUID PK; `entity_type TEXT` + raw-SQL `CHECK (entity_type IN ('actor','space','event','relation'))`; `entity_id UUID` (polymorphic — no FK); `entity_version_id UUID`; `session_id` FK→sessions; `embedding vector(${embeddingDimension})` NOT NULL (Liquibase parameter — 1536 for Gemini `gemini-embedding-001` with `outputDimensionality=1536` by default, 1024 under the `llm-ollama` profile; D-093, D-088); `content_hash TEXT`; `created_at`; raw-SQL index `USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)`; `<rollback>` for the raw-SQL items.
- `apps/api/src/main/resources/application.properties` — add `spring.liquibase.parameters.embeddingDimension=${EMBEDDING_DIMENSION:1536}` (base default; per-profile override lives in F2.12).
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml` — append the `<include>` line.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/EntityEmbeddingSchemaIT.java` — asserts the table exists, the `embedding` column type is `vector` (do NOT hard-assert the dimension — it is parameterized), the IVFFlat index exists (query `pg_indexes`/`pg_class` for `ivfflat`), and the `entity_type` CHECK rejects a bad value.

**Scope (out):** Embedding generation / similarity queries (Phase 3 query pipeline). The `llm-ollama` profile override (dimension = 1024) is F2.12.

**Skills:** `database-migration`  **Decisions:** D-031, D-040, D-062, D-088  **Dependencies:** F2.1.3

---

#### F2.1.5 — Annotations table

**Goal:** Create the immutable `annotations` table per ARCHITECTURE.md §5.6 (no `updated_at`).

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0017_create_annotations.xml` — UUID PK; `campaign_id` FK→campaigns; `entity_type TEXT` + raw-SQL `CHECK (entity_type IN ('actor','space','relation','event'))`; `entity_id UUID` (polymorphic — no FK); `author_id` FK→users; `content TEXT`; `created_at` (deliberately no `updated_at` — annotations are immutable).
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml` — append the `<include>` line.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/AnnotationSchemaIT.java` — asserts the table exists, has no `updated_at` column, and the `entity_type` CHECK rejects a bad value.

**Scope (out):** Annotation REST API / use cases (later phases).

**Skills:** `database-migration`  **Decisions:** D-021  **Dependencies:** F2.1.4

---

#### F2.1.6 — Proposals + proposal-votes (schema-only)

**Goal:** Create the `proposals` and `proposal_votes` tables from day one per ARCHITECTURE.md §5.8 (D-016). Schema only — no application code touches these in v1.

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0018_create_proposals.xml` — UUID PK; `campaign_id` FK→campaigns; `target_entity_type TEXT` + CHECK; `target_entity_id UUID` (polymorphic); `author_id` FK→users; `proposed_delta JSONB`; `status TEXT` + raw-SQL `CHECK (status IN ('open','cosigned','approved','rejected','expired'))`; `created_at`; `expires_at` nullable (TTL enforced in v2, D-019).
- `apps/api/src/main/resources/db/changelog/0019_create_proposal_votes.xml` — UUID PK; `proposal_id` FK→proposals; `voter_id` FK→users; `vote TEXT` + CHECK `IN ('cosign','approve','reject')`; `created_at`; `UNIQUE (proposal_id, voter_id)`.
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml` — append the two `<include>` lines.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/ProposalSchemaIT.java` — asserts both tables exist, the FKs resolve, and the `UNIQUE (proposal_id, voter_id)` constraint is enforced (second identical vote throws `DataIntegrityViolationException`).

**Scope (out):** Proposal approval pipeline — permanently out of scope for v1 (D-016).

**Skills:** `database-migration`  **Decisions:** D-016  **Dependencies:** F2.1.5

---

#### F2.2 — Mock LLM + Email adapters (local profile)

> **Umbrella task — run the F2.2.N sub-tasks below, not this.**

**Goal:** All AI-driven port adapters return deterministic canned responses under the `local` profile (zero API cost), unblocking TDD on the full ingestion pipeline (F2.3–F2.8).

**Scope (out):** Real Spring AI adapters (F2.4–F2.6). `EmailPort` + mock already done in F1.5.

> **No SETUP required.** Spring AI is already in `apps/api/pom.xml` (spring-ai-bom + anthropic + openai starters, D-032) and the `local` profile already exists. Mocks return canned data and stubs only throw, so no `ChatClient`/`EmbeddingModel` imports are needed until F2.4.
>
> **Port-signature note:** ARCHITECTURE §6.2 types `NarrativeExtractionPort.extract(NarrativeBlock)`, but the `NarrativeBlock` domain class is created in F2.3. Because F2.2 precedes it, the port takes `String rawSummaryText`; F2.4 passes `narrativeBlock.rawSummaryText()` when wiring the real pipeline.
>
> **Profile convention (D-088) — applies to every sub-task below:** there are three provider selections (mock / `llm-real` / `llm-ollama`). Mock adapters are `@Profile("!llm-real & !llm-ollama")` (active by default in dev; off whenever a real provider is selected). Real Spring AI adapters are provider-neutral (`SpringAi*` names) on `@Profile("llm-real | llm-ollama")` — the active `ChatModel`/`EmbeddingModel` bean is chosen per profile in `AiConfig` (F2.12 adds the Ollama beans). The F2.2 stub real adapters use these neutral names and profiles and simply throw until F2.4–F2.6 implement them.

**Skills:** `session-ingestion-pipeline`, `spring-ai-llm-adapter`  **Decisions:** D-032, D-049, D-088

---

#### F2.2.1 — AI value model: extraction + shared EntityContext

**Goal:** Define the shared, immutable value records the AI ports exchange, per ARCHITECTURE.md §6.2. These are plain records in `application.model` (ArchUnit Rule 7 keeps them out of port packages).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/ExtractedMention.java` — `record ExtractedMention(String name, String description, String rawText)`.
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/ExtractionResult.java` — `record ExtractionResult(String narrativeSummaryHeader, List<ExtractedMention> actors, List<ExtractedMention> spaces, List<ExtractedMention> events, List<ExtractedMention> relations)`.
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/EntityContext.java` — `record EntityContext(UUID entityId, String entityType, String name, String stateSnapshot, UUID sessionId, int versionNumber)`.
- `apps/api/src/test/java/com/bluesteel/application/model/ingestion/ExtractionResultTest.java` — unit test asserting record construction/accessors and (if compact constructors validate) that null lists/blank header are rejected.

**Scope (out):** Resolution/conflict/query records (F2.2.2/5/7); any port or adapter.

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-032  **Dependencies:** F2.1

---

#### F2.2.2 — AI value model: entity-resolution outcomes

**Goal:** Define the resolution-outcome value types consumed by `EntityResolutionPort`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/ResolutionOutcome.java` — `enum ResolutionOutcome { MATCH, NEW, UNCERTAIN }`.
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/ResolvedEntity.java` — `record ResolvedEntity(ExtractedMention mention, ResolutionOutcome outcome, UUID matchedEntityId)` (`matchedEntityId` nullable; non-null only for `MATCH`).
- `apps/api/src/test/java/com/bluesteel/application/model/ingestion/ResolvedEntityTest.java` — asserts construction and the MATCH/NEW/UNCERTAIN invariant if enforced in a compact constructor.

**Scope (out):** The resolution port + adapters (F2.2.4).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-049  **Dependencies:** F2.2.1

---

#### F2.2.3 — Narrative-extraction port + mock + stub

**Goal:** Define `NarrativeExtractionPort` and its `local` mock + `llm-real` stub. The mock returns a canned `ExtractionResult` (1 MATCH-candidate actor, 1 new actor, 1 space, 1 event, 1 relation, plus a narrative summary header).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/ingestion/NarrativeExtractionPort.java` — `ExtractionResult extract(String rawSummaryText)` (see umbrella port-signature note).
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/MockNarrativeExtractionAdapter.java` — `@Component @Profile("!llm-real & !llm-ollama")`; returns the canned result.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiNarrativeExtractionAdapter.java` — `@Component @Profile("llm-real | llm-ollama")`; stub that throws `UnsupportedOperationException("Real LLM adapter not implemented until F2.4")` (F2.4 fills in the real `ChatClient` logic).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/MockNarrativeExtractionAdapterTest.java` — asserts the canned counts (actors/spaces/events/relations) and non-blank header.

**Scope (out):** Real Gemini ChatClient logic (F2.4); AiConfig wiring (F2.2.8).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-032, D-049  **Dependencies:** F2.2.1

---

#### F2.2.4 — Entity-resolution port + mock + stub

**Goal:** Define `EntityResolutionPort` and its mock + stub. Mock outcomes are deterministic by mention name: "Mira" → MATCH, "Thornwick" → NEW, "Stranger" → UNCERTAIN; all others → NEW.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/ingestion/EntityResolutionPort.java` — `List<ResolvedEntity> resolve(List<ExtractedMention> mentions, List<EntityContext> candidateContext)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/MockEntityResolutionAdapter.java` — `@Component @Profile("!llm-real & !llm-ollama")`; name-based deterministic outcomes.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiEntityResolutionAdapter.java` — `@Component @Profile("llm-real | llm-ollama")`; stub that throws until F2.5.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/MockEntityResolutionAdapterTest.java` — asserts the three name → outcome mappings and the default.

**Scope (out):** Stage-1 pgvector similarity search + real LLM resolution (F2.5).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-049  **Dependencies:** F2.2.1, F2.2.2

---

#### F2.2.5 — Conflict-detection port + mock + stub

**Goal:** Define the `ConflictWarning` record, `ConflictDetectionPort`, and its mock + stub. Mock returns one `ConflictWarning` on the first call and an empty list thereafter (stateful counter).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/ConflictWarning.java` — `record ConflictWarning(String entityName, String description)` (fields per ARCHITECTURE §6.3 diff warning card; keep minimal).
- `apps/api/src/main/java/com/bluesteel/application/port/out/ingestion/ConflictDetectionPort.java` — `List<ConflictWarning> detect(ExtractionResult extraction, List<EntityContext> relevantContext)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/MockConflictDetectionAdapter.java` — `@Component @Profile("!llm-real & !llm-ollama")`; first-call-only warning.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiConflictDetectionAdapter.java` — `@Component @Profile("llm-real | llm-ollama")`; stub that throws until F2.6.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/MockConflictDetectionAdapterTest.java` — asserts one warning on first call, empty on second.

**Scope (out):** Real conflict LLM call + pgvector retrieval (F2.6).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-049  **Dependencies:** F2.2.1

---

#### F2.2.6 — Embedding port + mock + stub

**Goal:** Define `EmbeddingPort` and its mock + stub. Mock returns a deterministic `float[1536]` (all zeros except index 0 = 1.0f).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/embedding/EmbeddingPort.java` — `float[] embed(String content)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/MockEmbeddingAdapter.java` — `@Component @Profile("!llm-real & !llm-ollama")`; deterministic vector.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiEmbeddingAdapter.java` — `@Component @Profile("llm-real | llm-ollama")`; stub that throws until F2.6 (real impl injects Spring AI `EmbeddingModel` — Gemini or Ollama per profile).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/MockEmbeddingAdapterTest.java` — asserts `length == 1536`, index 0 == 1.0f, rest 0.0f.

**Scope (out):** Real Gemini `EmbeddingModel` call + async post-commit generation (F2.6/D-063).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-040, D-049  **Dependencies:** F2.1

---

#### F2.2.7 — Query-answering port + mock + stub

**Goal:** Define the `QueryResponse` + `Citation` records, `QueryAnsweringPort`, and its mock + stub. Mock returns a canned answer plus one citation to `sequenceNumber = 1`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/query/Citation.java` — `record Citation(UUID sessionId, int sequenceNumber, String snippet)` (grounding per D-003).
- `apps/api/src/main/java/com/bluesteel/application/model/query/QueryResponse.java` — `record QueryResponse(String answer, List<Citation> citations)`.
- `apps/api/src/main/java/com/bluesteel/application/port/out/query/QueryAnsweringPort.java` — `QueryResponse answer(String question, List<EntityContext> relevantContext)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/MockQueryAnsweringAdapter.java` — `@Component @Profile("!llm-real & !llm-ollama")`; canned answer + one citation.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiQueryAnsweringAdapter.java` — `@Component @Profile("llm-real | llm-ollama")`; stub that throws until the Query Mode pipeline (Phase 3).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/MockQueryAnsweringAdapterTest.java` — asserts non-blank answer and exactly one citation with `sequenceNumber == 1`.

**Scope (out):** Real query pipeline (Phase 3 / `query-pipeline` skill).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-049  **Dependencies:** F2.2.1

---

#### F2.2.8 — AiConfig + local-profile wiring IT

**Goal:** Add the co-located `AiConfig` (home for future real-adapter bean wiring) and a Testcontainers IT proving the `local` Spring context starts with all five mock adapters wired.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/AiConfig.java` — `@Configuration` in `adapters.out.ai` (ArchUnit Rule 4). Documents the three-way profile split — mock (`!llm-real & !llm-ollama`) / `llm-real` (Gemini) / `llm-ollama` (Ollama, F2.12) — and is the home for profile-selected `ChatClient`/`EmbeddingModel` beans; no real beans yet (deferred to F2.4/F2.12 to avoid requiring API keys at local startup).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/AiAdapterWiringIT.java` — extends `TestcontainersPostgresBaseIT` (already `@ActiveProfiles("local")`, i.e. no real provider profile); `@Autowired` the five ports and assert each resolves to its `Mock*` implementation.

**Scope (out):** Real bean definitions and ChatClient/EmbeddingModel wiring (F2.4–F2.6; Ollama beans F2.12).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-049, D-088  **Dependencies:** F2.2.3, F2.2.4, F2.2.5, F2.2.6, F2.2.7

---

#### F2.3 — Session submission + status machine

> **Umbrella task — run the F2.3.N sub-tasks below, not this.**
>
> **No SETUP required** — Spring async (`@EnableAsync` on `config/ApplicationConfig`) and the `local`
> profile already exist; the `sessions`/`narrative_blocks` schema (F2.1.1) and `CampaignMembershipPort`
> (F1.8.7) are produced by their cited dependencies; no new dependency or tooling is introduced. The
> startup half of D-074 already exists (`SessionRecoveryPort.recoverStuckSessions` →
> `PIPELINE_INTERRUPTED`, wired in `AdminBootstrapService`) — F2.3 adds only the scheduled TTL half.

**Goal:** Intake API for new sessions. Narrative block stored immutably. Async pipeline triggered. Status polling exposed. Stuck-processing TTL check wired.

**Scope (out):** The real pipeline logic (F2.4+). The `SessionIngestionEventListener` stub is replaced incrementally in F2.4–F2.7.

**Skills:** `session-ingestion-pipeline`, `backend-domain-model`  
**Decisions:** D-002, D-054, D-069, D-074  
**Dependencies:** F2.2

---

#### F2.3.1 — Session domain aggregate + status state machine

**Goal:** Pure-Java `Session` aggregate with a guarded status state machine (`pending → processing → draft → committed | failed | discarded`); invalid transitions throw a domain exception. No persistence, no Spring (ARCH-01).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/session/SessionStatus.java` — enum `PENDING, PROCESSING, DRAFT, COMMITTED, FAILED, DISCARDED`.
- `apps/api/src/main/java/com/bluesteel/domain/session/Session.java` — fields `id, campaignId, ownerId, status, sequenceNumber (nullable), failureReason (nullable), diffPayload (nullable), committedAt (nullable), createdAt, updatedAt`; static `create(...)` starting in `PENDING`; transition methods with guard clauses — `startProcessing()` (pending→processing), `toDraft()` (processing→draft), `markFailed(String reason)` (pending|processing→failed), `discard()` (draft→discarded, clears `diffPayload`), `commit()` (draft→committed); each invalid transition throws `InvalidSessionStateTransitionException`.
- `apps/api/src/main/java/com/bluesteel/domain/exception/InvalidSessionStateTransitionException.java` — extends `DomainException`.
- `apps/api/src/test/java/com/bluesteel/domain/session/SessionTest.java` — asserts each legal transition and that every illegal transition throws.

**Scope (out):** `NarrativeBlock` (F2.3.2); persistence (F2.3.4); `sequence_number` assignment at commit (F2.8, D-069 — left nullable here).

**Skills:** `backend-domain-model`  **Decisions:** D-054, D-069, D-074  **Dependencies:** F2.2

---

#### F2.3.2 — NarrativeBlock domain entity

**Goal:** Pure-Java write-once `NarrativeBlock` holding the raw submitted summary and its token count. Mirrors `User`'s non-blank invariant style; no mutators (write-once).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/session/NarrativeBlock.java` — fields `id, sessionId, rawSummaryText, tokenCount, createdAt`; static `create(...)`; rejects blank `rawSummaryText` and negative `tokenCount`; no setters / no copy-with methods (write-once invariant).
- `apps/api/src/test/java/com/bluesteel/domain/session/NarrativeBlockTest.java`.

**Scope (out):** Token estimation logic (F2.3.6); persistence (F2.3.5).

**Skills:** `backend-domain-model`  **Decisions:** D-054  **Dependencies:** F2.2

---

#### F2.3.3 — Session driven ports + submitted event + timeout-recovery contract

**Goal:** Declare the application contract surface the session use-cases and adapters depend on. Compile-only — interfaces and a record; no behaviour, no test (ArchUnit covers placement).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRepository.java` — `void save(Session)`, `Optional<Session> findById(UUID)`, `Optional<Session> findActiveByCampaignId(UUID)` (returns the session in `processing|draft` if any — backs the D-054 409 check).
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/NarrativeBlockRepository.java` — `void save(NarrativeBlock)`.
- `apps/api/src/main/java/com/bluesteel/application/event/SessionSubmittedEvent.java` — record `(UUID sessionId, UUID campaignId)`.
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRecoveryPort.java` (**edit** — exists for the startup path) — add `int recoverTimedOutSessions(int timeoutMinutes)`; Javadoc contrasts it (`PIPELINE_TIMEOUT`, scheduled) with the existing `recoverStuckSessions` (`PIPELINE_INTERRUPTED`, startup).

**Scope (out):** Implementations (F2.3.4/F2.3.5/F2.3.9); driving ports + commands/results (declared with their services, F2.3.6/F2.3.7).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-054, D-074  **Dependencies:** F2.3.1, F2.3.2

---

#### F2.3.4 — Session persistence adapter

**Goal:** JPA-backed `SessionRepository` over the existing `sessions` table (F2.1.1). Translates the single-active-session partial-unique-index violation (D-054) so the service can return 409 even under a TOCTOU race.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionJpaEntity.java` — `@Entity @Table(name="sessions")`; `status` persisted as the lowercase text the CHECK constraint expects; `sequenceNumber`, `failureReason`, `diffPayload`, `committedAt` nullable.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionJpaRepository.java` — `extends JpaRepository<SessionJpaEntity, UUID>`; derived/`@Query` method for the active (`processing`,`draft`) session of a campaign.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java` — `@Component`, `@Lazy` repo (mirrors `UserPersistenceAdapter`); `toDomain`/`toEntity`; `findActiveByCampaignId`; on `save`, lets `DataIntegrityViolationException` from the partial unique index propagate (service maps to 409).
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapterIT.java` — extends `TestcontainersPostgresBaseIT`; round-trips a session and asserts `findActiveByCampaignId` and the active-session uniqueness behaviour.

**Scope (out):** NarrativeBlock persistence (F2.3.5); the timeout-recovery query (F2.3.9, edits `SessionRecoveryAdapter`).

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-054, D-069  **Dependencies:** F2.1.1, F2.3.1, F2.3.3

---

#### F2.3.5 — NarrativeBlock persistence adapter

**Goal:** JPA-backed `NarrativeBlockRepository` over the existing `narrative_blocks` table (F2.1.1).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockJpaEntity.java` — `@Entity @Table(name="narrative_blocks")`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockJpaRepository.java` — `extends JpaRepository<NarrativeBlockJpaEntity, UUID>`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockPersistenceAdapter.java` — `@Component` implementing `NarrativeBlockRepository`; `toEntity` mapping.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockPersistenceAdapterIT.java` — extends `TestcontainersPostgresBaseIT`; persists a block against a saved session (FK).

**Scope (out):** Session persistence (F2.3.4).

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-054  **Dependencies:** F2.1.1, F2.3.2, F2.3.3

---

#### F2.3.6 — Submit session use case

**Goal:** `POST` intake logic: authorize `gm|editor`, enforce token budget and the single-active-session rule, persist the narrative block + a `PENDING` session in one transaction, and publish `SessionSubmittedEvent`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/session/SubmitSessionUseCase.java` — `SubmitSessionResult submit(SubmitSessionCommand)`.
- `apps/api/src/main/java/com/bluesteel/application/model/session/SubmitSessionCommand.java` (`UUID callerId, UUID campaignId, String summaryText`) + `SubmitSessionResult.java` (`UUID sessionId, SessionStatus status`).
- `apps/api/src/main/java/com/bluesteel/application/service/session/SubmitSessionService.java` — resolve role via `CampaignMembershipPort.resolveRole` (F1.8.7); non-`gm|editor` → `UnauthorizedException` (403); estimate token count (simple heuristic — a real tokenizer is out of scope under mock profiles); `> blue-steel.ingestion.max-tokens` → `SummaryTooLargeException` (400); `findActiveByCampaignId` present (or a caught unique-index violation from F2.3.4) → `ActiveSessionExistsException` carrying `existingSessionId` (409); `@Transactional` save `NarrativeBlock` + `Session.create(...PENDING)`; publish `SessionSubmittedEvent`; INFO on entry/exit (LOG-02).
- `apps/api/src/main/java/com/bluesteel/domain/exception/SummaryTooLargeException.java` + `apps/api/src/main/java/com/bluesteel/domain/exception/ActiveSessionExistsException.java` (extend `DomainException`; the latter exposes `existingSessionId()`).
- `apps/api/src/main/resources/application.properties` (**edit**) + `.env.example` (**edit**) — add `blue-steel.ingestion.max-tokens=${BLUE_STEEL_INGESTION_MAX_TOKENS:8000}`.
- `apps/api/src/test/java/com/bluesteel/application/session/SubmitSessionServiceTest.java` — mocked ports; covers role denial, oversize, active-draft 409, and the happy path (event published, both saves).

**Scope (out):** Status/discard use cases (F2.3.7); the async pipeline (F2.3.8); HTTP/controller + exception→envelope mapping (F2.3.11).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-002, D-054  **Dependencies:** F2.3.3, F2.3.4, F2.3.5, F1.8.7

---

#### F2.3.7 — Session status + discard use cases

**Goal:** Read the status of a session (any campaign member) and discard a draft (GM-only). Both are thin single-session application services over `SessionRepository`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/session/GetSessionStatusUseCase.java` + `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionStatusService.java` — load session or `SessionNotFoundException` (404); require the caller resolves to any role for the campaign via `CampaignMembershipPort` (else `UnauthorizedException`); return `SessionStatusView`.
- `apps/api/src/main/java/com/bluesteel/application/port/in/session/DiscardSessionUseCase.java` + `apps/api/src/main/java/com/bluesteel/application/service/session/DiscardSessionService.java` — GM-only via `CampaignMembershipPort`; load session or 404; call `session.discard()` (non-`draft` → `InvalidSessionStateTransitionException`, mapped to 409 in F2.3.11); save.
- `apps/api/src/main/java/com/bluesteel/application/model/session/SessionStatusView.java` (`UUID sessionId, SessionStatus status, String failureReason (nullable), String message (nullable)`).
- `apps/api/src/main/java/com/bluesteel/domain/exception/SessionNotFoundException.java` (404).
- `apps/api/src/test/java/com/bluesteel/application/session/GetSessionStatusServiceTest.java` + `DiscardSessionServiceTest.java` — mocked ports; cover authz, not-found, and (discard) the non-draft rejection.

**Scope (out):** The diff endpoint (F2.7); controller wiring (F2.3.11).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-054  **Dependencies:** F2.3.3, F2.3.4, F1.8.7

---

#### F2.3.8 — Session ingestion event listener (stub)

**Goal:** Async listener that drives a submitted session through `processing` and immediately to `failed('PIPELINE_NOT_IMPLEMENTED')`, so the full submit → poll → failure path is testable before any real pipeline stage exists. Replaced incrementally in F2.4–F2.7.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java` — `@EventListener` + `@Async` on `SessionSubmittedEvent`; load session via `SessionRepository`, `startProcessing()`, save, then `markFailed("PIPELINE_NOT_IMPLEMENTED")`, save; WARN log per failed session with `session_id` (LOG-02).
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java` — mocked `SessionRepository`; asserts the session ends `FAILED` with the stub reason.

**Scope (out):** Real extraction/resolution/conflict/diff stages (F2.4–F2.7); `@EnableAsync` (already present on `ApplicationConfig`).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-074  **Dependencies:** F2.3.1, F2.3.3, F2.3.4

---

#### F2.3.9 — Timeout-recovery persistence query

**Goal:** Implement the scheduled-TTL half of D-074 at the persistence layer by extending the existing `SessionRecoveryAdapter` with `recoverTimedOutSessions`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapter.java` (**edit**) — implement `recoverTimedOutSessions(int timeoutMinutes)`: `UPDATE sessions SET status='failed', failure_reason='PIPELINE_TIMEOUT', updated_at=now() WHERE status='processing' AND updated_at < now() - make_interval(mins => ?)`; reuse the existing `@Transactional(REQUIRES_NEW)` + `BadSqlGrammarException` "table not present" guard pattern.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapterIT.java` — extends `TestcontainersPostgresBaseIT`; asserts an old `processing` session is failed with `PIPELINE_TIMEOUT` while a recent one is untouched. (New IT, or extend an existing recovery IT if present.)

**Scope (out):** The `@Scheduled` trigger + `@EnableScheduling` (F2.3.10); the startup `recoverStuckSessions` path (already implemented).

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-074  **Dependencies:** F2.3.3

---

#### F2.3.10 — Scheduled stuck-processing TTL checker

**Goal:** Periodically fail sessions stuck in `processing` past the timeout (D-074, second mechanism). Activates scheduling app-wide.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionTimeoutRecoveryScheduler.java` — `@Scheduled(fixedDelayString="${blue-steel.ingestion.processing-timeout-check-interval-ms:300000}")`; reads `blue-steel.ingestion.processing-timeout-minutes` (`@Value`, default 10 — already in `application.properties`); calls `SessionRecoveryPort.recoverTimedOutSessions(timeoutMinutes)`; WARN log when rows > 0.
- `apps/api/src/main/java/com/bluesteel/config/ApplicationConfig.java` (**edit**) — add `@EnableScheduling` alongside the existing `@EnableAsync`.
- `apps/api/src/main/resources/application.properties` (**edit**) + `.env.example` (**edit**) — add `blue-steel.ingestion.processing-timeout-check-interval-ms=${BLUE_STEEL_INGESTION_PROCESSING_TIMEOUT_CHECK_INTERVAL_MS:300000}`.
- `apps/api/src/test/java/com/bluesteel/application/session/SessionTimeoutRecoverySchedulerTest.java` — mocked `SessionRecoveryPort`; asserts it delegates with the configured timeout.

**Scope (out):** The recovery SQL itself (F2.3.9); startup recovery (already wired in `AdminBootstrapService`).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-074  **Dependencies:** F2.3.3, F2.3.9

---

#### F2.3.11 — Session REST controller + DTOs + exception mappings

**Goal:** Expose the three session endpoints and map the new session exceptions to the standard error envelope. Caller identity from the JWT principal; campaign role enforced inside the services (not `@PreAuthorize`, since campaign role is not in the JWT — AUTH-01).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java` — `@RequestMapping("/api/v1/campaigns/{id}/sessions")`; `POST` (`@Valid` body, 202 `{ sessionId, status }`), `GET /{sid}/status` (`{ sessionId, status, failureReason?, message? }`), `DELETE /{sid}` (discard draft, 200 — soft-delete per ARCHITECTURE §7.5 / D-054); caller via `UUID.fromString(SecurityContextHolder...getName())` (mirrors `InvitationController`); responses wrapped in `ApiResponse`.
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SubmitSessionRequest.java` (`@NotBlank summaryText`), `SessionAcceptedResponse.java` (`sessionId, status`), `SessionStatusResponse.java` (`sessionId, status, failureReason, message`).
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/GlobalExceptionHandler.java` (**edit**) — map `SummaryTooLargeException` → 400 `SUMMARY_TOO_LARGE`; `ActiveSessionExistsException` → 409 `ACTIVE_SESSION_EXISTS` (message/payload includes `existingSessionId`); `InvalidSessionStateTransitionException` → 409 `INVALID_SESSION_STATE` (dedicated handler — overrides the generic 422 `DomainException` mapping); `SessionNotFoundException` → 404 `SESSION_NOT_FOUND`.
- `apps/api/src/test/java/com/bluesteel/adapters/in/web/session/SessionControllerTest.java` — `@WebMvcTest` with mocked use-case ports; asserts 202/200 happy paths and the 400/409/404 envelope mappings.

**Scope (out):** Diff retrieval (F2.7) and commit (F2.8) endpoints; frontend (F2.9–F2.11).

**Skills:** `session-ingestion-pipeline`, `backend-endpoint`  **Decisions:** D-054, D-074  **Dependencies:** F2.3.6, F2.3.7

---

#### F2.4 — Knowledge extraction pipeline

> **Umbrella task — run the F2.4.N sub-tasks below, not this.**
>
> **No SETUP required** — the spring-ai-bom + Google GenAI starter are already in `pom.xml`
> (D-032) and the `llm-real` profile config is a `.properties` file authored by sub-task F2.4.2;
> no new dependency or tooling is introduced. The mock adapter, ports, value models (F2.2),
> and the session aggregate/listener (F2.3) are produced by the cited dependencies.

**Goal:** LLM Call 1. Extract actors, spaces, events, and relations from the session summary. Generate the narrative summary header as a co-output (D-005). Wire into the `SessionSubmittedEvent` async listener.

**Scope (out):** Entity resolution (F2.5). The listener exits after extraction in this feature.

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-005, D-032, D-034, D-049, D-072, D-088  
**Dependencies:** F2.3

---

#### F2.4.1 — LLM cost logger + token-budget utilities

**Goal:** Shared LLM-call instrumentation reused by every real Spring AI adapter (F2.4–F2.6): structured cost logging and the pre-call input-token budget guard (D-034, LOG-01, D-072).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/config/LlmCostLogger.java` — `@Component`; `logLlmCall(String stage, int tokensIn, int tokensOut, java.time.Instant start)`; emits one INFO line via `log.atInfo().addKeyValue("stage" / "tokens_in" / "tokens_out" / "cost_usd" / "duration_ms")`; `session_id`/`user_id` come from MDC (set by the caller, F2.4.5); private `estimateCostUsd` per Gemini pricing; never logs raw response content.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/TokenEstimator.java` — `public static int estimate(String text)` (≈ `Math.ceil(len / 4.0)`); input-budget only.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/TokenBudgetExceededException.java` — `RuntimeException(int estimated, int max)`; thrown before any `ChatClient` call.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/TokenEstimatorTest.java` — empty/short/long boundary cases.

**Scope (out):** The adapter that uses these (F2.4.3); MDC population (F2.4.5); JSON appender config (already in `logback-spring.xml`, D-072).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-034, D-072  **Dependencies:** F2.2

---

#### F2.4.2 — AiConfig real ChatClient (llm-real) + provider config

**Goal:** Provide the `llm-real` `ChatClient` bean (Gemini) the extraction adapter injects, plus the provider properties and the extraction token-budget envelope. The `llm-ollama` `ChatClient` bean is F2.12.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/AiConfig.java` (**edit**, F2.2.8) — add `@Bean @Profile("llm-real")` `ChatClient` built from the auto-configured Gemini `ChatModel` (`ChatClient.create(chatModel)`); Javadoc notes the adapter is `@Profile("llm-real | llm-ollama")` so exactly one `ChatClient` bean must be active per profile (Ollama's bean is F2.12).
- `apps/api/src/main/resources/application-llm-real.properties` (**new**) — `spring.ai.google.genai.api-key=${GEMINI_API_KEY}`, `spring.ai.google.genai.chat.options.model=gemini-2.5-flash`, `spring.ai.google.genai.embedding.options.model=gemini-embedding-001` (one key serves chat + embeddings under `llm-real`).
- `apps/api/src/main/resources/application.properties` (**edit**) — add `blue-steel.llm.extraction-max-tokens=${BLUE_STEEL_LLM_EXTRACTION_MAX_TOKENS:4000}` (D-034 envelope); `.env.example` (**edit**) — mirror the new var.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/AiConfigTest.java` — unit test: the bean method returns a non-null `ChatClient` given a mock `ChatModel` (no Spring context start, no API key).

**Scope (out):** Ollama beans + dimension override (F2.12); the real Gemini `EmbeddingModel` bean (F2.5/F2.6); adapter logic (F2.4.3).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-032, D-034, D-088  **Dependencies:** F2.2.8

---

#### F2.4.3 — Real SpringAiNarrativeExtractionAdapter

**Goal:** Replace the F2.2.3 stub with the real LLM-call-1 implementation: provider-neutral `ChatClient`, structured output, token-budget guard, and cost logging (D-005, D-034, D-072).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiNarrativeExtractionAdapter.java` (**replace the stub body**, F2.2.3) — `@Component @Profile("llm-real | llm-ollama")`; inject `ChatClient` + `LlmCostLogger`; `@Value("${blue-steel.llm.extraction-max-tokens}")`; `TokenEstimator.estimate(rawSummaryText) > max` → throw `TokenBudgetExceededException` before any call; `chatClient.prompt().system(<extraction prompt incl. 1–3 sentence narrative-header instruction, D-005>).user(rawSummaryText).call()`; read `ChatResponse` usage metadata for `tokens_in`/`tokens_out`, then `.entity(ExtractionResult.class)`; `LlmCostLogger.logLlmCall("extraction", …)`; let an `.entity()` parse failure propagate (the service maps it to `failed`); never log raw response (LOG-01).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/SpringAiNarrativeExtractionAdapterTest.java` — asserts `TokenBudgetExceededException` on oversize input (no `ChatClient` invocation); happy path via a stubbed `ChatClient` fluent chain returning a canned `ExtractionResult`.

**Scope (out):** The `ChatClient` bean (F2.4.2); session transitions / MDC (F2.4.5); resolution adapter (F2.5).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-005, D-032, D-034, D-072, D-088  **Dependencies:** F2.2.1, F2.2.3, F2.4.1

---

#### F2.4.4 — NarrativeBlock lookup (port + adapter extension)

**Goal:** Add a read method so the listener can fetch the immutable summary text for a session — `NarrativeBlockRepository` (F2.3.3) currently exposes only `save()`. (Mirrors the F1.9.2 port+adapter-extension precedent.)

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/NarrativeBlockRepository.java` (**edit**, F2.3.3) — add `Optional<NarrativeBlock> findBySessionId(UUID sessionId)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockJpaRepository.java` (**edit**, F2.3.5) — derived query `Optional<NarrativeBlockJpaEntity> findBySessionId(UUID)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockPersistenceAdapter.java` (**edit**, F2.3.5) — implement `findBySessionId` with `toDomain` mapping.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/NarrativeBlockPersistenceAdapterIT.java` (**extend**, F2.3.5) — round-trip assertion for `findBySessionId`.

**Scope (out):** Consuming the result (F2.4.6).

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-054  **Dependencies:** F2.3.2, F2.3.3, F2.3.5

---

#### F2.4.5 — ExtractionPipelineService

**Goal:** Orchestrate the extraction stage: transition the session to `processing`, set the logging MDC, call the extraction port, and fail cleanly on error.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/ExtractionPipelineService.java` — internal `@Service`; `ExtractionResult run(Session session, String rawSummaryText)`: `session.startProcessing()` + `SessionRepository.save` (pending→processing); set MDC `session_id`/`user_id` from the session; call `NarrativeExtractionPort.extract(rawSummaryText)`; on any exception → `session.markFailed("EXTRACTION_FAILED")` + save, log ERROR with full context, rethrow; clear MDC in `finally`; INFO on entry/exit (LOG-02). Returns the `ExtractionResult` in-memory (the next stage is F2.5; the listener exits after extraction in this feature).
- `apps/api/src/test/java/com/bluesteel/application/session/ExtractionPipelineServiceTest.java` — mocked `NarrativeExtractionPort` + `SessionRepository`; asserts the processing transition, the `failed`/`EXTRACTION_FAILED` path on a thrown port exception, and the returned result on success.

**Scope (out):** The real adapter (F2.4.3); the listener edit (F2.4.6); entity resolution (F2.5).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-005, D-034, D-072  **Dependencies:** F2.2.1, F2.2.3, F2.3.1, F2.3.3

---

#### F2.4.6 — Wire extraction into SessionIngestionEventListener

**Goal:** Replace the F2.3.8 stub body so a submitted session runs real extraction instead of immediately failing with `PIPELINE_NOT_IMPLEMENTED`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java` (**edit**, F2.3.8) — on `SessionSubmittedEvent`, load the `Session` (`SessionRepository.findById`) and its `NarrativeBlock` (`NarrativeBlockRepository.findBySessionId`), then call `ExtractionPipelineService.run(session, block.rawSummaryText())`; the listener exits after extraction (session left in `processing` for F2.5+); failures are already handled inside the service.
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java` (**update**, F2.3.8) — mock the repos + `ExtractionPipelineService`; assert the listener loads the block and delegates extraction (no longer ends `FAILED` on the happy path).

**Scope (out):** Resolution/conflict/diff stages (F2.5–F2.7).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-005  **Dependencies:** F2.3.8, F2.4.4, F2.4.5

---

#### F2.5 — Entity resolution pipeline

> **Umbrella task — run the F2.5.N sub-tasks below, not this.**
>
> **No SETUP required** — ports/value models/mocks (F2.2), the `entity_embeddings` table (F2.1.4),
> the world-state version tables (F2.1.2/F2.1.3), and the shared LLM infra + extraction stage (F2.4)
> are produced by the cited dependencies; no new dependency or tooling is introduced.
>
> **Incremental-order note:** Stage 1 embeds mentions via `EmbeddingPort`, whose **real** adapter
> ships in F2.6 (F2.2.6 is a stub until then). F2.5 is built and tested against the **mock**
> `EmbeddingPort` (CI runs the `local` profile); the real embedding path activates with F2.6.

**Goal:** Two-stage entity resolution: pgvector similarity search (Stage 1) + LLM call 2 for borderline cases (Stage 2). Produces MATCH / NEW / UNCERTAIN outcomes (D-041, D-042).

**Scope (out):** Conflict detection (F2.6). Diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-041, D-042, D-062, D-088  
**Dependencies:** F2.4

---

#### F2.5.1 — SimilarityResult model + EntitySimilaritySearchPort

**Goal:** Define the driven port for Stage-1 retrieval and the flat result record it returns. Compile-only — an interface and a record; the `EntityResolutionService` (F2.5.4) builds `EntityContext` from these (ARCHITECTURE §6.2: context is assembled by the use case, never inside an adapter).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/ingestion/SimilarityResult.java` — `record SimilarityResult(UUID entityId, String entityType, String name, String stateSnapshot, UUID sessionId, int versionNumber, double similarity)` (the candidate's persistence projection + cosine similarity; the use case maps the non-`similarity` fields into an `EntityContext`).
- `apps/api/src/main/java/com/bluesteel/application/port/out/ingestion/EntitySimilaritySearchPort.java` — `List<SimilarityResult> search(float[] queryVector, UUID campaignId, String entityType, int topN)`.

**Scope (out):** The adapter implementation (F2.5.2); the use-case mapping to `EntityContext` (F2.5.4).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-041, D-062  **Dependencies:** F2.2.1

---

#### F2.5.2 — EntitySimilaritySearchAdapter (native pgvector retrieval)

**Goal:** Implement `EntitySimilaritySearchPort` as a native pgvector query (no `VectorStore`, D-062/ARCH-04) joining `entity_embeddings` to the type-specific head + version tables to project the candidate snapshot.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/embedding/EntitySimilaritySearchAdapter.java` — `@Component` (`JdbcTemplate`, mirrors `SessionRecoveryAdapter`); native SQL `SELECT …, 1 - (e.embedding <=> ?::vector) AS similarity FROM entity_embeddings e JOIN <type>_versions v ON v.id = e.entity_version_id JOIN <type>s h ON h.id = e.entity_id WHERE e.entity_type = ? AND h.campaign_id = ? ORDER BY e.embedding <=> ?::vector LIMIT ?`; the `<type>` head/version table pair is chosen from a **whitelisted** `entity_type` (never string-interpolated input); derive the prose `stateSnapshot` from `v.full_snapshot`; render `float[]` → pgvector literal; map rows → `SimilarityResult`.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/embedding/EntitySimilaritySearchAdapterIT.java` — extends `TestcontainersPostgresBaseIT`; seeds a session + an actor head + `actor_versions` row (with `full_snapshot`, `version_number`) + an `entity_embeddings` row, then asserts `search` returns it with a similarity score, scoped by `campaign_id`/`entity_type`, ordered by distance.

**Scope (out):** The other AI ports; Query-Mode reuse of this port (Phase 3); embedding generation (F2.6/D-063).

**Skills:** `session-ingestion-pipeline`, `backend-testing`, `database-migration`  **Decisions:** D-062, D-088  **Dependencies:** F2.1.2, F2.1.3, F2.1.4, F2.5.1

---

#### F2.5.3 — Real SpringAiEntityResolutionAdapter (LLM call 2)

**Goal:** Replace the F2.2.4 stub with the real bounded LLM resolution call: given high-score mentions + candidate `EntityContext`s, return MATCH/NEW/UNCERTAIN outcomes (D-042).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiEntityResolutionAdapter.java` (**replace the stub body**, F2.2.4) — `@Component @Profile("llm-real | llm-ollama")`; inject `ChatClient` + `LlmCostLogger`; `@Value("${blue-steel.llm.resolution-max-tokens}")`; `TokenEstimator` budget check on the serialized mentions+context → `TokenBudgetExceededException` before the call; `chatClient.prompt().system(<resolution prompt>).user(<mentions + candidate contexts>).call()` → read `ChatResponse` usage; `.entity(...)` structured output → per-mention `ResolutionOutcome` + nullable `matchedEntityId`; map to `List<ResolvedEntity>`; `LlmCostLogger.logLlmCall("resolution", …)` (MDC `stage="resolution"`, D-088); never log raw response (LOG-01).
- `apps/api/src/main/resources/application.properties` (**edit**) + `.env.example` (**edit**) — add `blue-steel.llm.resolution-max-tokens=${BLUE_STEEL_LLM_RESOLUTION_MAX_TOKENS:2000}` (D-034 envelope).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/SpringAiEntityResolutionAdapterTest.java` — stubbed `ChatClient` fluent chain returns canned outcomes; assert mapping to `ResolvedEntity` (incl. `matchedEntityId` only for MATCH) and `TokenBudgetExceededException` on oversize input.

**Scope (out):** Stage-1 retrieval (F2.5.2); orchestration + the similarity floor (F2.5.4); the `ChatClient` bean (F2.4.2).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-042, D-034, D-072, D-088  **Dependencies:** F2.2.1, F2.2.2, F2.2.4, F2.4.1

---

#### F2.5.4 — EntityResolutionService (two-stage orchestration)

**Goal:** Orchestrate Stage 1 (pgvector, no LLM) + Stage 2 (bounded LLM) per extracted mention, producing one `ResolvedEntity` each (D-041). Builds the `EntityContext` candidates from `SimilarityResult`s before the port call.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/EntityResolutionService.java` — internal `@Service`; `List<ResolvedEntity> run(UUID campaignId, ExtractionResult extraction)`: for each mention in each typed list (actors/spaces/events/relations): Stage 1 — `EmbeddingPort.embed(<mention name/description>)` → `EntitySimilaritySearchPort.search(vector, campaignId, entityType, topN)`; if max similarity `< blue-steel.resolution.similarity-floor` (default 0.75) → `ResolvedEntity(NEW)` with **no** LLM call; else Stage 2 — map the top-`N` `SimilarityResult`s to `EntityContext` and call `EntityResolutionPort.resolve(mention, candidates)` → MATCH/NEW/UNCERTAIN; aggregate. INFO entry/exit (LOG-02).
- `apps/api/src/main/resources/application.properties` (**edit**) + `.env.example` (**edit**) — add `blue-steel.resolution.similarity-floor=${BLUE_STEEL_RESOLUTION_SIMILARITY_FLOOR:0.75}` and `blue-steel.resolution.top-n=${BLUE_STEEL_RESOLUTION_TOP_N:3}`.
- `apps/api/src/test/java/com/bluesteel/application/session/EntityResolutionServiceTest.java` — mocked `EmbeddingPort` + `EntitySimilaritySearchPort` + `EntityResolutionPort`; asserts below-floor → `NEW` with **no** resolution-port call, above-floor → forwards top-`N` contexts and returns the port's outcomes.

**Scope (out):** The real adapters (F2.5.2/F2.5.3 — tests use mocks); listener wiring (F2.5.5); conflict detection (F2.6).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-041, D-042  **Dependencies:** F2.2.1, F2.2.2, F2.2.4, F2.2.6, F2.5.1

---

#### F2.5.5 — Wire resolution into SessionIngestionEventListener

**Goal:** Extend the listener so a session runs entity resolution after extraction, holding the `List<ResolvedEntity>` in-memory for the next stage (F2.6).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java` (**edit**, F2.4.6) — after `ExtractionPipelineService.run(...)` returns the `ExtractionResult`, call `EntityResolutionService.run(session.campaignId(), extractionResult)`; the listener exits after resolution (the resolved entities are passed to conflict detection in F2.6).
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java` (**update**, F2.4.6) — mock `ExtractionPipelineService` + `EntityResolutionService`; assert resolution is invoked with the extraction output after extraction.

**Scope (out):** Conflict detection (F2.6); diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-041  **Dependencies:** F2.4.6, F2.5.4

---

#### F2.6 — Conflict detection pipeline

> **Umbrella task — run the F2.6.N sub-tasks below, not this.**
>
> **No SETUP required** — ports/value models/mocks (F2.2), the shared LLM infra + `llm-real` config
> (F2.4), and the similarity-search port (F2.5.1) are produced by the cited dependencies; no new
> dependency or tooling is introduced.
>
> **Scope reconciliation (real `EmbeddingPort`):** F2.2.6 defers the real `SpringAiEmbeddingAdapter`
> to *"(F2.6/D-063)"* and F2.5/F2.6/F2.8 all call `EmbeddingPort`, but F2.6's prose listed only the
> conflict pieces. F2.6.1 below implements that real adapter (the first task to turn on real
> embeddings); the **async post-commit generation** that *writes* `entity_embeddings` rows stays in
> F2.8 (`EmbeddingGenerationListener`, D-063), and the **Ollama** embedding bean is F2.12.

**Goal:** LLM Call 3. Compare extracted facts against current world state for hard contradictions. Produces non-blocking `ConflictWarning` cards (D-033).

**Scope (out):** Diff generation (F2.7). Async post-commit embedding generation (F2.8, D-063). Ollama embedding bean (F2.12).

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-033, D-034, D-062, D-088  
**Dependencies:** F2.5

---

#### F2.6.1 — Real SpringAiEmbeddingAdapter

**Goal:** Replace the F2.2.6 stub with the real embedding implementation — the first task to enable real embeddings (used by Stage-1 resolution, conflict-context retrieval, and post-commit generation). Provider-neutral: Gemini `gemini-embedding-001`@1536 (outputDimensionality=1536) under `llm-real`, Ollama `bge-m3`@1024 under `llm-ollama` (D-093, D-088).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiEmbeddingAdapter.java` (**replace the stub body**, F2.2.6) — `@Component @Profile("llm-real | llm-ollama")`; inject the Spring AI `EmbeddingModel` (auto-configured from `spring.ai.google.genai.api-key` set in `application-llm-real.properties`, F2.4.2); `float[] embed(String content)` → `embeddingModel.embed(content)`; ERROR-log + rethrow on provider failure (LOG-02); never log `content`.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/SpringAiEmbeddingAdapterTest.java` — mock `EmbeddingModel`; assert `embed` delegates and returns the model's vector.

**Scope (out):** Async post-commit generation into `entity_embeddings` (F2.8, D-063); the Ollama `EmbeddingModel` bean selection (F2.12); similarity *search* (F2.5.2).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-040, D-063, D-088  **Dependencies:** F2.2.6, F2.4.2

---

#### F2.6.2 — Real SpringAiConflictDetectionAdapter (LLM call 3)

**Goal:** Replace the F2.2.5 stub with the real bounded LLM call-3: compare the extraction against the supplied world-state context and return non-blocking `ConflictWarning`s (D-033).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiConflictDetectionAdapter.java` (**replace the stub body**, F2.2.5) — `@Component @Profile("llm-real | llm-ollama")`; inject `ChatClient` + `LlmCostLogger`; `@Value("${blue-steel.llm.conflict-max-tokens}")`; `TokenEstimator` budget check on serialized extraction+context → `TokenBudgetExceededException`; `chatClient.prompt().system(<conflict prompt>).user(<extraction + relevant context>).call()` → read `ChatResponse` usage; `.entity(...)` → `List<ConflictWarning>`; `LlmCostLogger.logLlmCall("conflict_detection", …)` (MDC `stage="conflict_detection"`, D-088); never log raw response (LOG-01).
- `apps/api/src/main/resources/application.properties` (**edit**) + `.env.example` (**edit**) — add `blue-steel.llm.conflict-max-tokens=${BLUE_STEEL_LLM_CONFLICT_MAX_TOKENS:3000}` (D-034 envelope).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/SpringAiConflictDetectionAdapterTest.java` — stubbed `ChatClient` fluent chain returns canned warnings; assert mapping to `List<ConflictWarning>` and `TokenBudgetExceededException` on oversize input.

**Scope (out):** MATCH scoping + context assembly + the skip-when-no-MATCH rule (F2.6.3); the `ChatClient` bean (F2.4.2).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-033, D-034, D-072, D-088  **Dependencies:** F2.2.1, F2.2.5, F2.4.1

---

#### F2.6.3 — ConflictDetectionService (MATCH-scoped context + LLM call)

**Goal:** Orchestrate conflict detection: only when there are MATCH-resolved entities, retrieve bounded world-state context via pgvector and call the conflict port; otherwise skip the LLM call and return an empty list (D-033, D-034 bounding).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/ConflictDetectionService.java` — internal `@Service`; `List<ConflictWarning> run(UUID campaignId, ExtractionResult extraction, List<ResolvedEntity> resolved)`: if no `ResolvedEntity` has outcome `MATCH` → return `List.of()` (no LLM call); else `EmbeddingPort.embed(extraction.narrativeSummaryHeader())` → `EntitySimilaritySearchPort.search(vector, campaignId, …, topN)` per relevant type → map `SimilarityResult`s to `EntityContext` (use case assembles context, ARCHITECTURE §6.2) → `ConflictDetectionPort.detect(extraction, relevantContext)`; INFO entry/exit (LOG-02).
- `apps/api/src/main/resources/application.properties` (**edit**) + `.env.example` (**edit**) — add `blue-steel.conflict.context-top-n=${BLUE_STEEL_CONFLICT_CONTEXT_TOP_N:5}`.
- `apps/api/src/test/java/com/bluesteel/application/session/ConflictDetectionServiceTest.java` — mocked `EmbeddingPort` + `EntitySimilaritySearchPort` + `ConflictDetectionPort`; asserts no-MATCH → empty list with **no** port calls, and the MATCH path embeds + searches + forwards context to the conflict port.

**Scope (out):** The real adapters (F2.6.1/F2.6.2 — tests use mocks); listener wiring (F2.6.4); diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-033, D-062  **Dependencies:** F2.2.1, F2.2.2, F2.2.5, F2.2.6, F2.5.1

---

#### F2.6.4 — Wire conflict detection into SessionIngestionEventListener

**Goal:** Extend the listener so a session runs conflict detection after resolution, holding the `List<ConflictWarning>` in-memory for diff generation (F2.7).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java` (**edit**, F2.5.5) — after `EntityResolutionService.run(...)` returns the `List<ResolvedEntity>`, call `ConflictDetectionService.run(campaignId, extractionResult, resolved)`; the listener exits after conflict detection (extraction + resolved + warnings are passed to diff generation in F2.7).
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java` (**update**, F2.5.5) — mock the three stage services; assert conflict detection is invoked with the extraction + resolved outputs after resolution.

**Scope (out):** Diff generation + the `draft` transition (F2.7).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-033  **Dependencies:** F2.5.5, F2.6.3

---

#### F2.7 — Diff generation + draft API

> **Umbrella task — run the F2.7.N sub-tasks below, not this.**
>
> **No SETUP required** — the pipeline-stage outputs (F2.4–F2.6), the `Session` aggregate +
> repository (F2.3), and the `SessionController` (F2.3.11) are produced by the cited dependencies;
> no new dependency or tooling is introduced.
>
> **Schema authority:** `DiffPayload` is the formal contract in ARCHITECTURE §7.6 (D-076) — camelCase
> keys (D-076 amended 2026-05-24), `cardType`-discriminated `DiffCard` union (EXISTING/NEW/UNCERTAIN) + `ConflictCard`. The
> backend records and `apps/web/src/types/sessions.ts` must mirror it exactly; it is read-only to the
> client (the commit payload, F2.8, is a separate derived shape).

**Goal:** Assemble the structured diff from pipeline outputs, persist as `diff_payload` JSONB, transition session to `draft`, expose the diff retrieval endpoint. Output: a browser-renderable diff conforming to the formal schema in ARCHITECTURE.md §7.6.

**Scope (out):** Commit (F2.8). User-edited fields are in the commit payload — not persisted to `diff_payload`.

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-004, D-005, D-006, D-007, D-042, D-076  
**Dependencies:** F2.6

---

#### F2.7.1 — DiffCard sealed interface + entity card variants

**Goal:** Model the three `DiffCard` variants as a `cardType`-discriminated union per ARCHITECTURE §7.6 (D-076), with Jackson polymorphism so the payload round-trips through JSONB and the API unchanged.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/diff/DiffCard.java` — `sealed interface` permitting the three records; `@JsonTypeInfo(use = NAME, property = "cardType")` + `@JsonSubTypes` mapping `EXISTING`/`NEW`/`UNCERTAIN`.
- `apps/api/src/main/java/com/bluesteel/application/model/diff/ExistingEntityCard.java` — `cardId, entityId, entityType, name, changedFields (Map<String,Object>)` (delta only, D-006).
- `apps/api/src/main/java/com/bluesteel/application/model/diff/NewEntityCard.java` — `cardId, entityType, name, fullProfile (Map<String,Object>)` (full profile, D-007).
- `apps/api/src/main/java/com/bluesteel/application/model/diff/UncertainEntityCard.java` — `cardId, entityType, extractedMention, candidateEntityId, candidateEntityName` (D-042).
- `apps/api/src/test/java/com/bluesteel/application/model/diff/DiffCardTest.java` — Jackson round-trip per variant asserting the `cardType` discriminator + camelCase keys (use the project `ObjectMapper`).

> Field names are plain camelCase records and serialize as-is — no `@JsonProperty` casing overrides (D-076, amended 2026-05-24: the diff contract is camelCase like the rest of the API). The `cardType` discriminator is the only Jackson property name pinned explicitly (via `@JsonTypeInfo`).

**Scope (out):** `ConflictCard` + `DiffPayload` aggregate (F2.7.2); assembly logic (F2.7.3).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-006, D-007, D-042, D-076  **Dependencies:** F2.6

---

#### F2.7.2 — ConflictCard + DiffPayload aggregate

**Goal:** Model the conflict card and the top-level `DiffPayload` envelope that holds the typed card arrays + conflicts, completing the §7.6 contract.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/diff/ConflictCard.java` — `conflictId, entityId, entityType, description, extractedFact, existingFact` (non-blocking, D-033).
- `apps/api/src/main/java/com/bluesteel/application/model/diff/DiffPayload.java` — `record DiffPayload(String narrativeSummaryHeader, List<DiffCard> actors, List<DiffCard> spaces, List<DiffCard> events, List<DiffCard> relations, List<ConflictCard> detectedConflicts)` (plain camelCase records; fields serialize as-is — no `@JsonProperty`).
- `apps/api/src/test/java/com/bluesteel/application/model/diff/DiffPayloadTest.java` — full-payload Jackson round-trip with a mix of card variants + a conflict.

**Scope (out):** Assembly from pipeline outputs (F2.7.3); persistence (F2.7.3 stores the serialized string on the session).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-005, D-033, D-076  **Dependencies:** F2.7.1

---

#### F2.7.3 — DiffGenerationService

**Goal:** Assemble the `DiffPayload` from the three in-memory pipeline outputs, serialize it, attach it to the session, and transition the session to `draft`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/DiffGenerationService.java` — internal `@Service`; `void run(Session session, ExtractionResult extraction, List<ResolvedEntity> resolved, List<ConflictWarning> conflicts)`: set `narrativeSummaryHeader` from the extraction; per resolved mention → MATCH ⇒ `ExistingEntityCard` (delta), NEW ⇒ `NewEntityCard` (full profile), UNCERTAIN ⇒ `UncertainEntityCard`; map `ConflictWarning`s → `ConflictCard`s; mint a `cardId`/`conflictId` (`UUID`) per card; serialize the `DiffPayload` via the injected Jackson `ObjectMapper`; attach the JSON to the session and transition it to `draft` (the `Session` aggregate stores `diff_payload` and exposes the draft transition, F2.3.1); `SessionRepository.save`; INFO entry/exit (LOG-02).
- `apps/api/src/test/java/com/bluesteel/application/session/DiffGenerationServiceTest.java` — mocked `SessionRepository` (+ real `ObjectMapper`); asserts each outcome → the right card variant, conflicts mapped, session ends `draft` with a non-null `diff_payload`.

**Scope (out):** The async listener wiring (F2.7.4); the read endpoint (F2.7.5/F2.7.6); commit + user edits (F2.8 — not persisted to `diff_payload`).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-004, D-005, D-006, D-007, D-042, D-076  **Dependencies:** F2.7.2, F2.2.1, F2.2.2, F2.2.5, F2.3.1, F2.3.3

---

#### F2.7.4 — Wire diff generation into SessionIngestionEventListener (final stage)

**Goal:** Complete the ingestion pipeline: after conflict detection, generate the diff and leave the session in `draft` (ready for review).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java` (**edit**, F2.6.4) — after `ConflictDetectionService.run(...)` returns the `List<ConflictWarning>`, call `DiffGenerationService.run(session, extraction, resolved, conflicts)`; this is the final stage — the session transitions `processing → draft` and the listener completes.
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java` (**update**, F2.6.4) — mock the four stage services; assert diff generation runs last and the happy path ends in `draft` (not `processing`).

**Scope (out):** The diff read API (F2.7.5/F2.7.6); commit (F2.8).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-005, D-076  **Dependencies:** F2.6.4, F2.7.3

---

#### F2.7.5 — GetSessionDiffUseCase + service

**Goal:** Read the persisted draft diff for review: authorize `gm|editor`, require `draft` status, deserialize `diff_payload` → `DiffPayload`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/session/GetSessionDiffUseCase.java` — `DiffPayload getDiff(UUID callerId, UUID campaignId, UUID sessionId)`.
- `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionDiffService.java` — resolve role via `CampaignMembershipPort`, require `gm|editor` (else `UnauthorizedException`); load session or `SessionNotFoundException` (404); if status != `draft` (or `diff_payload` null) → 404 (the draft diff resource does not exist outside `draft`); deserialize `diff_payload` via the injected `ObjectMapper` → `DiffPayload`.
- `apps/api/src/test/java/com/bluesteel/application/session/GetSessionDiffServiceTest.java` — mocked `SessionRepository` + `CampaignMembershipPort` (+ real `ObjectMapper`); covers authz, not-found/not-draft → 404, and the happy-path deserialization.

**Scope (out):** The controller/HTTP mapping (F2.7.6); diff generation (F2.7.3); commit (F2.8).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-076  **Dependencies:** F2.7.2, F2.3.1, F2.3.3, F1.8.7

---

#### F2.7.6 — Diff retrieval endpoint (GET .../diff)

**Goal:** Expose the draft diff over HTTP, mirroring the §7.6 schema.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java` (**edit**, F2.3.11) — add `GET /api/v1/campaigns/{id}/sessions/{sid}/diff`; caller via `UUID.fromString(auth.getName())`; delegates to `GetSessionDiffUseCase`; returns `ApiResponse<DiffPayload>` (200); role + draft enforcement live in the service; not-draft/not-found → 404 (existing `SessionNotFoundException` mapping, F2.3.11).
- `apps/api/src/test/java/com/bluesteel/adapters/in/web/session/SessionControllerTest.java` (**update**, F2.3.11) — `@WebMvcTest` with a mocked `GetSessionDiffUseCase`; assert 200 returns the payload envelope and the 404 path.

**Scope (out):** Commit endpoint (F2.8); frontend diff review (F2.10).

**Skills:** `session-ingestion-pipeline`, `backend-endpoint`  **Decisions:** D-076  **Dependencies:** F2.3.11, F2.7.5

---

#### F2.8 — Commit endpoint

> **Umbrella task — run the F2.8.N sub-tasks below, not this.**
>
> **No SETUP required** — Spring async (`@EnableAsync` on `config/ApplicationConfig`), Jackson, and
> Liquibase are already wired; the world-state tables (F2.1.2/F2.1.3), `entity_embeddings`
> (F2.1.4), the `Session` aggregate + repository (F2.3), the diff model (F2.7), and `EmbeddingPort`
> (F2.2.6/F2.6.1) are produced by the cited dependencies. No new dependency or tooling.
>
> **Scheduler reconciliation:** the D-074 scheduled stuck-`processing` TTL check is **already**
> built by **F2.3.9** (`recoverTimedOutSessions`) + **F2.3.10** (`SessionTimeoutRecoveryScheduler`
> + `@EnableScheduling`). F2.8 does **not** recreate it.
>
> **World-state write persistence (D-089):** the write across the four head/version table-pairs uses
> native `JdbcTemplate` (whitelisted `entity_type` routing), **not** JPA — append-only inserts over
> four structurally-identical table-pairs, consistent with the F2.5.2 read path. See D-089.
>
> **Payload contract:** `CommitPayload` is the camelCase schema in ARCHITECTURE §7.6 (D-076) — plain
> records, no `@JsonProperty`. It is a *derived* shape, distinct from the read-only `DiffPayload`.

**Goal:** Validate the reviewed commit payload against the stored diff (8 checks, D-078–D-081),
write world state synchronously, assign `sequence_number` (D-069), transition the session to
`committed`, and trigger async post-commit embedding generation (D-063). Returns 200 immediately.

**Skills:** `session-ingestion-pipeline`, `backend-domain-model`, `backend-endpoint`, `backend-testing`, `database-migration`  
**Decisions:** D-001, D-002, D-033, D-042, D-053, D-063, D-069, D-076, D-078, D-079, D-080, D-081, D-089  
**Dependencies:** F2.7

---

#### F2.8.1 — Commit application model

**Goal:** The camelCase `CommitPayload` record family the validator and service consume, mirroring
ARCHITECTURE §7.6 exactly (D-076). Plain records — no `@JsonProperty`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/commit/CommitPayload.java` — `record(List<CardDecision> cardDecisions, List<UncertainResolution> uncertainResolutions, List<AcknowledgedConflict> acknowledgedConflicts)`.
- `apps/api/src/main/java/com/bluesteel/application/model/commit/CardDecision.java` — `record(UUID cardId, CardAction action, Map<String,Object> editedFields)` (`editedFields` nullable).
- `apps/api/src/main/java/com/bluesteel/application/model/commit/UncertainResolution.java` — `record(UUID cardId, ResolutionType resolution, UUID matchedEntityId)` (`matchedEntityId` nullable).
- `apps/api/src/main/java/com/bluesteel/application/model/commit/AcknowledgedConflict.java` — `record(UUID conflictId)`.
- `apps/api/src/main/java/com/bluesteel/application/model/commit/CardAction.java` — `enum {ACCEPT, EDIT, DELETE}` with lowercase JSON mapping (`@JsonValue`/`@JsonCreator` → `accept|edit|delete`).
- `apps/api/src/main/java/com/bluesteel/application/model/commit/ResolutionType.java` — `enum {MATCH, NEW}` (uppercase, serializes as-is).
- `apps/api/src/test/java/com/bluesteel/application/model/commit/CommitPayloadTest.java` — Jackson round-trip via the project `ObjectMapper`: camelCase keys, lowercase `action`, uppercase `resolution`.

**Scope (out):** Adapter request DTO + Bean Validation (F2.8.10); validation logic (F2.8.7).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-053, D-076  **Dependencies:** F2.7.2

---

#### F2.8.2 — Session.commit(sequenceNumber) domain transition

**Goal:** Extend the `Session` aggregate so commit records the assigned `sequence_number` (D-069),
stamps `committedAt`, and clears `diffPayload` as part of the draft→committed transition.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/session/Session.java` (**edit**, F2.3.1) — replace the no-arg `commit()` with `commit(int sequenceNumber)`: guard `draft → committed` (else `InvalidSessionStateTransitionException`); set `sequenceNumber`, `committedAt = now`, `status = COMMITTED`, clear `diffPayload`.
- `apps/api/src/test/java/com/bluesteel/domain/session/SessionTest.java` (**edit**, F2.3.1) — assert commit from `draft` sets all three fields + clears diff; commit from any non-`draft` status throws.

**Scope (out):** Computing the number (F2.8.3); persistence (F2.8.8 saves via repository).

**Skills:** `backend-domain-model`  **Decisions:** D-069  **Dependencies:** F2.3.1

---

#### F2.8.3 — Session sequence-number assignment query

**Goal:** `MAX(sequence_number) + 1` across the campaign's `committed` sessions (D-069), evaluated in
the commit transaction. Extends the existing session port + adapter.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRepository.java` (**edit**, F2.3.3) — add `int nextSequenceNumber(UUID campaignId)`; Javadoc: next ordinal across `committed` sessions; called inside the commit `@Transactional` (D-069).
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionJpaRepository.java` (**edit**, F2.3.4) — `@Query` `SELECT COALESCE(MAX(s.sequenceNumber),0)+1 FROM SessionJpaEntity s WHERE s.campaignId = ?1 AND s.status = 'committed'`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java` (**edit**, F2.3.4) — implement `nextSequenceNumber` delegating to the query.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapterIT.java` (**extend**, F2.3.4) — seed N committed + a non-committed session; assert next = N+1 and non-committed states are ignored.

**Scope (out):** Domain transition (F2.8.2); the use case calling this (F2.8.8). Atomicity rests on the one-active-draft rule (D-054) serializing commits per campaign.

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-069  **Dependencies:** F2.1.1, F2.3.3, F2.3.4

---

#### F2.8.4 — World-state write contract

**Goal:** Declare the driven port + value records for the world-state write. Compile-only (interface
+ records; ArchUnit covers placement).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/worldstate/WorldStatePort.java` — `CommittedEntityVersion writeEntity(EntityWriteCommand cmd)` (create head + v1 when `existingEntityId` null, else append next version); `boolean existsInCampaign(String entityType, UUID entityId, UUID campaignId)` (backs the D-079 INVALID_ENTITY_REFERENCE check).
- `apps/api/src/main/java/com/bluesteel/application/model/worldstate/EntityWriteCommand.java` — `record(String entityType, UUID existingEntityId, UUID campaignId, UUID ownerId, String name, Map<String,Object> changedFields, Map<String,Object> fullSnapshot, UUID sessionId)`.
- `apps/api/src/main/java/com/bluesteel/application/model/worldstate/CommittedEntityVersion.java` — `record(String entityType, UUID entityId, UUID entityVersionId, int versionNumber, String contentToEmbed, String contentHash)` (carried by the commit event so the async listener embeds without re-reading).

**Scope (out):** The native-SQL implementation (F2.8.5); consumers (F2.8.7/F2.8.8/F2.8.9).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-001, D-079  **Dependencies:** F2.1.2, F2.1.3

---

#### F2.8.5 — WorldStateAdapter (native-SQL head + version write)

**Goal:** Implement `WorldStatePort` as native `JdbcTemplate` SQL over the four head/version
table-pairs (D-089), mirroring `SessionRecoveryAdapter`/F2.5.2: whitelisted `entity_type` → table
routing, `MAX(version_number)+1` per head in-transaction (D-001), JSONB via `?::jsonb`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/worldstate/WorldStateAdapter.java` — `@Component`, `@Lazy JdbcTemplate`; private whitelist enum mapping `actor|space|event|relation` → (`<t>s`, `<t>_versions`) (never interpolate caller input); NEW → INSERT head (`campaign_id, owner_id, name, created_at, created_in_session_id`) + INSERT version at `version_number=1`; EXISTING → `SELECT COALESCE(MAX(version_number),0)+1` for the head, INSERT version; serialize `changedFields`/`fullSnapshot` to JSON and bind `?::jsonb`; derive `contentToEmbed` (name + snapshot prose) + `contentHash` (SHA-256); return `CommittedEntityVersion`; `existsInCampaign` via `SELECT EXISTS(... WHERE id=? AND campaign_id=?)`. **Class Javadoc documents why native SQL is used here and cites D-089** (the recorded persistence decision).
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/worldstate/WorldStateAdapterIT.java` — extends `TestcontainersPostgresBaseIT`; NEW inserts head + version 1; a second write to the same head appends version 2 (MAX+1); JSONB round-trips; `existsInCampaign` is true only within the owning campaign; entity_type whitelist rejects an unknown type.

**Scope (out):** The port/records (F2.8.4); embedding rows (F2.8.6).

**Skills:** `session-ingestion-pipeline`, `backend-testing`, `database-migration`  **Decisions:** D-001, D-003, D-089  **Dependencies:** F2.8.4, F2.1.2, F2.1.3

---

#### F2.8.6 — EntityEmbeddingWritePort + write adapter

**Goal:** The insert path the async listener uses to persist one `entity_embeddings` row per
committed entity version (D-063). Native INSERT, `float[]`→`::vector` literal (mirror F2.5.2).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/embedding/EntityEmbeddingWritePort.java` — `void insert(EntityEmbeddingRow row)`.
- `apps/api/src/main/java/com/bluesteel/application/model/embedding/EntityEmbeddingRow.java` — `record(String entityType, UUID entityId, UUID entityVersionId, UUID sessionId, float[] embedding, String contentHash)`.
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/embedding/EntityEmbeddingWriteAdapter.java` — `@Component`, `@Lazy JdbcTemplate`; `INSERT INTO entity_embeddings (id, entity_type, entity_id, entity_version_id, session_id, embedding, content_hash, created_at) VALUES (?, ?, ?, ?, ?, ?::vector, ?, now())`; reuse the F2.5.2 `float[]`→pgvector-literal rendering.
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/embedding/EntityEmbeddingWriteAdapterIT.java` — extends `TestcontainersPostgresBaseIT`; seed session + actor head/version, insert an embedding row, assert it is present with the right `entity_type` and dimension.

**Scope (out):** Reading/searching embeddings (F2.5.2); the listener that calls this (F2.8.9).

**Skills:** `session-ingestion-pipeline`, `database-migration`, `backend-testing`  **Decisions:** D-040, D-062, D-063  **Dependencies:** F2.1.4

---

#### F2.8.7 — CommitPayloadValidator + CommitValidationException

**Goal:** The application-layer gatekeeper (D-081): all 8 commit checks against the stored
`DiffPayload`, throwing before any world-state write. One parameterized 422 exception.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/exception/CommitValidationException.java` — extends `DomainException`; carries `code()` ∈ {`UNKNOWN_CARD_ID`, `DUPLICATE_CARD_DECISION`, `INCOMPLETE_CARD_DECISIONS`, `UNCERTAIN_ENTITIES_PRESENT`, `CONFLICTS_NOT_ACKNOWLEDGED`, `INVALID_ENTITY_REFERENCE`, `UNSUPPORTED_ACTION`} (mirrors `RefreshTokenException.code()`).
- `apps/api/src/main/java/com/bluesteel/application/service/session/CommitPayloadValidator.java` — `@Component`; `validate(DiffPayload storedDiff, CommitPayload payload, UUID campaignId)`: cardId-existence + no-duplicates (D-076); every non-UNCERTAIN card has a decision (D-080); every UNCERTAIN card resolved (D-042); every conflict acknowledged (D-033); `action == add` → UNSUPPORTED_ACTION (D-053); for MATCH resolutions, `WorldStatePort.existsInCampaign(...)` false → INVALID_ENTITY_REFERENCE (D-079, app tier).
- `apps/api/src/test/java/com/bluesteel/application/session/CommitPayloadValidatorTest.java` — one failing case per code + a passing case; this is the D-081-mandated suite proving each precondition is checked.

**Scope (out):** Orchestration / world-state write (F2.8.8); the 400 cross-field checks (F2.8.10, adapter Bean Validation).

**Skills:** `session-ingestion-pipeline`, `error-handling`  **Decisions:** D-033, D-042, D-053, D-076, D-078, D-079, D-080, D-081  **Dependencies:** F2.8.1, F2.7.1, F2.7.2, F2.8.4

---

#### F2.8.8 — CommitService + driving port + commit event

**Goal:** Orchestrate the commit: authorize `gm|editor`, deserialize the stored diff, validate
(F2.8.7), write world state (F2.8.5), assign `sequence_number` (F2.8.3), transition the session
(F2.8.2), and publish the post-commit event. Validation runs strictly before any write (D-081).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/session/CommitSessionUseCase.java` — `void commit(CommitSessionCommand command)`.
- `apps/api/src/main/java/com/bluesteel/application/model/session/CommitSessionCommand.java` — `record(UUID callerId, UUID campaignId, UUID sessionId, CommitPayload payload)`.
- `apps/api/src/main/java/com/bluesteel/application/event/SessionCommittedEvent.java` — `record(UUID sessionId, UUID campaignId, List<CommittedEntityVersion> committedVersions)`.
- `apps/api/src/main/java/com/bluesteel/application/service/session/CommitService.java` — `@Service`, `@Transactional`; resolve role via `CampaignMembershipPort` (non-`gm|editor` → `UnauthorizedException` 403); load session or `SessionNotFoundException` (404); require `draft` (else `InvalidSessionStateTransitionException` 409); deserialize `diff_payload`→`DiffPayload` (injected `ObjectMapper`); `CommitPayloadValidator.validate(...)`; per `cardDecision`: ACCEPT/EDIT → `WorldStatePort.writeEntity(...)` (EDIT merges `editedFields`), DELETE → skip; apply `uncertainResolutions` (MATCH → write vs `matchedEntityId`, NEW → create); `int seq = sessionRepository.nextSequenceNumber(campaignId)`; `session.commit(seq)`; `sessionRepository.save(session)`; publish `SessionCommittedEvent`; INFO entry/exit (LOG-02).
- `apps/api/src/test/java/com/bluesteel/application/session/CommitServiceTest.java` — mocked ports; D-081 suite: validator invoked before any `WorldStatePort` call; happy path writes versions, assigns seq, transitions `committed`/clears diff, publishes event; authz 403, 404, non-draft 409.

**Scope (out):** HTTP/DTO/Bean Validation (F2.8.10); async embedding (F2.8.9).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-002, D-063, D-069, D-081  **Dependencies:** F2.8.1, F2.8.2, F2.8.3, F2.8.4, F2.8.7, F2.7.2, F2.3.3, F1.8.7

---

#### F2.8.9 — EmbeddingGenerationListener (async post-commit)

**Goal:** After the commit transaction commits (D-063), embed each committed entity version and
insert its `entity_embeddings` row asynchronously; per-entity failures are logged and swallowed so
one failure never aborts the rest or the listener.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/session/EmbeddingGenerationListener.java` — `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` on `SessionCommittedEvent` (`@EnableAsync` already on `ApplicationConfig`); for each `CommittedEntityVersion`: `EmbeddingPort.embed(contentToEmbed)` → `EntityEmbeddingWritePort.insert(...)`; wrap each in try/catch — ERROR-log with `session_id`/entity ids and continue (D-063); never log raw content (LOG-01).
- `apps/api/src/test/java/com/bluesteel/application/session/EmbeddingGenerationListenerTest.java` — mocked `EmbeddingPort` + `EntityEmbeddingWritePort`; asserts one insert per version, and that a thrown `embed` for one version is swallowed while the others still insert.

**Scope (out):** Real embedding provider wiring (F2.6.1 / F2.12); the write adapter (F2.8.6).

**Skills:** `session-ingestion-pipeline`  **Decisions:** D-063  **Dependencies:** F2.2.6, F2.8.6, F2.8.8

---

#### F2.8.10 — Commit endpoint (controller + request DTO + 422 mapping)

**Goal:** Expose `POST .../commit`, validate request format (400 cross-field per D-076/D-079), map
to `CommitPayload`, delegate to the use case, and map `CommitValidationException` → 422 with its code.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/CommitSessionRequest.java` — request DTO + nested `CardDecisionRequest`/`UncertainResolutionRequest`/`AcknowledgedConflictRequest`; Bean Validation: `@NotEmpty cardDecisions`; `@AssertTrue` cross-field — `editedFields` non-empty when `action == edit` (D-076), `matchedEntityId` non-null when `resolution == MATCH` (D-079) → both **400**.
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java` (**edit**, F2.3.11/F2.7.6) — add `POST /api/v1/campaigns/{id}/sessions/{sid}/commit`; `@Valid CommitSessionRequest`; caller via `UUID.fromString(auth.getName())`; map request → `CommitPayload`; delegate to `CommitSessionUseCase`; return `200` `ApiResponse` (role/draft/business rules enforced in the service).
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/GlobalExceptionHandler.java` (**edit**) — add `@ExceptionHandler(CommitValidationException.class)` → 422 `ApiError.of(ex.code(), ex.getMessage())` (mirrors the `RefreshTokenException` arm).
- `apps/api/src/test/java/com/bluesteel/adapters/in/web/session/SessionControllerTest.java` (**update**, F2.3.11) — `@WebMvcTest` mocked `CommitSessionUseCase`; assert 200 happy path, 400 on each cross-field violation, 422 with code when the use case throws `CommitValidationException`.

**Scope (out):** Frontend commit flow (F2.11).

**Skills:** `backend-endpoint`, `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-076, D-079, D-081  **Dependencies:** F2.3.11, F2.7.6, F2.8.1, F2.8.7, F2.8.8

---

#### F2.9 — Frontend: Input Mode — session submission + status polling

> **Umbrella task — run the F2.9.N sub-tasks below, not this.**

**Goal:** Session submission form and processing status view. The user submits a summary, sees the pipeline running, and is navigated to the diff review when the session reaches `draft`. The original scope is split across `F2.9-SETUP` (human scaffolding) and the ordered `F2.9.1`–`F2.9.5` sub-tasks.

**Scope (out):** Diff review (F2.10). Commit flow + draft-recovery banner (F2.11) — F2.9 only surfaces the `409` recovery *link*; it does not pre-check for an existing draft on page load. No `FocusedOverlay` is needed: this flow has no contextual action (no confirm/expand/inline-edit) per UX Constitution §4.

**Skills:** `frontend-api-resource`, `frontend-testing`  
**Decisions:** D-049, D-054, D-067, D-076, D-083, D-086, D-087  
**Dependencies:** F1.7, F1.10, F2.3

---

#### F2.9-SETUP — Frontend scaffolding (human runs by hand, once)

> **Human step — not a pipeline sub-task.** Everything else the sub-tasks need already exists from
> `F1.7-SETUP` and F1.7 (the `@/` alias, Tailwind v4 + theme, `shadcn init`, the Vitest setup file,
> `@hookform/resolvers` + `zod`, the `apiClient`, the Zustand stores, `RequireAuth`, `InlineBanner`,
> and the shadcn `button input label form card` primitives). F2.9 adds **only** the one missing
> shadcn primitive.

```bash
cd apps/web
npx shadcn@latest add textarea     # writes apps/web/src/components/ui/textarea.tsx
# VITE_API_BASE_URL is already documented + wired by F1.7.10 — nothing to add here.
```

> **Backend contract (verified against `apps/api` — F2.3.11 `SessionController` + DTOs, the
> `SessionStatus` domain enum, and `GlobalExceptionHandler`). Every response uses the envelope
> `{ data, meta, errors: [{ code, message, field }] }`; request/response keys are camelCase (D-076):**
> - `POST /api/v1/campaigns/{campaignId}/sessions` body `{ summaryText }` (`@NotBlank`) → **202** `data: { sessionId, status }` (`SessionAcceptedResponse`)
> - `GET  /api/v1/campaigns/{campaignId}/sessions/{sessionId}/status` → `data: { sessionId, status, failureReason, message }` (`SessionStatusResponse`; `failureReason`/`message` nullable)
> - `DELETE /api/v1/campaigns/{campaignId}/sessions/{sessionId}` exists (discard) but belongs to **F2.11**, not F2.9.
>
> **`status` is the `SessionStatus` Java enum serialized by Jackson's default (enum *name*) — it is
> UPPERCASE on the wire: `'PENDING' | 'PROCESSING' | 'DRAFT' | 'COMMITTED' | 'FAILED' | 'DISCARDED'`.**
> There is no `@JsonValue`/global lowercasing (confirmed: `HealthResponse` already emits `'UP'|'DEGRADED'`
> and F1.7.1 mirrors it uppercase). ⚠️ The `frontend-api-resource` skill's example uses lowercase
> `'processing'` **and** `summary_text` — **both are stale**; use UPPERCASE statuses and `summaryText`.
>
> **Error envelope reality — there is NO structured slot for `existingSessionId` or `maxTokens`;
> `ApiError` is only `{ code, message, field }`:**
> - `400` field-blank summary → `{ code: 'VALIDATION_ERROR', field: 'summaryText' }` (Bean Validation).
> - `400` oversize → `{ code: 'SUMMARY_TOO_LARGE', message }` — the token limit is embedded in `message` text only; surface the message verbatim (no structured `maxTokens`).
> - `409` active draft → `{ code: 'ACTIVE_SESSION_EXISTS', message }` — `existingSessionId` (UUID) is embedded in `message` text only; the client extracts it via regex to build the resume link.
> - F1.7.3's `apiClient` parses the envelope and **throws on `errors[]`**; the thrown error exposes the parsed `ApiError[]` (+ HTTP `status`). Reference F1.7.3's actual exported error type when narrowing — do not invent a new one.

---

#### F2.9.1 — Session submit + status TypeScript types

**Goal:** Hand-written TypeScript mirrors of the F2.3.11 session DTOs so every later sub-task imports real, compiling symbols. No runtime logic.

**Scope (in):**
- `apps/web/src/types/session.ts` — `SessionStatus` (`'PENDING' | 'PROCESSING' | 'DRAFT' | 'COMMITTED' | 'FAILED' | 'DISCARDED'` — UPPERCASE, mirrors the Java enum name); `SubmitSessionRequest` (`{ summaryText: string }`); `SessionAcceptedResponse` (`{ sessionId: string; status: SessionStatus }`); `SessionStatusResponse` (`{ sessionId: string; status: SessionStatus; failureReason: string | null; message: string | null }` — `failureReason` is a free-form string, not a union: the backend set grows across phases, e.g. `PIPELINE_NOT_IMPLEMENTED` today, later `PIPELINE_TIMEOUT`/extraction reasons).

**Scope (out):** No fetch logic, hooks, or components (F2.9.2+). Diff/commit types (F2.10/F2.11). No runtime test — `npm run type-check` is the verification for this types-only sub-task.

**Skills:** `frontend-api-resource`  **Decisions:** D-076  **Dependencies:** F2.9-SETUP, F1.7.1

---

#### F2.9.2 — sessions API client + submit/status-polling hooks

**Goal:** Typed fetch functions + TanStack Query v5 hooks for session submit and status polling, plus a pure helper that extracts `existingSessionId` from the `409` error message (the envelope carries no structured field for it).

**Scope (in):**
- `apps/web/src/api/sessions.ts` (+ `sessions.test.ts`) — `sessionKeys` query-key factory; `submitSession(campaignId, body: SubmitSessionRequest)` → `POST .../sessions` (sends camelCase `{ summaryText }`); `getSessionStatus(campaignId, sessionId)` → `GET .../{sid}/status`; hook `useSubmitSession(campaignId)` (mutation; on success invalidate `sessionKeys.all(campaignId)`); hook `useSessionStatus(campaignId, sessionId, enabled)` with `refetchInterval: (query) => query.state.data?.status === 'PROCESSING' ? 2000 : false`; exported helper `extractExistingSessionId(error): string | null` (regex-matches a UUID in an `ACTIVE_SESSION_EXISTS` message; null otherwise). All requests go through `apiClient` (F1.7.3) — never raw `fetch`.
- Tests mock `apiClient`; cover submit success, the `refetchInterval` predicate returning `2000` for `PROCESSING` and `false` for `DRAFT`/`FAILED`, and `extractExistingSessionId` (UUID present / absent).

**Scope (out):** UI/components (F2.9.3/F2.9.4). Diff + commit + discard clients (F2.10/F2.11). The `apiClient` wrapper itself (F1.7.3).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-054, D-076  **Dependencies:** F2.9.1, F1.7.3

---

#### F2.9.3 — ProcessingStatusView (poll → skeleton → draft/failed)

**Goal:** Given a campaign + session id, poll status and represent each state per the UX Constitution: a DTO-derived **skeleton** while `PROCESSING` (no content spinner, D-086), navigate to the diff route on `DRAFT`, and surface a `FAILED` session's `failureReason` + `message` via an error `InlineBanner`.

**Scope (in):**
- `apps/web/src/features/input/ProcessingStatusView.tsx` (+ `ProcessingStatusView.test.tsx`, incl. axe assertion) — props `{ campaignId: string; sessionId: string }`; uses `useSessionStatus`; `PROCESSING` (and initial load) → `animate-pulse` skeleton blocks matching the eventual review header (per §5 skeleton classes — `h-4`/`h-3` `bg-slate-200`), with an `aria-live="polite"` "Processing your session…" status line; `DRAFT` → `useNavigate` to `/campaigns/{campaignId}/sessions/{sessionId}/diff`; `FAILED` → `InlineBanner` variant `error` showing `failureReason` + `message`; fetch error → `InlineBanner` error.

**Scope (out):** The submit form (F2.9.4). The diff page itself (F2.10) — this only navigates to its route. Route registration (F2.9.5).

**Skills:** `frontend-api-resource`, `ux-skeleton-crafting`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-086, D-087  **Dependencies:** F2.9.1, F2.9.2, F1.7.6

---

#### F2.9.4 — SubmitSessionPage form (role guard + error states)

**Goal:** Summary-submission form (React Hook Form + shadcn `Form`/`Textarea`/`Button` in a `rounded-2xl` card) with a `player` role guard; on successful submit, hand off to `ProcessingStatusView` for the returned session. Map the verified `400`/`409` errors to field errors and `InlineBanner`s.

**Scope (in):**
- `apps/web/src/features/input/SubmitSessionPage.tsx` (+ `SubmitSessionPage.test.tsx`, incl. axe assertion) — reads `campaignId` via `useParams`; role guard reads `useCampaignStore(s => s.activeRole)` (populated by `CampaignContextGuard`, F1.10.3 — this route nests under it per F2.9.5) and `<Navigate to="/campaigns/{campaignId}" replace>`-redirects when `activeRole === 'player'`; RHF form over `{ summaryText }` with client `required` validation; submit via `useSubmitSession`; submit `Button` `disabled` + in-button `Loader2` while `isPending` (the one place a spinner is allowed, §5); on success store the returned `sessionId` in local `useState` and render `<ProcessingStatusView campaignId sessionId />` in place of the form; on error narrow the thrown `ApiError[]`: `VALIDATION_ERROR` → `setError('summaryText', …)`, `SUMMARY_TOO_LARGE` → error `InlineBanner` (render `message` verbatim), `ACTIVE_SESSION_EXISTS` → warning `InlineBanner` with a "Resume your unfinished review" link to `/campaigns/{campaignId}/sessions/{id}/diff` built from `extractExistingSessionId(error)`.

**Scope (out):** Route registration (F2.9.5). Pre-load draft-recovery check + discard (F2.11). A reusable `RequireRole` guard (deferred until ≥2 features need it — inline here per scope). Populating `activeRole` (done by `CampaignContextGuard`, F1.10.3).

**Skills:** `react-hook-form`, `frontend-api-resource`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-054, D-067, D-076, D-083, D-087  **Dependencies:** F2.9-SETUP, F2.9.1, F2.9.2, F2.9.3, F1.10.3, F1.7.2, F1.7.6

---

#### F2.9.5 — Register `/sessions/new` route behind RequireAuth

**Goal:** Wire the submission page into the app router so an authenticated `gm`/`editor` can reach it, with the campaign role context loaded.

**Scope (in):**
- `apps/web/src/main.tsx` (**edit**, F1.10.6) — add `sessions/new` as a child route of the `/campaigns/:campaignId` subtree, i.e. **inside the `CampaignContextGuard`** (F1.10.3) so `activeRole` is populated before `SubmitSessionPage`'s `player` guard runs. (`RequireAuth` already wraps the subtree, F1.7.5.) No new providers.

**Scope (out):** Diff/commit routes (F2.10/F2.11). The sidebar Input Mode nav item (F1.11/`ux-navigation-logic`). Role gating beyond auth (handled inside the page, F2.9.4). The campaign subtree + guard themselves (F1.10.6).

**Skills:** `frontend-api-resource`, `auth`  **Decisions:** D-087  **Dependencies:** F2.9.4, F1.10.6, F1.7.5

---

#### F2.10 — Frontend: Input Mode — diff review screen

> **Umbrella task — run the F2.10.N sub-tasks below, not this.**

**Goal:** The trust boundary between AI extraction and world state: render the four card types, force UNCERTAIN resolution, surface (acknowledgeable) conflicts, and hold all per-card decisions in client state. The Commit **button disabled state** is the primary guard for D-042 (backend `422` is defence in depth). The original scope is split across `F2.10-SETUP` (human scaffolding) and the ordered `F2.10.1`–`F2.10.12` sub-tasks.

**Scope (out):** The commit API call + `CommitPayload` assembly/types + draft-recovery banner + discard (all F2.11 — F2.10 builds the decision *state* the F2.11 payload builder consumes, and a `CommitButton` whose `onCommit` is wired in F2.11). "Propose a change" affordance is Exploration Mode (Phase 4, D-012).

**Skills:** `frontend-diff-review`, `frontend-testing`  
**Decisions:** D-004, D-005, D-006, D-007, D-033, D-042, D-076, D-082, D-086, D-087  
**Dependencies:** F1.7, F2.7, F2.9

---

#### F2.10-SETUP — Frontend scaffolding (human runs by hand, once)

> **Human step — not a pipeline sub-task.** Everything else already exists from `F1.7-SETUP`/F1.7
> (`@/` alias, Tailwind v4 + theme, `shadcn init`, Vitest setup, `apiClient`, Zustand stores,
> `RequireAuth`, `InlineBanner`, `@hookform/resolvers` + `zod`, and the `button input label form card`
> primitives) and F2.9 (the `textarea` primitive, `src/types/session.ts`, `src/api/sessions.ts` +
> `sessionKeys`, the `/sessions/new` route). F2.10 adds **only** the missing shadcn primitives.

```bash
cd apps/web
npx shadcn@latest add badge checkbox radio-group
#   -> writes src/components/ui/{badge,checkbox,radio-group}.tsx
#      (radio-group pulls @radix-ui/react-radio-group; checkbox pulls @radix-ui/react-checkbox)
```

> **Backend contract (verified against ARCHITECTURE.md §7.6 + the F2.7 Java records — `DiffCard`
> sealed interface, `ExistingEntityCard`/`NewEntityCard`/`UncertainEntityCard`, `ConflictCard`,
> `DiffPayload`). Returned by `GET /api/v1/campaigns/{campaignId}/sessions/{sessionId}/diff` as
> `ApiResponse<DiffPayload>` (200). The payload is READ-ONLY from the client — it is never POSTed
> back (the commit payload is a separate derived shape, F2.11). All keys are camelCase (D-076):**
> - `DiffPayload` = `{ narrativeSummaryHeader: string, actors: DiffCard[], spaces: DiffCard[], events: DiffCard[], relations: DiffCard[], detectedConflicts: ConflictCard[] }`
> - `DiffCard` is a union discriminated by **`cardType`** (`'EXISTING' | 'NEW' | 'UNCERTAIN'` — UPPERCASE, the Jackson `@JsonTypeInfo` name):
>   - `EXISTING`: `{ cardId, cardType: 'EXISTING', entityId, entityType, name, changedFields: Record<string, unknown> }` (delta only, D-006)
>   - `NEW`: `{ cardId, cardType: 'NEW', entityType, name, fullProfile: Record<string, unknown> }` (full profile, D-007)
>   - `UNCERTAIN`: `{ cardId, cardType: 'UNCERTAIN', entityType, extractedMention, candidateEntityId, candidateEntityName }` (D-042)
>   - `ConflictCard` is **NOT** in the `DiffCard` union (it has no `cardType`): `{ conflictId, entityId, entityType, description, extractedFact, existingFact }` (D-033)
> - **`entityType` is LOWERCASE on the wire:** `'actor' | 'space' | 'event' | 'relation'` (note: this differs from `SessionStatus`/`cardType`, which are UPPERCASE — §7.6 pins these as lowercase).
> - Read access requires `gm|editor` and `status === 'draft'`; outside `draft` (or not found) the endpoint returns **404** (`SESSION_NOT_FOUND`) — there is no separate "no draft" code.
>
> **File-location reconciliation:** §7.6 and the `frontend-diff-review` skill say diff types live in
> `src/types/sessions.ts` (plural). The repo standardized on **singular** `src/types/session.ts`
> (created by F2.9.1, matching `types/auth.ts`/`types/health.ts`). Extend that existing file — do
> **not** create a second `sessions.ts`. The API client file is `src/api/sessions.ts` (plural,
> created by F2.9.2) — extend it. Diff **cards** are used only by the input feature, so they live in
> `src/features/input/components/` (per the "feature-scoped until shared" rule), **not**
> `components/domain/`; only `FocusedOverlay` (reused app-wide) is a `components/domain/` primitive.

---

#### F2.10.1 — DiffPayload read TypeScript types (§7.6)

**Goal:** Hand-written mirrors of the F2.7 diff records so every later sub-task imports real, compiling, discriminated symbols. Read shapes only — the `CommitPayload` wire types are F2.11.

**Scope (in):**
- `apps/web/src/types/session.ts` (**extend**, F2.9.1) — `CardType` (`'EXISTING' | 'NEW' | 'UNCERTAIN'`); `EntityType` (`'actor' | 'space' | 'event' | 'relation'` — lowercase per §7.6); `ExistingDiffCard`, `NewDiffCard`, `UncertainDiffCard` (exact §7.6 fields above); `DiffCard = ExistingDiffCard | NewDiffCard | UncertainDiffCard` (discriminated on `cardType`); `ConflictCard` (separate — no `cardType`); `DiffPayload` aggregate. `changedFields`/`fullProfile` typed `Record<string, unknown>` (no `any`).

**Scope (out):** Fetch hook (F2.10.2); FE decision-state types (`CardDecision`/`UncertainResolution`, F2.10.4); `CommitPayload` wire types + `buildCommitPayload` (F2.11). No runtime test — `npm run type-check` verifies this types-only sub-task.

**Skills:** `frontend-diff-review`, `frontend-api-resource`  **Decisions:** D-004, D-006, D-007, D-033, D-042, D-076  **Dependencies:** F2.10-SETUP, F2.9.1

---

#### F2.10.2 — useSessionDiff query hook

**Goal:** Typed fetch function + TanStack Query hook for the draft diff, extending the existing sessions client.

**Scope (in):**
- `apps/web/src/api/sessions.ts` (**extend**, F2.9.2) + `sessions.test.ts` (**extend**) — add `sessionKeys.diff(campaignId, sessionId)` to the existing key factory; `getSessionDiff(campaignId, sessionId): Promise<DiffPayload>` → `GET .../{sid}/diff` via `apiClient`; hook `useSessionDiff(campaignId, sessionId, enabled?)` (`useQuery`, no polling — diff is fetched once in `draft`). Tests mock `apiClient`; assert the URL + that the parsed `DiffPayload` is returned, and the `404` error path propagates.

**Scope (out):** Components/state (F2.10.3+). Commit/discard mutations (F2.11). The status-poll hook (already F2.9.2).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-076  **Dependencies:** F2.10.1, F2.9.2, F1.7.3

---

#### F2.10.3 — FocusedOverlay primitive + useEscapeKey (no-modal)

**Goal:** The reusable app-wide overlay primitive that replaces all modals/dialogs (D-082, UX Constitution §4). Built here because F2.10's card-edit flow is the first contextual action; reused later (annotations, confirms).

**Scope (in):**
- `apps/web/src/hooks/useEscapeKey.ts` (+ `useEscapeKey.test.ts`) — `useEscapeKey(handler)` document-level `keydown` listener, cleaned up on unmount.
- `apps/web/src/components/domain/FocusedOverlay.tsx` (+ `FocusedOverlay.test.tsx`, incl. axe assertion) — props `{ open, onClose, children, className? }`; backdrop `fixed inset-0 z-40 bg-slate-900/30 backdrop-blur-sm` with `onClick={onClose}` + `aria-hidden`; focused element `z-50 shadow-xl ring-2 ring-blue-500/50` anchored in flow (never viewport-centred); calls `useEscapeKey(onClose)`; first focusable child `autoFocus`. Test covers ESC + backdrop-click close and that `open=false` renders nothing.

**Scope (out):** Any feature usage of the overlay (the edit overlay is F2.10.8). shadcn `Dialog`/`AlertDialog` are forbidden (D-082).

**Skills:** `ux-focused-overlay`, `frontend-testing`  **Decisions:** D-082, D-087  **Dependencies:** F1.7-SETUP

---

#### F2.10.4 — useDiffState reducer hook (decisions / resolutions / acks)

**Goal:** The single source of truth for all per-card user decisions, with derived unresolved counts that drive the commit guard. Pure logic — the most heavily unit-tested piece.

**Scope (in):**
- `apps/web/src/features/input/hooks/useDiffState.ts` (+ `useDiffState.test.ts`) — exports the FE decision types `CardDecision` (`{action:'accept'} | {action:'edit'; editedFields: Record<string,unknown>} | {action:'delete'}`), `UncertainResolution` (`{cardId; resolution:'MATCH'; matchedEntityId:string} | {cardId; resolution:'NEW'; matchedEntityId:null}`), and the `isEdit` type guard; a `useReducer`-backed hook exposing `decisions: Map<string,CardDecision>`, `uncertainResolutions: Map<string,UncertainResolution>`, `acknowledgedConflicts: Set<string>`, the mutators `setDecision`/`resolveUncertain`/`acknowledgeConflict`, and derived `unresolvedUncertainCount` (UNCERTAIN cards without a resolution) + `unacknowledgedConflictCount` (conflicts not yet acknowledged). Initialized from a `DiffPayload`; non-UNCERTAIN cards default to `accept`. Tests cover defaulting, each mutator, and both derived counts reaching zero.

**Scope (out):** The wire `CommitPayload` shape + `buildCommitPayload` (F2.11 — consumes this state). Card UI (F2.10.5–F2.10.8). Page wiring (F2.10.11).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-033, D-042, D-053  **Dependencies:** F2.10.1

---

#### F2.10.5 — DeltaCard + NewEntityCard (entity decision cards)

**Goal:** The two non-UNCERTAIN entity cards. Both expose Accept / Edit / Delete; they differ only in the field block — `DeltaCard` shows `changedFields` only (D-006), `NewEntityCard` shows the full `fullProfile` (D-007). No "add" affordance (D-053).

**Scope (in):**
- `apps/web/src/features/input/components/DeltaCard.tsx` (+ `DeltaCard.test.tsx`, incl. axe) — props `{ card: ExistingDiffCard; decision: CardDecision; onSetDecision: (d: CardDecision) => void; onEdit: () => void }`; renders `name`, the `changedFields` delta, and Accept/Delete buttons (reflecting current `decision`) + an Edit button that calls `onEdit` (overlay is F2.10.8); `rounded-2xl` card.
- `apps/web/src/features/input/components/NewEntityCard.tsx` (+ `NewEntityCard.test.tsx`, incl. axe) — same action contract; renders the full `fullProfile`.

**Scope (out):** The edit overlay itself (F2.10.8 — these only emit `onEdit`). UNCERTAIN/conflict cards (F2.10.6/F2.10.7). Layout/sections (F2.10.9).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-006, D-007, D-053  **Dependencies:** F2.10-SETUP, F2.10.1, F2.10.4

---

#### F2.10.6 — UncertainCard (inline MATCH/NEW resolution)

**Goal:** Force the binary resolution of an UNCERTAIN card — "Same entity" (`MATCH` → `candidateEntityId`) or "Different entity" (`NEW`). No defer/skip option (D-042).

**Scope (in):**
- `apps/web/src/features/input/components/UncertainCard.tsx` (+ `UncertainCard.test.tsx`, incl. axe) — props `{ card: UncertainDiffCard; resolution: UncertainResolution | undefined; onResolve: (r: UncertainResolution) => void }`; `Badge` "Requires Resolution"; shows `extractedMention` + `candidateEntityName`; a keyboard-navigable `RadioGroup` (Same entity / Different entity) that calls `onResolve` with the correct `matchedEntityId` (candidate id for MATCH, `null` for NEW); visible focus indicators.

**Scope (out):** The commit guard that counts unresolved cards (F2.10.4/F2.10.10). Other card types.

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-042  **Dependencies:** F2.10-SETUP, F2.10.1, F2.10.4

---

#### F2.10.7 — ConflictWarningCard (acknowledge, non-blocking)

**Goal:** Surface a detected contradiction the user must acknowledge before commit, without blocking edits (non-blocking, D-033).

**Scope (in):**
- `apps/web/src/features/input/components/ConflictWarningCard.tsx` (+ `ConflictWarningCard.test.tsx`, incl. axe) — props `{ conflict: ConflictCard; acknowledged: boolean; onAcknowledge: (conflictId: string) => void }`; `role="alert"` content showing `description`, `extractedFact` vs `existingFact`; an acknowledgment `Checkbox` (amber/warning styling) that calls `onAcknowledge(conflict.conflictId)`.

**Scope (out):** The unacknowledged-count guard (F2.10.4/F2.10.10). Entity cards.

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-033  **Dependencies:** F2.10-SETUP, F2.10.1, F2.10.4

---

#### F2.10.8 — EditCardOverlay (FocusedOverlay-based field edit)

**Goal:** The contextual "Edit" action for a Delta/New card — edit the relevant fields inside a `FocusedOverlay` (no modal, D-082) and commit the change as an `edit` decision. On cancel, revert to `accept`.

**Scope (in):**
- `apps/web/src/features/input/components/EditCardOverlay.tsx` (+ `EditCardOverlay.test.tsx`, incl. axe) — props `{ card: ExistingDiffCard | NewDiffCard; open: boolean; onClose: () => void; onSave: (editedFields: Record<string, unknown>) => void }`; wraps `FocusedOverlay`; React Hook Form over the editable fields (`changedFields` for Delta, `fullProfile` for New — only those fields, not a full entity form); Save calls `onSave(editedFields)` (the page maps this to `setDecision({action:'edit', editedFields})`); Cancel/ESC/backdrop closes without saving.

**Scope (out):** `FocusedOverlay` itself (F2.10.3). The `setDecision` wiring + revert-to-accept on cancel (F2.10.11 page). Validation beyond required fields.

**Skills:** `frontend-diff-review`, `ux-focused-overlay`, `react-hook-form`, `frontend-testing`  **Decisions:** D-006, D-007, D-082, D-087  **Dependencies:** F2.10.1, F2.10.3, F2.10.4

---

#### F2.10.9 — NarrativeSummaryHeader + DiffCategorySection

**Goal:** The presentational scaffolding around the cards: the AI session summary shown first (D-005) and the per-category (Actors/Spaces/Events/Relations) collapsible section wrapper.

**Scope (in):**
- `apps/web/src/features/input/components/NarrativeSummaryHeader.tsx` (+ `NarrativeSummaryHeader.test.tsx`, incl. axe) — props `{ summary: string }`; display-only 1–3 sentence header; never `dangerouslySetInnerHTML` (LLM text is plain).
- `apps/web/src/features/input/components/DiffCategorySection.tsx` (+ `DiffCategorySection.test.tsx`, incl. axe) — props `{ title: string; count: number; children: React.ReactNode }`; `<section>` with `aria-labelledby` heading; local expand/collapse (a section is presentational — not global UI state).

**Scope (out):** The cards rendered inside (F2.10.5–F2.10.7). Page composition (F2.10.11).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-005, D-087  **Dependencies:** F2.10-SETUP, F2.10.1

---

#### F2.10.10 — CommitButton (controlled disabled guard)

**Goal:** The D-042 primary guard as a controlled component: disabled while any UNCERTAIN is unresolved or any conflict unacknowledged (or a commit is in flight), with the "N items require your decision" progress text.

**Scope (in):**
- `apps/web/src/features/input/components/CommitButton.tsx` (+ `CommitButton.test.tsx`, incl. axe) — props `{ unresolvedUncertainCount: number; unacknowledgedConflictCount: number; isPending: boolean; onCommit: () => void }`; `disabled = unresolvedUncertainCount + unacknowledgedConflictCount > 0 || isPending`; when disabled set `aria-disabled` + descriptive `aria-label`; show "N item(s) require your decision" when the sum > 0; in-button `Loader2` while `isPending` (the only permitted spinner, §5).

**Scope (out):** The actual commit mutation + `onCommit` implementation + `422` handling (all F2.11 — here `onCommit` is just a prop). Count computation (F2.10.4 supplies it).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-042  **Dependencies:** F2.10.4

---

#### F2.10.11 — DiffReviewPage container (fetch + skeleton + assemble)

**Goal:** Compose the whole screen: fetch the diff, drive `useDiffState`, lay out the narrative header + conflict section (top) + per-type category sections of cards + the edit overlay + the commit button. Loading is a DTO-derived skeleton (no spinner, D-086); fetch/404 errors surface via `InlineBanner`.

**Scope (in):**
- `apps/web/src/features/input/DiffReviewPage.tsx` (+ `DiffReviewPage.test.tsx`, incl. axe) — reads `campaignId`/`sessionId` via `useParams`; `useSessionDiff`; `useDiffState(diff)`; while loading render an inline `animate-pulse` skeleton matching the header + a couple of card placeholders (§5 classes); on error/404 render an error `InlineBanner`; renders `NarrativeSummaryHeader`, a `detectedConflicts` section of `ConflictWarningCard`s at the top, then `DiffCategorySection`s mapping each card to `DeltaCard`/`NewEntityCard`/`UncertainCard` by `cardType`; owns the `EditCardOverlay` open state + maps its `onSave` to `setDecision({action:'edit',…})` and Cancel to `accept`; renders `CommitButton` fed by the derived counts with a **placeholder** `onCommit` (real mutation is F2.11).

**Scope (out):** The commit mutation, payload assembly, success/redirect, and `422` defence-in-depth handling (F2.11). Route registration (F2.10.12). Draft-recovery banner (F2.11).

**Skills:** `frontend-diff-review`, `ux-skeleton-crafting`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-004, D-005, D-033, D-042, D-086, D-087  **Dependencies:** F2.10.1, F2.10.2, F2.10.4, F2.10.5, F2.10.6, F2.10.7, F2.10.8, F2.10.9, F2.10.10, F1.7.6

---

#### F2.10.12 — Register `/sessions/:sessionId/diff` route

**Goal:** Wire the diff review page into the router so the F2.9 `ProcessingStatusView` navigation target resolves for an authenticated `gm`/`editor`.

**Scope (in):**
- `apps/web/src/main.tsx` (**edit**, F2.9.5) — add `sessions/:sessionId/diff` as a child route of the `/campaigns/:campaignId` subtree (i.e. **inside `CampaignContextGuard`**, F1.10.3), alongside the `sessions/new` route. `RequireAuth` already wraps the subtree (F1.7.5). No new providers.

**Scope (out):** Commit/discard routes + draft-recovery entry (F2.11). Sidebar nav (F1.11). Page-level role gating (the diff endpoint already enforces `gm|editor` server-side; F2.11 owns any client role affordance). The campaign subtree + guard themselves (F1.10.6).

**Skills:** `frontend-api-resource`, `auth`  **Decisions:** D-087  **Dependencies:** F2.10.11, F1.10.6, F1.7.5, F2.9.5

---

#### F2.11 — Frontend: Input Mode — commit flow + draft recovery

> **Umbrella task — run the F2.11.N sub-tasks below, not this.**

**Goal:** Turn the reviewed diff into a committed world state: assemble the `CommitPayload` from `useDiffState`, POST it, **navigate to the campaign home** on success (F1.10.5), handle the `422` defence-in-depth codes as UI bugs, and provide GM discard (the unblock path). The original scope is split across the ordered `F2.11.1`–`F2.11.6` sub-tasks.

> **No SETUP required.** Every primitive is already present: F1.7 (`apiClient`, `InlineBanner`, Zustand `campaignStore.activeRole`, `Button`/`Loader2`), F2.10 (`FocusedOverlay`, `useDiffState` + its `CardDecision`/`UncertainResolution` types + `isEdit`, `DiffReviewPage`, `src/api/sessions.ts`, `src/types/session.ts`). The discard confirmation is a `FocusedOverlay` (D-082) — **not** a shadcn `Dialog`/`AlertDialog` — so no new shadcn component is installed.

> **Draft-recovery reconciliation (important — avoids a duplicate + an invented endpoint):** the
> *reactive* recovery link is **already delivered by F2.9.4** — a `409 ACTIVE_SESSION_EXISTS` from
> `useSubmitSession` renders a "Resume your unfinished review" banner via `extractExistingSessionId`.
> A *proactive* "check for a draft on page load" would need `GET /api/v1/campaigns/{id}/sessions`
> (the §7.6 list endpoint), which is **not** in the backend decomposition (F2.3 exposed only
> `POST`, `/{sid}/status`, `/{sid}/diff`, `DELETE /{sid}`). So v1 draft recovery = the F2.9.4
> reactive link (resume) **plus** the F2.11 GM discard (unblock to resubmit). F2.11 does **not**
> re-implement the banner or assume a list endpoint.

**Scope (out):** Proactive list-based draft detection (no `GET /sessions` endpoint — see above). The reactive resume banner (F2.9.4). World state exploration + world-state cache keys (Phase 4). The campaign-home page itself (F1.10.5 — **commit** navigates there; **discard** returns to the campaign Input entry `/sessions/new`).

**Skills:** `frontend-diff-review`, `frontend-testing`  
**Decisions:** D-002, D-033, D-042, D-053, D-054, D-076, D-082, D-083, D-087  
**Dependencies:** F2.10, F2.8, F1.10

---

#### F2.11.1 — CommitPayload wire TypeScript types (§7.6)

**Goal:** Hand-written mirrors of the F2.8 `CommitSessionRequest`/`CommitPayload` shape so the builder and client compile against the exact wire contract. Write shapes only.

**Scope (in):**
- `apps/web/src/types/session.ts` (**extend**, F2.10.1) — `CardDecisionPayload` (`{ cardId: string; action: 'accept' | 'edit' | 'delete'; editedFields?: Record<string, unknown> }` — `editedFields` present+non-empty only for `edit`); `UncertainResolutionPayload` (`{ cardId: string; resolution: 'MATCH' | 'NEW'; matchedEntityId: string | null }`); `AcknowledgedConflictPayload` (`{ conflictId: string }`); `CommitPayload` (`{ cardDecisions: CardDecisionPayload[]; uncertainResolutions: UncertainResolutionPayload[]; acknowledgedConflicts: AcknowledgedConflictPayload[] }`). All camelCase (D-076); no `any`.

**Scope (out):** The builder logic (F2.11.2). The mutation/client (F2.11.3). The read `DiffPayload` types (already F2.10.1). No runtime test — `npm run type-check` verifies this types-only sub-task.

**Skills:** `frontend-diff-review`, `frontend-api-resource`  **Decisions:** D-053, D-076  **Dependencies:** F2.10.1

---

#### F2.11.2 — buildCommitPayload pure builder (state → payload)

**Goal:** The pure function translating a `DiffPayload` + the live `useDiffState` into a §7.6-valid `CommitPayload`. This is the correctness core of commit — exhaustively unit-tested against the server's validation rules.

**Scope (in):**
- `apps/web/src/features/input/hooks/useCommitPayload.ts` (+ `useCommitPayload.test.ts`) — `buildCommitPayload(diff: DiffPayload, state: DiffState): CommitPayload`: emit a `cardDecisions` entry for **every** non-UNCERTAIN card (default `accept` when untouched — D-080); include `editedFields` **only** when `action === 'edit'` (via the `isEdit` guard); map every `uncertainResolutions` entry (`matchedEntityId` = candidate id for MATCH, `null` for NEW); map every acknowledged `conflictId`; never emit an `add` action (D-053). Tests assert: all non-UNCERTAIN cards present, edit-only `editedFields`, MATCH/NEW `matchedEntityId`, and that `acknowledgedConflicts` covers every detected conflict.

**Scope (out):** The wire types (F2.11.1). The mutation call + error handling (F2.11.3/F2.11.5). The decision *state* itself (F2.10.4).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-033, D-042, D-053, D-076, D-080  **Dependencies:** F2.11.1, F2.10.1, F2.10.4

---

#### F2.11.3 — commit + discard API client + mutation hooks

**Goal:** Typed fetch functions + TanStack Query mutation hooks for commit and discard, extending the existing sessions client.

**Scope (in):**
- `apps/web/src/api/sessions.ts` (**extend**, F2.9.2/F2.10.2) + `sessions.test.ts` (**extend**) — `commitSession(campaignId, sessionId, payload: CommitPayload)` → `POST .../{sid}/commit` (200; treat `data` as void); `discardSession(campaignId, sessionId)` → `DELETE .../{sid}` (200); hooks `useCommitSession(campaignId, sessionId)` and `useDiscardSession(campaignId, sessionId)`, both invalidating `sessionKeys.all(campaignId)` on success. All via `apiClient`. Tests mock `apiClient`; assert the URLs/bodies and that a thrown `ApiError[]` (e.g. `422 UNCERTAIN_ENTITIES_PRESENT`, `409 INVALID_SESSION_STATE`) propagates to the caller.

**Scope (out):** Payload assembly (F2.11.2). Page wiring + 422-as-UI-bug presentation (F2.11.5/F2.11.6). The confirmation overlay (F2.11.4).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-002, D-054, D-076  **Dependencies:** F2.11.1, F2.9.2, F2.10.2, F1.7.3

---

#### F2.11.4 — DiscardConfirmOverlay (FocusedOverlay confirm)

**Goal:** The discard confirmation as a `FocusedOverlay` (modals forbidden, D-082), not a shadcn dialog. A destructive-action confirm with an explicit cancel.

**Scope (in):**
- `apps/web/src/features/input/components/DiscardConfirmOverlay.tsx` (+ `DiscardConfirmOverlay.test.tsx`, incl. axe) — props `{ open: boolean; onConfirm: () => void; onClose: () => void; isPending: boolean }`; wraps `FocusedOverlay`; warning copy ("Discard this draft? This cannot be undone."); a destructive confirm `Button` (`bg-red-600`, in-button `Loader2` while `isPending`) + a Cancel button; ESC/backdrop close via the overlay.

**Scope (out):** The discard mutation + role gating + navigation (F2.11.6). `FocusedOverlay` itself (F2.10.3).

**Skills:** `ux-focused-overlay`, `frontend-diff-review`, `frontend-testing`  **Decisions:** D-082, D-087  **Dependencies:** F2.10.3

---

#### F2.11.5 — DiffReviewPage commit wiring (+ 422 handling)

**Goal:** Wire the real commit into the review page: build the payload, call the mutation, navigate to the campaign home on success, and treat the defence-in-depth `422`s as UI bugs (the disabled button should have prevented them).

**Scope (in):**
- `apps/web/src/features/input/DiffReviewPage.tsx` (**edit**, F2.10.11) + `DiffReviewPage.test.tsx` (**extend**) — replace the placeholder `onCommit` with: `buildCommitPayload(diff, state)` → `useCommitSession(campaignId, sessionId)`; pass `isPending` to `CommitButton`; **on success `navigate('/campaigns/{campaignId}', { replace: true })` (the campaign home, F1.10.5)** — the mutation invalidates `sessionKeys.all(campaignId)`; also invalidate `campaignKeys.detail(campaignId)` (F1.10.2) so the home reflects the commit. Navigating away is required because the session leaves `draft` and `GET .../diff` then `404`s — the page must not refetch it. On error narrow the `ApiError[]` — `UNCERTAIN_ENTITIES_PRESENT`/`CONFLICTS_NOT_ACKNOWLEDGED`/other commit codes → `console.error` (UI-bug signal) + a generic error `InlineBanner` ("Something went wrong committing this review"); do **not** silently re-enable the button (it stays count-driven). Test the success navigation (to campaign home) and the 422-as-banner path.

**Scope (out):** Discard button/overlay wiring (F2.11.6). Payload builder internals (F2.11.2). The mutation hook (F2.11.3). The campaign-home page (F1.10.5).

**Skills:** `frontend-diff-review`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-002, D-042, D-076, D-083, D-087  **Dependencies:** F2.11.2, F2.11.3, F2.10.11, F1.10.2, F1.10.5, F1.7.6

---

#### F2.11.6 — DiffReviewPage GM-only discard wiring

**Goal:** Add the GM-only discard affordance to the review page, gated on campaign role and confirmed via the overlay; on success the campaign is unblocked and the user returns to submit a new session.

**Scope (in):**
- `apps/web/src/features/input/DiffReviewPage.tsx` (**edit**, F2.11.5) + `DiffReviewPage.test.tsx` (**extend**) — render a "Discard draft" button **only** when `useCampaignStore(s => s.activeRole) === 'gm'` (D-054; the backend also enforces GM + draft, returning 403/409); clicking opens `DiscardConfirmOverlay`; confirm → `useDiscardSession(campaignId, sessionId)` (pass `isPending` to the overlay); on success `navigate` to `/campaigns/{campaignId}/sessions/new`; on `409 INVALID_SESSION_STATE`/`403` → error `InlineBanner`. Test: button hidden for non-GM, shown for GM, and confirm→discard→navigate.

**Scope (out):** Commit wiring (F2.11.5). The overlay component (F2.11.4) and discard hook (F2.11.3). Proactive draft detection (no endpoint — see umbrella).

**Skills:** `frontend-diff-review`, `ux-focused-overlay`, `ux-inline-feedback`, `auth`, `frontend-testing`  **Decisions:** D-054, D-082, D-083, D-087  **Dependencies:** F2.11.4, F2.11.3, F2.11.5, F1.7.2, F1.7.6

---

#### F2.12 — Local LLM via Ollama (offline real pipeline)

> **Umbrella task — run the F2.12.N sub-tasks below, not this.**

**Goal:** Add a third provider selection — the `llm-ollama` Spring profile — that runs the entire ingestion + Query Mode pipeline against local models served by Ollama, fully offline with no API keys and zero cost, including **real semantic Query Mode** (real local embeddings in pgvector). Prod stays Gemini@1536 (outputDimensionality=1536); local becomes Ollama (`bge-m3`@1024 + `qwen2.5:7b`). See D-088.

**Scope (out):** Any domain/application/adapter code change — the `SpringAi*` adapters are already provider-neutral (F2.4–F2.6) and gated `@Profile("llm-real | llm-ollama")`; this task only adds the Ollama starter, profile config, and the profile-selected model beans. Reversing the Gemini@1536 production default (D-088 keeps it).

> **Why small:** provider selection lives at the `ChatModel`/`EmbeddingModel` bean level in `AiConfig`, and the embedding dimension is already a Liquibase parameter (F2.1.4). Ollama is a configuration concern, not new adapters.

#### F2.12-SETUP (human — run by hand before the sub-tasks)

- [ ] `apps/api/pom.xml`: add `<dependency>` `org.springframework.ai:spring-ai-starter-model-ollama` (version managed by the existing Spring AI BOM).
- [ ] Install Ollama (https://ollama.com) and start it (serves `http://localhost:11434`).
- [ ] `ollama pull bge-m3` (embeddings, 1024 dims) and `ollama pull qwen2.5:7b` (chat).
- [ ] (optional) `docker-compose.yml`: add an `ollama` service (`image: ollama/ollama`, port `11434`, named volume) for one-command bring-up.
- [ ] Recreate the local DB when switching dimension: `docker compose down -v` then back up (the `entity_embeddings` column is created at 1024 under this profile).

---

#### F2.12.1 — Ollama profile config + AiConfig model-bean wiring

**Goal:** Add the `llm-ollama` profile config and the profile-selected Ollama `ChatClient` + `EmbeddingModel` beans, so the existing provider-neutral adapters run against Ollama.

**Scope (in):**
- `apps/api/src/main/resources/application-llm-ollama.properties` — all env-overridable with defaults: `spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}`; `spring.ai.ollama.chat.options.model=${OLLAMA_CHAT_MODEL:qwen2.5:7b}`; `spring.ai.ollama.embedding.options.model=${OLLAMA_EMBEDDING_MODEL:bge-m3}`; `spring.liquibase.parameters.embeddingDimension=${EMBEDDING_DIMENSION:1024}`; reuse the `local` datasource.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/AiConfig.java` (extend) — `@Profile("llm-ollama")` `@Bean`s exposing the active `ChatClient` (from `OllamaChatModel`) and `EmbeddingModel` (Ollama); ensure no bean ambiguity with the Gemini auto-config (profile-gate the provider beans so exactly one `ChatModel`/`EmbeddingModel` is active).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/OllamaWiringTest.java` — verifies `AiConfig` produces a `ChatClient` + `EmbeddingModel` under `llm-ollama` using a mocked Ollama model (no live Ollama → CI-safe).

**Scope (out):** Live end-to-end run (F2.12.2). Any adapter logic (already in F2.4–F2.6).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-032, D-040, D-088  **Dependencies:** F2.12-SETUP, F2.2.8, F2.8

---

#### F2.12.2 — Offline pipeline smoke test (env-gated, manual/local)

**Goal:** Prove the full offline pipeline works end-to-end against a live Ollama: submit → extract → resolve → conflict → diff → commit → embed → query, with a real grounded Query Mode answer.

**Scope (in):**
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/OllamaPipelineSmokeIT.java` — `@EnabledIfEnvironmentVariable` (e.g. `RUN_OLLAMA_IT=true`); runs under `local,llm-ollama` against a live Ollama + pgvector Testcontainer; submits a session, commits, then asks a Query Mode question and asserts a non-empty grounded answer with at least one citation. **Disabled by default** (CI has no Ollama) — documents the real offline check.

**Scope (out):** CI wiring — CI stays on mock adapters (`local`). This IT is developer-run only.

**Skills:** `spring-ai-llm-adapter`, `session-ingestion-pipeline`  **Decisions:** D-088  **Dependencies:** F2.12.1

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
