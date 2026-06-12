# ROADMAP V2 — Blue Steel

> **Status: decomposed to feature level.** Phases 5–7 are broken into `F<phase>.<n>` tasks below.
> Each task still needs the fine-grained sub-task split (`.ai/roadmap-decomposition-prompt.md` —
> exact file paths, SETUP blocks, per-file tests) before the pipeline runs it. Tracking conventions
> and the status legend live in [`README.md`](README.md); the v1 record is
> [`ROADMAP_V1.md`](ROADMAP_V1.md).
>
> **Phase numbering continues from v1** (Phases 0–4 = v1), so task IDs stay globally unique across
> roadmap files: v2 work is `F5.x`, `F6.x`, `F7.x`. Build sequence within v2 is **TBD** — recorded
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

## Versioning

Per D-090, `1.0.0` = v1 complete (Input + Query + Exploration). v2 phase milestones map to post-1.0
SemVer minors in completion order; the exact phase→version mapping is recorded at the Phase 5 gate
together with the v2 build sequence.
