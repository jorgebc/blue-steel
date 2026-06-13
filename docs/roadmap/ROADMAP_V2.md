# ROADMAP V2 — Blue Steel

> **Status: decomposed to feature level.** Phases 5–9 are broken into `F<phase>.<n>` tasks below.
> Each task still needs the fine-grained sub-task split (`.ai/roadmap-decomposition-prompt.md` —
> exact file paths, SETUP blocks, per-file tests) before the pipeline runs it. Tracking conventions
> and the status legend live in [`README.md`](README.md); the v1 record is
> [`ROADMAP_V1.md`](ROADMAP_V1.md).
>
> **Phase numbering continues from v1** (Phases 0–4 = v1), so task IDs stay globally unique across
> roadmap files: v2 work is `F5.x` through `F9.x`. Build sequence within v2 is **TBD** — recorded
> as a DECISIONS.md entry at the Phase 5 gate. Scope (in) bullets are capability-level on purpose;
> exact paths are assigned at sub-task decomposition.

---

## Phases

### Phase 5 — Proposal & Approval Pipeline

**Purpose:** Give players a structured way to correct or extend world state without write access:
propose a change → another player co-signs → GM approves or vetoes → approved deltas become new
entity versions. Activates the workflow v1 shipped as data model only (D-016): `proposals` /
`proposal_votes` tables (migrations `0018`/`0019`) and the disabled `ProposeChangeButton` (D-012).

**Phase 5 Gate — design decisions to record in DECISIONS.md before F5.1 starts:**

- [ ] TTL value + semantics for proposal expiry — which statuses expire, clock source, env knob (resolves the open part of D-019)
- [ ] Concurrent-proposal rule — what happens when two open proposals target the same entity (PRD §7 v2 scope); blocks F5.6
- [ ] Proposal-delta shape — field-level JSONB contract for `proposed_delta` and how it maps onto entity version `changed_fields`
- [ ] v2 build sequence (Phase 5 → 6 → 7 or otherwise) + post-1.0 version mapping (extends D-090)

#### Summary

| # | Feature | Status |
|---|---|---|
| F5.1 | Backend: proposal domain model + ports | 🔲 |
| F5.2 | Backend: proposal submission + listing API | 🔲 |
| F5.3 | Backend: co-sign flow | 🔲 |
| F5.4 | Backend: GM decision — approve applies delta, veto rejects | 🔲 |
| F5.5 | Backend: proposal TTL expiry scheduler | 🔲 |
| F5.6 | Backend: concurrent-proposal conflict rule | 🔲 |
| F5.7 | Frontend: activate "Propose a change" + submission overlay | 🔲 |
| F5.8 | Frontend: proposal list/detail on profiles + co-sign | 🔲 |
| F5.9 | Frontend: GM review queue (approve/veto) | 🔲 |

#### F5.1 — Backend: proposal domain model + ports

**Goal:** Pure-domain `Proposal` and `ProposalVote` with the status machine the schema already
constrains (`open → cosigned → approved | rejected`, `open|cosigned → expired`), plus driving/driven
ports, so every later task builds on validated domain invariants.

**Scope (in):**
- `domain/proposal/` entities + status transitions; invariants: polymorphic target (`actor|space|event|relation`), one vote per voter (mirrors `uidx_proposal_votes_proposal_voter`), vote kinds `cosign|approve|reject`
- driving/driven port interfaces (`port/in/proposal/`, `port/out/proposal/`) + persistence adapter over the existing tables (no schema change)

**Scope (out):** Any REST endpoint (F5.2+); delta application (F5.4); expiry (F5.5).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-016, D-017, D-018, D-019  **Dependencies:** Phase 5 Gate

#### F5.2 — Backend: proposal submission + listing API

**Goal:** Campaign members create proposals against an existing entity and browse them, scoped per
entity and per campaign.

**Scope (in):**
- `POST /api/v1/campaigns/{id}/proposals` (any member; target entity must exist in the campaign; `proposed_delta` validated against the gate-decided contract; sets `expires_at` from the configured TTL)
- `GET /api/v1/campaigns/{id}/proposals` (+ filter by target entity and status; offset pagination per D-055 conventions)
- error mapping in `GlobalExceptionHandler` (404 unknown target, 422 invalid delta)

**Scope (out):** Voting (F5.3); GM decision (F5.4); UI (F5.7+).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** D-016, D-043  **Dependencies:** F5.1

#### F5.3 — Backend: co-sign flow

**Goal:** A player other than the author seconds a proposal; at ≥1 co-sign the proposal becomes
`cosigned` and surfaces to the GM (D-017).

**Scope (in):**
- `POST /api/v1/campaigns/{id}/proposals/{pid}/votes` with `cosign` (member auth; author cannot co-sign their own proposal; duplicate vote → 409, backed by the DB unique constraint)
- status transition `open → cosigned` on first co-sign

**Scope (out):** `approve`/`reject` votes (F5.4 — GM only).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-017  **Dependencies:** F5.2

#### F5.4 — Backend: GM decision — approve applies delta, veto rejects

**Goal:** The GM approves or unilaterally vetoes a `cosigned` proposal (D-018). Approval applies
`proposed_delta` as a **new entity version** through the existing world-state write path, preserving
append-only history and traceability.

**Scope (in):**
- GM-only decision endpoint (`approve`/`reject` vote or a dedicated decision route — settled at sub-task decomposition); status → `approved`/`rejected`
- on approve: map delta → entity version write reusing the commit write machinery (`WorldStatePort` / `WorldStateAdapter`, v1 F2.x); version provenance must record that it came from a proposal

**Scope (out):** Conflicts between competing proposals (F5.6); notifications (not in v2 scope anywhere — do not add).

**Skills:** `backend-endpoint`, `backend-domain-model`, `backend-testing`  **Decisions:** D-017, D-018, D-001  **Dependencies:** F5.3

#### F5.5 — Backend: proposal TTL expiry scheduler

**Goal:** Proposals with no action within the configured TTL flip to `expired` automatically, keeping
the GM queue clean (D-019).

**Scope (in):**
- scheduled sweep patterned on `SessionTimeoutRecoveryScheduler` (v1 F2.x); expires `open`/`cosigned` proposals past `expires_at`
- TTL + sweep interval as env-overridable properties (existing `${SOME_KNOB:default}` convention)

**Scope (out):** Changing `expires_at` semantics (fixed by the gate decision).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-019  **Dependencies:** F5.2, Phase 5 Gate (TTL decision)

#### F5.6 — Backend: concurrent-proposal conflict rule

**Goal:** Enforce the gate-decided rule for multiple open proposals targeting the same entity
(e.g., block at submission, or flag at GM decision time) so approvals never silently clobber each
other.

**Scope (in):** the decided rule at submission and/or decision time, plus its error code(s).

**Scope (out):** Merge/rebase tooling for deltas (out of v2 scope entirely).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** Phase 5 Gate (new D-number)  **Dependencies:** F5.4, Phase 5 Gate

#### F5.7 — Frontend: activate "Propose a change" + submission overlay

**Goal:** The disabled `ProposeChangeButton` stub (D-012) becomes live on every entity/space/event/
relation profile: a FocusedOverlay form captures the proposed field changes and submits.

**Scope (in):**
- proposal TypeScript types mirrored from the backend DTOs (D-076) + API client/hooks
- enable `components/domain/ProposeChangeButton.tsx`; FocusedOverlay submission form (no modals, D-082); InlineBanner feedback (no toasts, D-083)

**Scope (out):** Browsing proposals (F5.8); GM actions (F5.9).

**Skills:** `frontend-api-resource`, `ux-focused-overlay`, `ux-inline-feedback`, `react-hook-form`, `frontend-testing`  **Decisions:** D-012, D-076, D-082, D-083  **Dependencies:** F5.2

#### F5.8 — Frontend: proposal list/detail on profiles + co-sign

**Goal:** Members see a proposal thread on each entity profile (status badges for the five states)
and players co-sign others' proposals.

**Scope (in):** per-entity proposal section in exploration profiles; co-sign action with role/author
gating mirrored from the backend rules; skeleton loading (D-086).

**Scope (out):** GM approve/veto (F5.9).

**Skills:** `frontend-exploration`, `frontend-api-resource`, `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-017, D-076  **Dependencies:** F5.3, F5.7

#### F5.9 — Frontend: GM review queue (approve/veto)

**Goal:** The GM gets a campaign-level queue of `cosigned` proposals and decides each one; approval
feedback links to the resulting entity version.

**Scope (in):** review queue view (GM-gated route/section); approve/veto actions with confirmation
overlay; InlineBanner outcomes.

**Scope (out):** Player-facing views (F5.8).

**Skills:** `frontend-exploration`, `ux-focused-overlay`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-018, D-076  **Dependencies:** F5.4, F5.8

---

### Phase 6 — Input & Query Enhancements

**Purpose:** Close the two workflow gaps v1 consciously deferred — manually adding entities the AI
missed during diff review (D-053), and persisting Query Mode history (D-058) — plus the contingent
streaming path (D-052).

#### Summary

| # | Feature | Status |
|---|---|---|
| F6.1 | Backend: commit-payload `add` action | 🔲 |
| F6.2 | Frontend: "Add entity" affordance in diff review | 🔲 |
| F6.3 | Backend: Q&A log persistence | 🔲 |
| F6.4 | Backend: Q&A log read API | 🔲 |
| F6.5 | Frontend: Q&A history panel in Query Mode | 🔲 |
| F6.6 | Query streaming / SSE (contingent — latency evidence only) | 🔲 |

#### F6.1 — Backend: commit-payload `add` action

**Goal:** Reviewers can introduce an entity the extraction missed: the commit payload accepts `add`
entries that create new entities + first versions at commit, with the same campaign/session
traceability as extracted ones.

**Scope (in):**
- replace `CommitPayloadValidator` check #8 (`422 UNSUPPORTED_ACTION`) with validation of added entities (type, non-blank name, field contract)
- extend `CommitService` / the world-state write path to create the added entities; update the canonical CommitPayload contract (ARCHITECTURE §7.6, D-076)

**Scope (out):** The UI affordance (F6.2); editing extracted cards (shipped in v1).

**Skills:** `session-ingestion-pipeline`, `backend-endpoint`, `backend-testing`  **Decisions:** D-053, D-076, D-001  **Dependencies:** v1 F2.x commit path

#### F6.2 — Frontend: "Add entity" affordance in diff review

**Goal:** An "Add entity" action on the diff review page opens a FocusedOverlay form; added entities
appear as a new card category and ride the commit payload.

**Scope (in):** add-entity form + card rendering; extend `useDiffState` / `useCommitPayload` and the
mirrored CommitPayload types (D-076); commit-button gating unchanged.

**Scope (out):** Backend validation/write (F6.1).

**Skills:** `frontend-diff-review`, `ux-focused-overlay`, `react-hook-form`, `frontend-testing`  **Decisions:** D-053, D-076, D-082  **Dependencies:** F6.1

#### F6.3 — Backend: Q&A log persistence

**Goal:** Successful queries are persisted (question, answer, citations, asker, timestamp) per
campaign so the table can revisit past answers (D-058 lifts v1 statelessness).

**Scope (in):**
- append-only Liquibase migration for the query-log table (campaign-scoped, asker FK)
- persistence hook in `QueryService` after a successful answer (failures/timeouts are not logged); retention/size bound as an env-overridable property

**Scope (out):** Read API (F6.4); UI (F6.5); re-running past queries.

**Skills:** `database-migration`, `query-pipeline`, `backend-testing`  **Decisions:** D-058, D-096  **Dependencies:** v1 F3.x query pipeline

#### F6.4 — Backend: Q&A log read API

**Goal:** Campaign members browse the campaign's Q&A history, newest first.

**Scope (in):** `GET /api/v1/campaigns/{id}/queries/history` (member auth; offset pagination;
read-only — does not consume the query rate limit, same as `GET /queries/usage`).

**Scope (out):** Deleting/editing history entries (log is append-only — not in scope).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-058, D-043  **Dependencies:** F6.3

#### F6.5 — Frontend: Q&A history panel in Query Mode

**Goal:** A history panel inside Query Mode lists past Q&As; selecting one shows its answer and
citations (citations keep linking to session detail).

**Scope (in):** history panel + types/hooks for the read API; skeleton loading; the current-answer
flow (v1 F3.4) stays unchanged.

**Scope (out):** Re-submitting a past question as a new query (not in D-058 scope).

**Skills:** `frontend-query-mode`, `frontend-api-resource`, `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-058, D-076  **Dependencies:** F6.4

#### F6.6 — Query streaming / SSE (contingent)

**Goal:** Token-streamed answers **only if** v1 production latency data shows the synchronous model
cannot meet the < 5s target (D-052). Do not build on speculation.

**Scope (in):** trigger check first — latency evidence from production cost/latency logs, recorded as
a DECISIONS entry; if triggered: SSE endpoint variant + frontend incremental rendering, decomposed at
that point (backend and frontend as separate sub-tasks).

**Scope (out):** Everything, until the latency trigger is documented.

**Skills:** `query-pipeline`, `frontend-query-mode`  **Decisions:** D-052  **Dependencies:** v1 production latency evidence (gate)

---

### Phase 7 — Campaign Export

**Purpose:** A campaign's data outlives the platform: download the full campaign (actors, spaces,
events, relations, annotations, sessions, version history) in a portable format — primarily a guard
rail before campaign deletion.

**Phase 7 Gate — design decisions to record in DECISIONS.md before F7.1 starts:**

- [ ] Resolve the scope tension: PRD §7 lists "public sharing or export" as post-v2, while the v1 roadmap's v2 stub included this narrower pre-deletion export — confirm scope and update PRD or roadmap accordingly
- [ ] Export format (e.g., structured JSON archive vs. static HTML bundle) and authorization rule (GM and/or admin)

#### Summary

| # | Feature | Status |
|---|---|---|
| F7.1 | Backend: campaign export endpoint | 🔲 |
| F7.2 | Frontend: export affordance in campaign danger zone | 🔲 |

#### F7.1 — Backend: campaign export endpoint

**Goal:** One endpoint assembles the complete campaign dataset in the gate-decided format, without
blowing the Render free-tier memory budget.

**Scope (in):** export endpoint (authorization per gate decision); streamed/chunked assembly — heavy
lifting in SQL, bounded result sets in the JVM, limits env-overridable (per `apps/api/CLAUDE.md`
resource-budget conventions).

**Scope (out):** Public/sharable links (post-v2 per PRD); import.

**Skills:** `backend-endpoint`, `security-hardening`, `backend-testing`  **Decisions:** Phase 7 Gate (new D-number), D-001  **Dependencies:** Phase 7 Gate

#### F7.2 — Frontend: export affordance in campaign danger zone

**Goal:** The campaign home danger zone offers "Export campaign" before deletion; the file downloads
with clear progress and feedback.

**Scope (in):** export button + download handling; InlineBanner success/failure; placement beside the
existing delete flow (`CampaignHomePage` danger zone).

**Scope (out):** Backend assembly (F7.1).

**Skills:** `frontend-api-resource`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** Phase 7 Gate  **Dependencies:** F7.1

---

### Phase 8 — User Profile, Settings & Personalization

**Purpose:** Give every user a persistent identity (display name, initials/accent avatar) and
personalization (UI language EN/ES, theme light/dark/system), reached through a standard top-right
account menu — replacing today's raw-email header and the inert "Settings — Coming soon" sidebar
stub. This phase establishes the settings persistence (D-100/D-101) that the i18n (F8.6) and theme
(F8.7) features consume. UI locale here is the per-*user* axis; the per-*campaign* content language is
Phase 9 (D-099).

**Phase 8 Gate — design decisions (resolved before F8.1):**

- [x] User profile/settings model — display name (cosmetic, non-unique), initials+accent avatar, columns on `users` (**D-100**)
- [x] Preference persistence — hybrid DB-source-of-truth + localStorage mirror; locales `en`/`es`; theme `light|dark|system` (**D-101**)
- [x] Settings entry point — top-right account menu + global `/settings` route, removed from campaign sidebar (**D-102**)
- [x] Two language axes (UI locale vs campaign content language) framing (**D-099**)

(Accent-color palette and the per-page order of i18n string extraction are implementation details,
settled at sub-task decomposition.)

#### Summary

| # | Feature | Status |
|---|---|---|
| F8.1 | Backend: user profile/settings persistence + domain | 🔲 |
| F8.2 | Backend: `GET /me` extension + `PATCH /me` profile/settings API | 🔲 |
| F8.3 | Frontend: settings store + API hooks + DTO types | 🔲 |
| F8.4 | Frontend: top-right account menu (header redesign) | 🔲 |
| F8.5 | Frontend: User Settings page (`/settings`) | 🔲 |
| F8.6 | Frontend: i18n infrastructure + EN/ES catalogs | 🔲 |
| F8.7 | Frontend: dark-mode theme system | 🔲 |

#### F8.1 — Backend: user profile/settings persistence + domain

**Goal:** Persist the four new profile/settings fields so every later task reads and writes a stable
user model.

**Scope (in):**
- append-only Liquibase migration `0026_add_user_profile_settings.xml` adding to `users`: `display_name TEXT NULL`, `avatar_accent_color TEXT NULL`, `ui_locale TEXT NOT NULL DEFAULT 'en'`, `theme TEXT NOT NULL DEFAULT 'system'`; registered in `db.changelog-master.xml`
- extend `UserJpaEntity` and the `UserProfile` domain model with the four fields

**Scope (out):** Read/update API (F8.2); any UI.

**Acceptance:**
- A `users` row created before this migration loads afterward with `ui_locale='en'` and `theme='system'` (defaults applied), `display_name`/`avatar_accent_color` null.
- The domain user model round-trips all four fields through persistence (write → read returns the same values).

**Skills:** `database-migration`, `backend-testing`  **Decisions:** D-100, D-101  **Dependencies:** —

#### F8.2 — Backend: `GET /me` extension + `PATCH /me` profile/settings API

**Goal:** Expose the profile/settings on the current-user endpoint and let the user update them.

**Scope (in):**
- extend `UserMeResponse` (`GET /api/v1/users/me`) with `displayName`, `avatarAccentColor`, `uiLocale`, `theme`
- new `PATCH /api/v1/users/me` with `UpdateProfileRequest` (validates `uiLocale ∈ {en,es}`, `theme ∈ {light,dark,system}`, accent-color hex) + `UpdateCurrentUserProfileUseCase`, mirroring the existing `ChangePasswordUseCase` shape
- error mapping in `GlobalExceptionHandler` (400 validation)

**Scope (out):** UI (F8.3+).

**Acceptance:**
- `GET /me` returns the four new fields for the authenticated user.
- `PATCH /me` with valid values persists them; a subsequent `GET /me` reflects the change.
- Invalid `ui_locale`, `theme`, or accent-color → `400` with a field-level error; one user's update never affects another's settings.

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** D-100, D-101, D-043  **Dependencies:** F8.1

#### F8.3 — Frontend: settings store + API hooks + DTO types

**Goal:** Client-side settings state synced to the server, with a localStorage mirror for no-flash
first paint.

**Scope (in):**
- extend the `UserMeResponse` type in `types/auth.ts`; add `updateProfile()` + `useUpdateProfile()` in `api/users.ts` (invalidate the `/me` query on success)
- `useSettingsStore` (Zustand `persist`, key `blue-steel-settings`) mirroring `theme` + `uiLocale`, hydrated from `/me` on login

**Scope (out):** Visible UI (F8.4/F8.5).

**Acceptance:**
- After login, the settings store is populated from `/me`.
- Calling the update hook writes to the server and the `/me` cache reflects the change without a manual refetch.
- `theme`/`uiLocale` survive a page reload (read from the localStorage mirror) even before `/me` resolves.

**Skills:** `frontend-api-resource`, `frontend-testing`, `auth`  **Decisions:** D-101, D-076  **Dependencies:** F8.2

#### F8.4 — Frontend: top-right account menu (header redesign)

**Goal:** Replace the raw-email header element and retire the sidebar Settings stub with a standard
top-right account menu.

**Scope (in):**
- new `components/domain/UserMenu.tsx` — avatar (initials + accent) trigger opening a top-right shadcn `dropdown-menu` with name + email, a Settings link, inline theme toggle, inline EN/ES switch, and Log out (relocated from `AppBar`)
- wire into `AppBar.tsx`, replacing the email span; **remove** the `ComingSoonItem` "Settings" entry and its now-unused `Settings` icon import from `Sidebar.tsx`

**Scope (out):** The Settings page body (F8.5).

**Acceptance:**
- The top-right header shows an avatar rendered from initials (display name, or email fallback); the campaign sidebar no longer shows a Settings item.
- Opening the menu reveals name + email, a Settings link, theme and EN/ES toggles, and Log out; logging out from it signs the user out from any page.
- The menu is keyboard-operable and passes an axe check.

**Skills:** `ux-navigation-logic`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-102, D-087  **Dependencies:** F8.3

#### F8.5 — Frontend: User Settings page (`/settings`)

**Goal:** A full settings surface for profile + preferences, on a global (non-campaign) route.

**Scope (in):**
- `/settings` route in `main.tsx` (global, authenticated, sibling to `CampaignListPage` — **not** campaign-scoped) → `UserSettingsPage` with a display-name field, accent-color picker + live initials preview, locale selector, and theme control; writes via `useUpdateProfile`; InlineBanner feedback (no toasts, D-083)

**Scope (out):** Account-menu chrome (F8.4).

**Acceptance:**
- `/settings` is reachable only while authenticated and works independently of any active campaign.
- Editing display name / accent color / locale / theme and saving shows inline success feedback (no toast) and the changes persist across reload.
- The initials preview updates live as the display name or accent color changes.

**Skills:** `frontend-api-resource`, `react-hook-form`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-100, D-102, D-082, D-083  **Dependencies:** F8.4

#### F8.6 — Frontend: i18n infrastructure + EN/ES catalogs

**Goal:** Internationalize the UI with English and Spanish catalogs, driven by the user's UI locale.

**Scope (in):**
- add `i18next` + `react-i18next`; provider at app root driven by `useSettingsStore.uiLocale`
- catalogs `src/i18n/locales/{en,es}.json`; extract hardcoded strings starting with `Sidebar.tsx`, `AppBar.tsx`, `UserMenu`, then page by page

**Scope (out):** Campaign content language (Phase 9).

**Acceptance:**
- Switching the UI locale (menu or settings) re-renders all visible UI strings in the chosen language without a full page reload.
- A string with no translation falls back to English rather than displaying a raw key.
- The chosen locale persists across reloads.

**Skills:** `frontend-testing`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-099, D-101, D-087  **Dependencies:** F8.3 (locale source)
*Note:* no existing i18n skill in `skills/SKILLS_INDEX.md` — consider adding one during decomposition.

#### F8.7 — Frontend: dark-mode theme system

**Goal:** Light/dark/system theming wired to the user's theme preference, with no flash on load.

**Scope (in):**
- `.dark` CSS-variable overrides in `src/index.css` `@theme` (currently light-only, Tailwind v4)
- theme provider toggling `<html class="dark">` from `useSettingsStore.theme`; `system` follows `prefers-color-scheme`; the localStorage mirror prevents first-paint flash. shadcn is already `cssVariables: true`

**Scope (out):** Per-component restyle beyond variable wiring.

**Acceptance:**
- Selecting Dark applies the dark palette across the app; Light restores it; System follows the OS preference and reacts to OS changes live.
- On reload the correct theme is applied on first paint — no flash of the wrong theme.
- shadcn components render in the active theme.

**Skills:** `ux-focused-overlay`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-101, D-087  **Dependencies:** F8.3
*Note:* no existing dark-mode skill in `skills/SKILLS_INDEX.md` — consider adding one during decomposition.

---

### Phase 9 — Per-Campaign Content Language (Multilingual LLM Pipeline)

**Purpose:** Each campaign fixes one content language at creation; narrative extraction, conflict
detection, and query answering all produce output in that language, and the stored world state stays
single-language. This is the per-*campaign* axis (D-099), independent of the per-user UI locale
(Phase 8). An independent backend track; embeddings are unchanged.

**Phase 9 Gate — design decisions (resolved before F9.1):**

- [x] Content language fixed at creation (immutable), `NOT NULL DEFAULT 'en'` for existing rows (**D-103**)
- [x] Language injected into the three LLM prompts; embeddings unchanged (multilingual models); no per-version/embedding language tag (**D-103**)
- [x] Content-language list = UI locale list (`en`, `es`) (**D-099/D-103**)

#### Summary

| # | Feature | Status |
|---|---|---|
| F9.1 | Backend: campaign `content_language` schema + domain + create API | 🔲 |
| F9.2 | Backend: thread language into extraction + conflict-detection prompts | 🔲 |
| F9.3 | Backend: language in query-answering prompt | 🔲 |
| F9.4 | Frontend: content-language picker on create + read-only display | 🔲 |

#### F9.1 — Backend: campaign `content_language` schema + domain + create API

**Goal:** Persist and expose an immutable per-campaign content language, set at creation.

**Scope (in):**
- append-only migration `0027_add_campaign_content_language.xml` (`content_language TEXT NOT NULL DEFAULT 'en'`); registered in the master changelog
- extend `CampaignJpaEntity` + the `Campaign` domain + `CreateCampaignRequest` (`contentLanguage ∈ {en,es}`) + campaign read DTOs; **no** update path (immutable)

**Scope (out):** Prompt wiring (F9.2/F9.3); UI (F9.4).

**Acceptance:**
- Creating a campaign with `contentLanguage='es'` persists it and it appears on campaign reads; omitting it defaults to `en`; pre-existing campaigns read back as `en`.
- There is no API path to change a campaign's content language after creation; an invalid value → `400`.

**Skills:** `database-migration`, `backend-endpoint`, `backend-testing`  **Decisions:** D-103  **Dependencies:** —

#### F9.2 — Backend: thread language into extraction + conflict-detection prompts

**Goal:** Ingestion produces entities and conflict analysis in the campaign's language.

**Scope (in):**
- thread `contentLanguage` through the `NarrativeExtractionPort` / `ConflictDetectionPort` call sites and inject a language instruction into the `SpringAiNarrativeExtractionAdapter` and `SpringAiConflictDetectionAdapter` system prompts (today hardcoded constants); mock adapters honor it for tests
- **embeddings untouched** (`EmbeddingPort` / pgvector retrieval unchanged)

**Scope (out):** Query path (F9.3).

**Acceptance:**
- Ingesting a session in an `es` campaign yields extracted entities and conflict descriptions in Spanish; an `en` campaign stays English (verifiable via the mock adapter honoring the language parameter, and end-to-end under `llm-real`).
- Embedding generation and storage are byte-for-byte unchanged by this task.

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-103  **Dependencies:** F9.1

#### F9.3 — Backend: language in query-answering prompt

**Goal:** Query Mode answers come back in the campaign's language.

**Scope (in):**
- `QueryService` passes the campaign's language to `QueryPromptAssembler` / `SpringAiQueryAnsweringAdapter`; append "Respond in {language}." to the answering prompt; citation grounding (D-003) unchanged

**Scope (out):** Retrieval/embedding changes (none).

**Acceptance:**
- A query against an `es` campaign returns the answer text in Spanish while citations still resolve to the correct sessions; an `en` campaign answers in English.
- Retrieved context is unaffected by the language of the question phrasing.

**Skills:** `query-pipeline`, `backend-testing`  **Decisions:** D-103, D-003  **Dependencies:** F9.1

#### F9.4 — Frontend: content-language picker on create + read-only display

**Goal:** GMs pick the campaign language at creation and see it (read-only) afterwards.

**Scope (in):**
- language picker on `CreateCampaignPage` (reuse the F8.6 locale list); read-only display of the content language in campaign details

**Scope (out):** Changing it later (immutable, D-103).

**Acceptance:**
- The create-campaign form offers an EN/ES content-language choice and submits it.
- Campaign detail displays the chosen content language read-only, with no affordance to edit it.

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-103, D-076  **Dependencies:** F9.1, F8.6

---

## Versioning

Per D-090, `1.0.0` = v1 complete (Input + Query + Exploration). v2 phase milestones map to post-1.0
SemVer minors in completion order; the exact phase→version mapping is recorded at the Phase 5 gate
together with the v2 build sequence.
