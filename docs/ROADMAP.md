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
| F1.7.1 | Frontend: shared API envelope + auth + health TypeScript types | 🔲 |
| F1.7.2 | Frontend: Zustand auth + campaign stores | 🔲 |
| F1.7.3 | Frontend: base HTTP client with silent-refresh on 401 | 🔲 |
| F1.7.4 | Frontend: auth + user API resource hooks | 🔲 |
| F1.7.5 | Frontend: RequireAuth route guard (+ forced-password-change redirect) | 🔲 |
| F1.7.6 | Frontend: InlineBanner feedback component (no-toast) | 🔲 |
| F1.7.7 | Frontend: LoginPage form | 🔲 |
| F1.7.8 | Frontend: ChangePasswordPage form | 🔲 |
| F1.7.9 | Frontend: /status round-trip health page (skeleton loading) | 🔲 |
| F1.7.10 | Frontend: app wiring (routes + providers) + Vercel config | 🔲 |
| F1.8 | Campaign creation + membership API | 🔲 |
| F1.8.1 | Backend: Campaign + CampaignMember domain + CampaignNotFoundException | 🔲 |
| F1.8.2 | Backend: campaign driven ports + command/read-model records | 🔲 |
| F1.8.3 | Backend: CreateCampaignUseCase + service (admin-only, atomic GM) | 🔲 |
| F1.8.4 | Backend: GetCampaignUseCase + service (member-or-admin) | 🔲 |
| F1.8.5 | Backend: ListCampaignsUseCase + service (caller's campaigns; admin all) | 🔲 |
| F1.8.6 | Backend: campaign persistence adapter (JPA + Testcontainers IT) | 🔲 |
| F1.8.7 | Backend: campaign membership persistence adapter + CampaignMembershipPort | 🔲 |
| F1.8.8 | Backend: CampaignController + DTOs + 404 mapping | 🔲 |
| F1.9 | Campaign-scoped invitation + role enforcement | 🔲 |
| F1.9.1 | Backend: CampaignMember.withRole + membership exceptions (CANNOT_REMOVE_GM, ALREADY_CAMPAIGN_MEMBER) | 🔲 |
| F1.9.2 | Backend: extend CampaignMembershipRepository + adapter (find/delete/existsByRole) | 🔲 |
| F1.9.3 | Backend: TemporaryPasswordGenerator shared component (de-duplicate invite flows) | 🔲 |
| F1.9.4 | Backend: InviteCampaignMemberUseCase + service (GM-only, create-or-add, 409 if member) | 🔲 |
| F1.9.5 | Backend: ChangeMemberRoleUseCase + service (GM-only, GM role protected) | 🔲 |
| F1.9.6 | Backend: RemoveMemberUseCase + service (GM-only, 422 CANNOT_REMOVE_GM) | 🔲 |
| F1.9.7 | Backend: SearchUsersUseCase + service (admin or GM-anywhere; search by email) | 🔲 |
| F1.9.8 | Backend: CampaignMembershipController + DTOs + 409/422 handler mappings | 🔲 |
| F1.9.9 | Backend: UserSearchController + DTO (GET /api/v1/users?email=) | 🔲 |

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
- `apps/web/src/store/authStore.ts` (+ `authStore.test.ts`) — `accessToken: string | null`; `currentUser: CurrentUser | null`; `setAccessToken`, `setCurrentUser`, `logout`
- `apps/web/src/store/campaignStore.ts` (+ `campaignStore.test.ts`) — `activeCampaignId: string | null`; `activeRole: CampaignRole | null` (empty for now); `setCampaign`, `clearCampaign`

**Scope (out):** No fetching, no persistence middleware. `uiStore` (later). Role is never read from the JWT.

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

**Goal:** Email/password form (React Hook Form + shadcn `Form`/`Input`/`Button`, in a `rounded-2xl` card). Submit via `useLogin`; map `400` field errors with `setError`; surface auth failure through `InlineBanner` (error variant); submit button shows in-button `Loader2` while pending. On success redirect to `/change-password` if `forcePasswordChange`, else `/status` (campaign list deferred — F1.8).

**Scope (in):**
- `apps/web/src/features/auth/LoginPage.tsx` (+ `LoginPage.test.tsx`, incl. axe assertion)

**Scope (out):** ChangePasswordPage (F1.7.8). Route registration (F1.7.10).

**Skills:** `react-hook-form`, `frontend-api-resource`, `auth`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-067, D-077, D-083, D-087  **Dependencies:** F1.7-SETUP, F1.7.1, F1.7.2, F1.7.4, F1.7.6

---

#### F1.7.8 — ChangePasswordPage

**Goal:** Forced/voluntary password-change form (RHF + shadcn, card layout). `newPassword` client-validated to min 12 chars (mirrors backend); submit via `useChangePassword`; `InlineBanner` for success/error; in-button `Loader2` while pending. On success clear `forcePasswordChange` in `currentUser` and redirect to `/status`.

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

### Phase 2 — Session Ingestion Pipeline

> **Principle:** Schema first, mocks second, backend pipeline third, frontend last.
> Every pipeline stage is developed TDD under the `local` profile (zero API cost) before the real adapters are wired.
>
> **Note on F2.1 placement:** The world state schema comes first in Phase 2 because entity resolution (F2.5) and conflict detection (F2.6) both require `entity_embeddings` to run integration tests. The original ordering (schema in the middle) created a false dependency.

#### Summary

| # | Feature | Status |
|---|---|---|
| F2.1 | World state + session schema migrations | 🔲 |
| F2.1.1 | Session + narrative-block schema migrations (0006–0007) | 🔲 |
| F2.1.2 | Actor + space versioned-table migrations (0008–0011) | 🔲 |
| F2.1.3 | Event + relation versioned-table migrations (0012–0015) | 🔲 |
| F2.1.4 | Entity-embeddings migration — vector(1536) + IVFFlat (0016) | 🔲 |
| F2.1.5 | Annotations table migration (0017) | 🔲 |
| F2.1.6 | Proposals + proposal-votes schema-only migrations (0018–0019) | 🔲 |
| F2.2 | Mock LLM + Email adapters (local profile) | 🔲 |
| F2.2.1 | AI value model — extraction + shared EntityContext | 🔲 |
| F2.2.2 | AI value model — entity-resolution outcomes | 🔲 |
| F2.2.3 | Narrative-extraction port + mock + llm-real stub | 🔲 |
| F2.2.4 | Entity-resolution port + mock + llm-real stub | 🔲 |
| F2.2.5 | Conflict-detection port + mock + llm-real stub | 🔲 |
| F2.2.6 | Embedding port + mock + llm-real stub | 🔲 |
| F2.2.7 | Query-answering port + mock + llm-real stub | 🔲 |
| F2.2.8 | AiConfig + local-profile mock-adapter wiring IT | 🔲 |
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
| F2.6 | Conflict detection pipeline | 🔲 |
| F2.7 | Diff generation + draft API | 🔲 |
| F2.8 | Commit endpoint | 🔲 |
| F2.9 | Frontend: Input Mode — session submission + status polling | 🔲 |
| F2.10 | Frontend: Input Mode — diff review screen | 🔲 |
| F2.11 | Frontend: Input Mode — commit flow + draft recovery | 🔲 |
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
- `apps/api/src/main/resources/db/changelog/0016_create_entity_embeddings.xml` — UUID PK; `entity_type TEXT` + raw-SQL `CHECK (entity_type IN ('actor','space','event','relation'))`; `entity_id UUID` (polymorphic — no FK); `entity_version_id UUID`; `session_id` FK→sessions; `embedding vector(${embeddingDimension})` NOT NULL (Liquibase parameter — 1536 for OpenAI `text-embedding-3-small` by default, 1024 under the `llm-ollama` profile; D-040, D-088); `content_hash TEXT`; `created_at`; raw-SQL index `USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)`; `<rollback>` for the raw-SQL items.
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

**Scope (out):** Real Anthropic ChatClient logic (F2.4); AiConfig wiring (F2.2.8).

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
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/SpringAiEmbeddingAdapter.java` — `@Component @Profile("llm-real | llm-ollama")`; stub that throws until F2.6 (real impl injects Spring AI `EmbeddingModel` — OpenAI or Ollama per profile).
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/MockEmbeddingAdapterTest.java` — asserts `length == 1536`, index 0 == 1.0f, rest 0.0f.

**Scope (out):** Real OpenAI `EmbeddingModel` call + async post-commit generation (F2.6/D-063).

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
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/AiConfig.java` — `@Configuration` in `adapters.out.ai` (ArchUnit Rule 4). Documents the three-way profile split — mock (`!llm-real & !llm-ollama`) / `llm-real` (Anthropic+OpenAI) / `llm-ollama` (Ollama, F2.12) — and is the home for profile-selected `ChatClient`/`EmbeddingModel` beans; no real beans yet (deferred to F2.4/F2.12 to avoid requiring API keys at local startup).
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
> **No SETUP required** — the spring-ai-bom + Anthropic/OpenAI starters are already in `pom.xml`
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
- `apps/api/src/main/java/com/bluesteel/config/LlmCostLogger.java` — `@Component`; `logLlmCall(String stage, int tokensIn, int tokensOut, java.time.Instant start)`; emits one INFO line via `log.atInfo().addKeyValue("stage" / "tokens_in" / "tokens_out" / "cost_usd" / "duration_ms")`; `session_id`/`user_id` come from MDC (set by the caller, F2.4.5); private `estimateCostUsd` per Claude pricing; never logs raw response content.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/TokenEstimator.java` — `public static int estimate(String text)` (≈ `Math.ceil(len / 4.0)`); input-budget only.
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/TokenBudgetExceededException.java` — `RuntimeException(int estimated, int max)`; thrown before any `ChatClient` call.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/TokenEstimatorTest.java` — empty/short/long boundary cases.

**Scope (out):** The adapter that uses these (F2.4.3); MDC population (F2.4.5); JSON appender config (already in `logback-spring.xml`, D-072).

**Skills:** `spring-ai-llm-adapter`  **Decisions:** D-034, D-072  **Dependencies:** F2.2

---

#### F2.4.2 — AiConfig real ChatClient (llm-real) + provider config

**Goal:** Provide the `llm-real` `ChatClient` bean (Anthropic) the extraction adapter injects, plus the provider properties and the extraction token-budget envelope. The `llm-ollama` `ChatClient` bean is F2.12.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/AiConfig.java` (**edit**, F2.2.8) — add `@Bean @Profile("llm-real")` `ChatClient` built from the auto-configured Anthropic `ChatModel` (`ChatClient.create(chatModel)`); Javadoc notes the adapter is `@Profile("llm-real | llm-ollama")` so exactly one `ChatClient` bean must be active per profile (Ollama's bean is F2.12).
- `apps/api/src/main/resources/application-llm-real.properties` (**new**) — `spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}`, `spring.ai.anthropic.chat.options.model=…`, `…temperature=…`; `spring.ai.openai.api-key=${OPENAI_API_KEY}` (satisfies OpenAI autoconfig under `llm-real`; the real embedding adapter ships later).
- `apps/api/src/main/resources/application.properties` (**edit**) — add `blue-steel.llm.extraction-max-tokens=${BLUE_STEEL_LLM_EXTRACTION_MAX_TOKENS:4000}` (D-034 envelope); `.env.example` (**edit**) — mirror the new var.
- `apps/api/src/test/java/com/bluesteel/adapters/out/ai/AiConfigTest.java` — unit test: the bean method returns a non-null `ChatClient` given a mock `ChatModel` (no Spring context start, no API key).

**Scope (out):** Ollama beans + dimension override (F2.12); the real OpenAI `EmbeddingModel` bean (F2.5/F2.6); adapter logic (F2.4.3).

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

**Goal:** Two-stage entity resolution: pgvector similarity search (Stage 1) + LLM call 2 for borderline cases (Stage 2). Produces MATCH / NEW / UNCERTAIN outcomes (D-041, D-042).

**Scope (in):**

*Application:*
- `EntityResolutionService` (internal): for each `ExtractedMention`; Stage 1: embed mention via `EmbeddingPort` → `EntitySimilaritySearchPort` → if max score < `blue-steel.resolution.similarity-floor` (default 0.75): classify NEW immediately (no LLM call); Stage 2: if score ≥ floor: call `EntityResolutionPort` with mention + top-3 `EntityContext` candidates → MATCH / NEW / UNCERTAIN
- `EntitySimilaritySearchPort` driven port: `List<SimilarityResult> search(float[] vector, UUID campaignId, String entityType, int topN)`
- `EntitySimilaritySearchAdapter`: native pgvector query `1 - (embedding <=> $1::vector) AS similarity ... WHERE campaign_id = $2 AND entity_type = $3 ORDER BY embedding <=> $1::vector LIMIT $4` (D-062, ARCH-04)
- `SpringAiEntityResolutionAdapter` (`@Profile("llm-real | llm-ollama")`): provider-neutral `ChatClient`, LLM call 2; structured output: outcome + matched entity ID; logs MDC `stage = "resolution"` (D-088)

*`SessionIngestionEventListener` extended:* calls entity resolution after extraction; passes `List<ResolvedEntity>` to next stage.

**Scope (out):** Conflict detection (F2.6). Diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-041, D-042, D-062, D-088  
**Dependencies:** F2.4

---

#### F2.6 — Conflict detection pipeline

**Goal:** LLM Call 3. Compare extracted facts against current world state for hard contradictions. Produces non-blocking `ConflictWarning` cards (D-033).

**Scope (in):**

*Application:*
- `ConflictDetectionService` (internal): assembles `EntityContext` for MATCH-resolved entities only via `EntitySimilaritySearchPort` (embed `narrativeSummaryHeader` → top-N relevant snapshots); calls `ConflictDetectionPort`; if no MATCH entities: skip LLM call, return empty list
- `SpringAiConflictDetectionAdapter` (`@Profile("llm-real | llm-ollama")`): provider-neutral `ChatClient`, pgvector context-scoped LLM call 3; structured output: `List<ConflictWarning>`; logs MDC `stage = "conflict_detection"` (D-088)

*`SessionIngestionEventListener` extended:* calls conflict detection after entity resolution; passes `List<ConflictWarning>` to next stage.

**Scope (out):** Diff generation (F2.7).

**Skills:** `session-ingestion-pipeline`  
**Decisions:** D-033, D-034, D-062, D-088  
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
- Discard button: GM-only (from `campaignStore.activeRole`); confirmation dialog; calls `DELETE /sessions/{sid}`; navigates to `/sessions/new` on success
- Draft recovery: on `/sessions/new`, check for existing draft; if found → show banner "You have an unfinished review" with link to `/sessions/{id}/diff`; uses the `existingSessionId` from the `409` response of `useSubmitSession`
- `api/sessions.ts` extended: `DELETE /sessions/{sid}` → `useDiscardSession` mutation

**Scope (out):** World state exploration (Phase 4).

**Skills:** `frontend-diff-review`, `frontend-testing`  
**Decisions:** D-002, D-033, D-042, D-054, D-076  
**Dependencies:** F2.10, F2.8

---

#### F2.12 — Local LLM via Ollama (offline real pipeline)

> **Umbrella task — run the F2.12.N sub-tasks below, not this.**

**Goal:** Add a third provider selection — the `llm-ollama` Spring profile — that runs the entire ingestion + Query Mode pipeline against local models served by Ollama, fully offline with no API keys and zero cost, including **real semantic Query Mode** (real local embeddings in pgvector). Prod stays OpenAI@1536; local becomes Ollama (`bge-m3`@1024 + `qwen2.5:7b`). See D-088.

**Scope (out):** Any domain/application/adapter code change — the `SpringAi*` adapters are already provider-neutral (F2.4–F2.6) and gated `@Profile("llm-real | llm-ollama")`; this task only adds the Ollama starter, profile config, and the profile-selected model beans. Reversing the OpenAI@1536 production default (D-088 keeps it).

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
- `apps/api/src/main/java/com/bluesteel/adapters/out/ai/AiConfig.java` (extend) — `@Profile("llm-ollama")` `@Bean`s exposing the active `ChatClient` (from `OllamaChatModel`) and `EmbeddingModel` (Ollama); ensure no bean ambiguity with the Anthropic/OpenAI auto-config (profile-gate the provider beans so exactly one `ChatModel`/`EmbeddingModel` is active).
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
