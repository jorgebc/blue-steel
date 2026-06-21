# Module Name: System & Platform Services

## 1. Overview

This module covers the cross-cutting machinery that no single end-user "owns" but every feature depends on: health monitoring, the pluggable LLM and email providers, cost accounting, asynchronous execution, the global API error contract, and the persistence patterns (append-only versioning and vector embeddings) that make the product's memory trustworthy. From a business perspective, these services are what keep the system observable, affordable (LLM spend is bounded at several layers), and auditable (world state history is immutable).

## 2. Capabilities & Use Cases

- **Use Case / Action:** Check system health — ✅ Implemented
- **Actor:** Anonymous User, monitoring/load balancer
- **Functional Description:** A public, unauthenticated endpoint reports overall and database status (`UP`/`DOWN`/`DEGRADED`). The frontend exposes it on the public `/status` page, which renders without login.
- **Technical Reference / Source Files:** `GET /api/v1/health` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/health/HealthController.java`, `apps/api/src/main/java/com/bluesteel/application/service/health/HealthService.java`, `apps/web/src/features/status/StatusPage.tsx`

---

- **Use Case / Action:** Switch LLM providers by deployment profile — ✅ Implemented
- **Actor:** System (operator/deployment decision)
- **Functional Description:** All five LLM touchpoints (extraction, entity resolution, conflict detection, embeddings, query answering) go through provider-agnostic ports with three interchangeable adapter sets:
  - **Mock** (default profile) — deterministic test data, zero cost, no API keys.
  - **`llm-real`** — Google Gemini (`gemini-2.5-flash` chat, `gemini-embedding-001` embeddings at 1536 dimensions) (D-093).
  - **`llm-ollama`** — local self-hosted models (e.g., `qwen2.5:7b` chat, `bge-m3` 1024-dim embeddings), fully offline (D-088).
  The pgvector column dimension follows the embedding model via a Liquibase parameter; embedding models must never be mixed within one database.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/out/ai/` (`SpringAi*Adapter` and `Mock*Adapter` pairs), ports under `apps/api/src/main/java/com/bluesteel/application/port/out/`

---

- **Use Case / Action:** Account for and cap LLM spend — ✅ Implemented
- **Actor:** System
- **Functional Description:** Every LLM call logs tokens in/out and estimated USD cost with session/user attribution. A per-UTC-day in-memory accumulator across all stages feeds the daily cost cap that gates Query Mode (503 when exhausted; resets at UTC midnight and on application restart). Per-stage token budgets (extraction 4000, resolution 2000, conflict 3000, query 6000 by default) bound each individual call.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/out/ai/InMemoryDailyCostAccountingAdapter.java`, cost logging in the `SpringAi*Adapter` classes

---

- **Use Case / Action:** Send transactional email through a pluggable provider — ✅ Implemented
- **Actor:** System
- **Functional Description:** Invitation emails go through an email port with two adapters: a mock that logs to the console (default — local development sends nothing) and Brevo under the `email-real`/`prod` profile (D-075).
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/out/email/BrevoEmailAdapter.java`, `MockEmailAdapter.java`, `apps/api/src/main/java/com/bluesteel/application/service/email/InvitationEmailFactory.java`

---

- **Use Case / Action:** Run background work without blocking users — ✅ Implemented
- **Actor:** System
- **Functional Description:** Three asynchronous mechanisms keep the UX responsive: (1) the ingestion pipeline runs on the application task executor after session submission returns 202; (2) embedding generation runs after the commit response has already been sent; (3) a scheduled job recovers sessions stuck in `PROCESSING` (details in [session_ingestion.md](session_ingestion.md)). Query Mode additionally uses its own bounded executor so heavy query load cannot starve ingestion.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java`, `EmbeddingGenerationListener.java`, `SessionTimeoutRecoveryScheduler.java`

---

- **Use Case / Action:** Return consistent, machine-readable API errors — ✅ Implemented
- **Actor:** System (API consumers)
- **Functional Description:** A global exception handler maps every domain exception to the standard error envelope `{ "errors": [{ "code", "message", "field" }] }` with conventional status codes: 400 validation, 401 unauthenticated, 403 unauthorized, 404 not found, 409 conflict (e.g., active draft exists), 422 business rule (e.g., `UNCERTAIN_ENTITIES_PRESENT`, `SUMMARY_TOO_LARGE`), 429 rate limit, 503 cost cap, 504 query timeout. The frontend maps field errors onto forms and coded errors onto specific UX (banners, draft-recovery links).
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/in/web/GlobalExceptionHandler.java`; error-code catalog in `docs/ARCHITECTURE.md` §7.11

---

- **Use Case / Action:** Preserve immutable world-state history (two-table versioning) — ✅ Implemented
- **Actor:** System (persistence layer)
- **Functional Description:** Each versioned entity type (actor, space, event, relation) has a head table plus an append-only `*_versions` table storing, per committing session, the changed-fields delta and a full snapshot. Current state = highest version; point-in-time state = highest version with `session_id ≤ target`. History is never rewritten (D-001, D-035), which is what makes profiles' version timelines and citations trustworthy.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/worldstate/WorldStateAdapter.java`; migrations `apps/api/src/main/resources/db/changelog/0008`–`0015` (entity + version tables)

---

- **Use Case / Action:** Store and search vector embeddings — ✅ Implemented
- **Actor:** System (persistence layer)
- **Functional Description:** Committed entity versions are embedded into the `entity_embeddings` pgvector table (entity type/id/version, session, vector, content hash). Similarity search is implemented with native SQL rather than Spring AI's generic `VectorStore` (D-062) because retrieval needs domain filters (campaign scoping, entity type, session joins). This single table powers both ingestion-time entity resolution and Query Mode retrieval.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/embedding/EntityEmbeddingWriteAdapter.java`, `EntitySimilaritySearchAdapter.java`, `EntityQueryRetrievalAdapter.java`; migration `db/changelog/0016_create_entity_embeddings.xml`

---

- **Use Case / Action:** Render the UI in the user's language (EN/ES) — ✅ Implemented (v2, Phase 8)
- **Actor:** System (frontend runtime)
- **Functional Description:** An `i18next`/`react-i18next` runtime drives all internationalized strings from the user's UI locale preference, with an English fallback for missing keys; switching locale re-renders without a reload, and the `<html lang>` attribute follows the active locale (set on first paint by the pre-paint script and synced live on change) so assistive tech sees the right language. Phase 8 shipped the mechanism plus the always-visible nav chrome (Sidebar/AppBar/UserMenu); per-page string extraction proceeds as incremental follow-on work. This is the per-*user* language axis, distinct from a campaign's content language (D-099).
- **Technical Reference / Source Files:** `apps/web/src/i18n/index.ts`, `apps/web/src/i18n/locales/en.json`, `es.json`, driven by `apps/web/src/store/settingsStore.ts`

---

- **Use Case / Action:** Apply the user's theme (light/dark/system) without a first-paint flash — ✅ Implemented (v2, Phase 8)
- **Actor:** System (frontend runtime)
- **Functional Description:** The stored theme preference toggles a `dark` class on `<html>`, recolouring the semantic design tokens (shadcn surfaces); `system` follows the OS color scheme live via `matchMedia`. A synchronous pre-paint script in `index.html` reads the `localStorage` mirror and applies the theme before the bundle loads, so a reload never flashes the wrong theme. *Known limitation:* most feature components and the app chrome still hardcode raw `slate-*` utilities and therefore stay light in Dark mode; full app-wide coverage is tracked as roadmap follow-on F8.8.
- **Technical Reference / Source Files:** `apps/web/src/hooks/useApplyTheme.ts`, `apps/web/index.html` (pre-paint script), `apps/web/src/index.css` (`.dark` token overrides), `apps/web/src/store/settingsStore.ts`

## 3. Core User Journeys (Workflows)

**Journey: Operator verifies a deployment**
1. Operator (or uptime monitor) hits `GET /api/v1/health` — no credentials needed.
2. A `DEGRADED`/`DOWN` database status pinpoints connectivity issues; the public `/status` page shows the same to users.
3. Behind the scenes the same probe serves as the hosting platform's readiness check.

**Journey: A day of LLM spend, bounded end to end**
1. Each ingestion stage and query logs its token usage and cost with user/session attribution.
2. The daily accumulator approaches the cap; the query usage endpoint shows the table their shared remaining budget.
3. The cap trips → queries return 503 for the rest of the UTC day; ingestion token budgets still bound each pipeline call; the provider-console hard cap is the final backstop.
4. At UTC midnight the accumulator resets and normal service resumes.
