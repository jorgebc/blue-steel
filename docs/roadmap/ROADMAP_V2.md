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

- [x] TTL value + semantics for proposal expiry — which statuses expire, clock source, env knob (resolves the open part of D-019) (**D-105**)
- [x] Concurrent-proposal rule — what happens when two open proposals target the same entity (PRD §7 v2 scope); blocks F5.6 (**D-106**)
- [x] **Proposal↔session linkage (provenance) + version stamp** — the author selects a campaign `session_id` at creation as **provenance/context only** (`proposals.session_id`, FK `sessions`). On approval the resulting entity version is written through the existing `WorldStatePort.writeEntity` path but stamped with the campaign's **latest committed session id**, keeping `version_number`↔`sessions.sequence_number` co-monotonic (current state stays `MAX(version_number)`; no as-of-session reconstruction anomaly). (**D-107**) Shapes F5.1.1, F5.2.2, F5.4.2–F5.4.4.
- [x] **v2 target scope = `actor` + `space` only** — defer event/relation (structured endpoints) and new-entity proposals; D-012's "relation" affordance is explicitly deferred. Creation validation rejects other target types (422). (**D-108**) Shapes F5.1.2, F5.2.2, F5.4.3, F5.7.1.
- [x] **GM decision recorded as a vote** — approve/veto writes a `proposal_votes('approve'|'reject')` row (who/when audit) in addition to flipping `proposal.status`; co-sign writes a `cosign` row. (**D-109**) Shapes F5.3.2, F5.4.4.
- [x] **GM edit-on-approve** — the GM decision accepts an optional GM-edited delta applied in place of the author's `proposed_delta` when approving. (**D-110**) Shapes F5.4.1, F5.4.3, F5.9.2.
- [x] **`proposed_delta` shape (resolve first)** — field-level JSONB contract that **mirrors entity-version `changed_fields`** (ARCHITECTURE §7.6) so the approve-time apply-mapper is trivial. Note: `target_entity_type`/`target_entity_id`/`proposed_delta` stay nullable in the DB but are enforced **non-null in the application** at creation. (**D-104**)
- [x] v2 build sequence (Phase 5 → 6 → 7 → 8 → 9) + post-1.0 version mapping (extends D-090) (**D-111**)

#### Summary

| # | Feature | Status |
|---|---|---|
| F5.1 | Backend: proposal domain model + ports *(umbrella)* | ✅ |
| F5.1.1 | Migration 0026 — add `session_id` (FK sessions) + `resulting_entity_version_id` to `proposals` | ✅ |
| F5.1.2 | Domain: `Proposal` aggregate + `ProposalStatus` + `ProposalTargetType` + exceptions | ✅ |
| F5.1.3 | Domain: `ProposalVote` + `VoteKind` | ✅ |
| F5.1.4 | Application: `ProposalRepository` driven port + `application/model/proposal` records | ✅ |
| F5.1.5 | Persistence: `ProposalJpaEntity` + `ProposalVoteJpaEntity` + JPA repositories | ✅ |
| F5.1.6 | Persistence: `ProposalPersistenceAdapter` (mapper, impl `ProposalRepository`) + IT | ✅ |
| F5.2 | Backend: proposal submission + listing API *(umbrella)* | ✅ |
| F5.2.1 | Driving ports `Create`/`ListProposalsUseCase` + command/result models | ✅ |
| F5.2.2 | `ProposalCreationService` — validate target+session in campaign, set `expires_at` | ✅ |
| F5.2.3 | `ListProposalsService` — filter by target/status, offset pagination | ✅ |
| F5.2.4 | `ProposalController` POST+GET + request/response DTOs | ✅ |
| F5.2.5 | Error mapping in `GlobalExceptionHandler` (404 target/session, 422 invalid delta) | ✅ |
| F5.3 | Backend: co-sign flow *(umbrella)* | ✅ |
| F5.3.1 | Driving port `CoSignProposalUseCase` + `CoSignProposalCommand` | ✅ |
| F5.3.2 | `ProposalCoSignService` — author-cannot-cosign, duplicate→409, `open→cosigned` | ✅ |
| F5.3.3 | Vote endpoint + error mapping (`@WebMvcTest`); co-sign returns updated proposal (no body) | ✅ |
| F5.4 | Backend: GM decision — approve applies delta, veto rejects *(umbrella)* | ✅ |
| F5.4.1 | Driving port `DecideProposalUseCase` + command (approve/reject + optional edited delta) | ✅ |
| F5.4.2 | `SessionRepository.findLatestCommittedSessionId` + adapter (IT) — supplies the version stamp | ✅ |
| F5.4.3 | `ProposalDeltaMapper` (actor/space) — reads current snapshot, stamps latest committed session | ✅ |
| F5.4.4 | `ProposalDecisionService` — GM-only; approve writes version + embeds + vote row, veto rejects | ✅ |
| F5.4.5 | Decision endpoint + DTO + GM gating + error mapping (`@WebMvcTest`) | ✅ |
| F5.5 | Backend: proposal TTL expiry scheduler *(umbrella)* | ✅ |
| F5.5.1 | `ProposalExpiryPort` + bulk-expiry SQL adapter (IT) | ✅ |
| F5.5.2 | `ProposalExpiryScheduler` (@Scheduled, env-overridable TTL/interval) | ✅ |
| F5.6 | Backend: concurrent-proposal conflict rule *(umbrella)* | ✅ |
| F5.6.1 | Repository `existsOpenProposalForTarget` query (IT) — shipped with F5.1 data layer | ✅ |
| F5.6.2 | Enforce gate rule in creation/decision + error code → handler — shipped with F5.2 | ✅ |
| F5.7 | Frontend: activate "Propose a change" + submission overlay *(umbrella)* | ✅ |
| F5.7-SETUP | Human: `shadcn add select` | ✅ |
| F5.7.1 | `types/proposal.ts` — DTO mirrors (Proposal incl `sessionId`, requests, statuses) | ✅ |
| F5.7.2 | `api/proposals.ts` — keys + `getProposals`/`createProposal` + hooks | ✅ |
| F5.7.3 | `ProposalSubmitForm.tsx` — RHF+zod, session `<Select>`, field-change capture, banner | ✅ |
| F5.7.4 | Activate `ProposeChangeButton.tsx` → opens `FocusedOverlay` with the form | ✅ |
| F5.8 | Frontend: proposal list/detail on profiles + co-sign *(umbrella)* | ✅ |
| F5.8.1 | `ProposalStatusBadge.tsx` — five states → shadcn `Badge` | ✅ |
| F5.8.2 | `api/proposals.ts` — add `useCoSignProposal` hook | ✅ |
| F5.8.3 | `ProposalThreadSkeleton.tsx` — DTO-derived loading state | ✅ |
| F5.8.4 | `ProposalThread.tsx` — per-entity list + co-sign (role/author gated) | ✅ |
| F5.8.5 | Mount `ProposalThread` in `EntityProfileView.tsx` | ✅ |
| F5.9 | Frontend: GM review queue (approve/veto) *(umbrella)* | ✅ |
| F5.9.1 | `api/proposals.ts` — add `useDecideProposal` + cosigned-queue hook | ✅ |
| F5.9.2 | `ProposalReviewCard.tsx` — approve (editable overlay) / veto + result link | ✅ |
| F5.9.3 | `ProposalReviewQueuePage.tsx` — GM-gated queue + skeleton | ✅ |
| F5.9.4 | Route + Sidebar nav entry (GM-gated) | ✅ |

#### F5.1 — Backend: proposal domain model + ports

> **Umbrella task — run the F5.1.N sub-tasks below, not this.**

Pure-domain `Proposal` and `ProposalVote` with the status machine the schema already constrains
(`open → cosigned → approved | rejected`, `open|cosigned → expired`), the driven persistence port,
and the JPA adapter — so every later task builds on validated domain invariants. Per the Phase 5
Gate session-linkage decision the proposal now carries a creator-selected `session_id`, which needs
one append-only migration (so this task is no longer strictly "no schema change").

#### F5.1.1 — Migration: add `session_id` + `resulting_entity_version_id` to `proposals`

**Goal:** Add the creator-selected provenance session link and the approved-version back-reference the gate decisions require, on top of the existing `proposals` table (migration 0018).

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0026_add_proposal_session_and_result.xml` — nullable `session_id UUID` + `fk_proposals_session` FK → `sessions(id)` (provenance/context; non-null enforced in the domain at creation); nullable `resulting_entity_version_id UUID` (**no FK** — polymorphic across the four `*_versions` tables; set on approval, traceability). Register it in the master changelog include list.

**Scope (out):** Any domain/JPA code (F5.1.2+); any change to `*_versions` tables (the approved version is stamped with the **latest committed session id**, not a new version-table column).

**Skills:** `database-migration`  **Decisions:** D-016, D-021, D-107  **Dependencies:** Phase 5 Gate

#### F5.1.2 — Domain: `Proposal` aggregate + status machine

**Goal:** Pure-Java `Proposal` enforcing the status machine and creation invariants, with no Spring/JPA imports (ARCH-01).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/proposal/Proposal.java`, `ProposalStatus.java`, `ProposalTargetType.java` (**`ACTOR|SPACE` only — v2 scope;** event/relation deferred) + needed `domain/exception/` additions (+ domain unit test)
- transitions `open→cosigned→approved|rejected` and `open|cosigned→expired`; carries `campaignId, targetType, targetId, ownerId (maps to the `author_id` column — document the D-021 name divergence), sessionId (provenance), proposedDelta, expiresAt, resultingEntityVersionId`; non-blank + owner invariants

**Scope (out):** `ProposalVote` (F5.1.3); ports/persistence (F5.1.4+); delta application (F5.4).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-016, D-017, D-018, D-019, D-021, D-078  **Dependencies:** F5.1.1

#### F5.1.3 — Domain: `ProposalVote` + vote kinds

**Goal:** Pure-domain vote value object capturing vote kind and the one-vote-per-voter invariant the DB unique index mirrors.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/proposal/ProposalVote.java`, `VoteKind.java` (`cosign|approve|reject`) (+ domain unit test)

**Scope (out):** Vote persistence (F5.1.5); co-sign logic (F5.3); GM approve/reject (F5.4).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-016, D-017  **Dependencies:** F5.1.2

#### F5.1.4 — Application: `ProposalRepository` driven port + models

**Goal:** Driven persistence port and shared application-model records every proposal service depends on.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/proposal/ProposalRepository.java` (save, findById, findByCampaign(filter,page), findByTarget, countByCampaign, `saveVote`, `existsOpenForTarget`)
- `apps/api/src/main/java/com/bluesteel/application/model/proposal/` read-view + filter/page records (`ProposalView` exposing provenance `sessionId` + `resultingEntityVersionId`, paging params)

**Scope (out):** Driving (`port/in`) use-cases — created with their endpoints (F5.2+); the adapter (F5.1.6) which provides this port's test coverage.

**Skills:** `backend-endpoint`  **Decisions:** D-016, D-055  **Dependencies:** F5.1.2, F5.1.3

#### F5.1.5 — Persistence: JPA entities + repositories

**Goal:** JPA entities and Spring Data repositories over the existing `proposals` / `proposal_votes` tables.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/proposal/ProposalJpaEntity.java` (`@JdbcTypeCode(JSON)` for `proposed_delta`), `ProposalVoteJpaEntity.java`, `ProposalJpaRepository.java`, `ProposalVoteJpaRepository.java` (+ repository Testcontainers IT)

**Scope (out):** The domain↔JPA mapper / `ProposalRepository` impl (F5.1.6).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-016  **Dependencies:** F5.1.1

#### F5.1.6 — Persistence: `ProposalPersistenceAdapter` + IT

**Goal:** Adapter implementing `ProposalRepository` by mapping domain ↔ JPA, validated end-to-end against Postgres.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/proposal/ProposalPersistenceAdapter.java` (+ Testcontainers IT covering save/find and the duplicate-vote `uidx_proposal_votes_proposal_voter` constraint)

**Scope (out):** Use-case services (F5.2+).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-016, D-017  **Dependencies:** F5.1.4, F5.1.5

#### F5.2 — Backend: proposal submission + listing API

> **Umbrella task — run the F5.2.N sub-tasks below, not this.**

Campaign members create proposals against an existing entity (selecting the linked session) and
browse them, scoped per entity and per campaign.

#### F5.2.1 — Driving ports + command/result models

**Goal:** Use-case interfaces and command/result records for submission and listing.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/proposal/CreateProposalUseCase.java`, `ListProposalsUseCase.java`
- `apps/api/src/main/java/com/bluesteel/application/model/proposal/CreateProposalCommand.java` (includes `sessionId`), `ProposalListView.java`

**Scope (out):** Service logic (F5.2.2/F5.2.3); controller (F5.2.4); voting (F5.3).

**Skills:** `backend-endpoint`  **Decisions:** D-016, D-055  **Dependencies:** F5.1.4

#### F5.2.2 — `ProposalCreationService`

**Goal:** Create a proposal after validating membership, target type/existence, the provenance session, and the delta; set `expires_at` from the configured TTL.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalCreationService.java` (+ unit test, mocked ports): membership via `CampaignMembershipPort`; **reject `targetType ∉ {actor, space}` → 422**; require non-null `target` and `proposed_delta`; target exists via `WorldStatePort.existsInCampaign`; provenance session belongs to the campaign via `SessionRepository`; `proposed_delta` validated against the gate `changed_fields`-shaped contract; `expires_at` from configured TTL

**Scope (out):** Listing (F5.2.3); controller/DTOs (F5.2.4); concurrent-proposal rule (F5.6).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-016, D-019, D-043, D-104, D-105, D-107, D-108  **Dependencies:** F5.2.1, F5.1.6

#### F5.2.3 — `ListProposalsService`

**Goal:** List proposals filtered by target entity and status with offset pagination.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ListProposalsService.java` (+ unit test)

**Scope (out):** Creation (F5.2.2); controller (F5.2.4).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-016, D-055  **Dependencies:** F5.2.1, F5.1.6

#### F5.2.4 — `ProposalController` POST + GET + DTOs

**Goal:** REST surface for submission and listing.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/proposal/ProposalController.java` (`POST`/`GET /api/v1/campaigns/{id}/proposals`), `CreateProposalRequest.java` (includes `sessionId`), `ProposalResponse.java`, `ProposalListResponse.java` (+ `@WebMvcTest`)

**Scope (out):** Voting/decision endpoints (F5.3.3/F5.4.4); exception mapping (F5.2.5).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-016, D-043, D-055  **Dependencies:** F5.2.2, F5.2.3

#### F5.2.5 — Error mapping for submission

**Goal:** Map proposal submission domain exceptions to the standard error envelope.

**Scope (in):**
- new proposal exceptions + handlers in `apps/api/src/main/java/com/bluesteel/adapters/in/web/GlobalExceptionHandler.java` (404 unknown target/session, 422 invalid delta) (+ handler test)

**Scope (out):** Vote/decision error codes (F5.3.3/F5.4.4); concurrent-rule code (F5.6.2).

**Skills:** `error-handling`, `backend-testing`  **Decisions:** D-016  **Dependencies:** F5.2.2

#### F5.3 — Backend: co-sign flow

> **Umbrella task — run the F5.3.N sub-tasks below, not this.**

A player other than the author seconds a proposal; at ≥1 co-sign the proposal becomes `cosigned`
and surfaces to the GM (D-017).

#### F5.3.1 — Driving port + command

**Goal:** Use-case interface and command for casting a co-sign vote.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/proposal/CoSignProposalUseCase.java`, `apps/api/src/main/java/com/bluesteel/application/model/proposal/CoSignProposalCommand.java`

**Scope (out):** Service logic (F5.3.2); endpoint (F5.3.3).

**Skills:** `backend-endpoint`  **Decisions:** D-017  **Dependencies:** F5.1.4

#### F5.3.2 — `ProposalCoSignService`

**Goal:** Apply a co-sign vote with the author and duplicate-vote rules, transitioning the proposal on the first co-sign.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalCoSignService.java` (+ unit test): writes a `proposal_votes('cosign')` row; allowed for **any non-author member that is not the GM** (D-017 — the GM decides; a GM co-sign would also consume their one vote slot and block the decision vote, 422 `GM_CANNOT_COSIGN`); author cannot co-sign own proposal (422); duplicate vote → 409 (backed by `uidx_proposal_votes_proposal_voter`); first co-sign transitions `open → cosigned`

**Scope (out):** `approve`/`reject` votes (F5.4 — GM only); endpoint/DTO (F5.3.3).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-017  **Dependencies:** F5.3.1, F5.1.6

#### F5.3.3 — Co-sign endpoint + DTO + error mapping

**Goal:** REST surface for co-signing, with conflict mapping.

**Scope (in):**
- `POST .../proposals/{pid}/votes` on `ProposalController` + `CastVoteRequest.java` + `GlobalExceptionHandler` mapping (author-cosign → 422, duplicate → 409) (+ `@WebMvcTest`)

**Scope (out):** GM decision route (F5.4.5).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** D-017  **Dependencies:** F5.3.2, F5.2.4

#### F5.4 — Backend: GM decision — approve applies delta, veto rejects

> **Umbrella task — run the F5.4.N sub-tasks below, not this.**

The GM approves (optionally editing the delta) or unilaterally vetoes a `cosigned` proposal (D-018).
Approval writes the effective delta as a **new entity version** through the existing world-state
write path, stamped with the campaign's **latest committed session id** (keeping
`version_number`↔session-sequence monotonic; the creator-selected `proposals.session_id` is
provenance only), and **publishes `SessionCommittedEvent`** so the new version is embedded and
visible to Query Mode. v2 scope is **actor/space targets only**.

#### F5.4.1 — Driving port + decision command/result

**Goal:** Use-case interface and command capturing the approve/reject decision and the optional GM-edited delta.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/proposal/DecideProposalUseCase.java`
- `apps/api/src/main/java/com/bluesteel/application/model/proposal/DecideProposalCommand.java` (decision + optional `editedDelta`), `ProposalDecisionResult.java` (`resultingEntityVersionId`)

**Scope (out):** Session lookup (F5.4.2); delta mapping (F5.4.3); service (F5.4.4); endpoint (F5.4.5).

**Skills:** `backend-endpoint`  **Decisions:** D-018, D-110  **Dependencies:** F5.1.4

#### F5.4.2 — Latest-committed-session lookup

**Goal:** Supply the session the approved version is stamped with, preserving monotonic version↔session ordering.

**Scope (in):**
- extend `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRepository.java` with `findLatestCommittedSessionId(campaignId)` + its query in `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java` (max `sequence_number` where `status='committed'`) (+ Testcontainers IT)

**Scope (out):** Delta mapping (F5.4.3); decision orchestration (F5.4.4).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-001, D-107  **Dependencies:** F5.1.4

#### F5.4.3 — `ProposalDeltaMapper`

**Goal:** Translate the effective delta into an `EntityWriteCommand` for the existing write path (actor/space only).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalDeltaMapper.java` (+ unit test): read the target's current head (`ownerId`, `name`) + latest `full_snapshot` via `WorldStateReadPort`; merge the effective delta (GM-edited if present, else `proposed_delta`) → `EntityWriteCommand` with `changed_fields` mirroring the delta, merged `full_snapshot`, and **`sessionId =` the latest committed session id (F5.4.2)**

**Scope (out):** Event/relation targets (deferred); orchestration (F5.4.4); endpoint (F5.4.5).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-001, D-076, D-107, D-108, D-110  **Dependencies:** F5.4.1, F5.4.2, F5.1.6

#### F5.4.4 — `ProposalDecisionService`

**Goal:** GM-only decision orchestration: approve writes a new (embedded) entity version and records the decision vote; veto rejects unilaterally.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalDecisionService.java` (+ unit test): GM-only via `CampaignMembershipPort`; `cosigned` precondition; `@Transactional`. **Approve** → `ProposalDeltaMapper` + `WorldStatePort.writeEntity` + **publish `SessionCommittedEvent(latestCommittedSessionId, campaignId, [version])`** (embeddings) + write `proposal_votes('approve')` + status `approved` + store `resulting_entity_version_id`. **Veto** → write `proposal_votes('reject')` + status `rejected`.

**Scope (out):** Endpoint/DTOs (F5.4.5); concurrent-proposal rule (F5.6).

**Skills:** `backend-endpoint`, `backend-domain-model`, `backend-testing`  **Decisions:** D-001, D-017, D-018, D-063, D-109  **Dependencies:** F5.4.3, F5.3.2

#### F5.4.5 — Decision endpoint + DTO + error mapping

**Goal:** GM-gated REST surface for the decision.

**Scope (in):**
- `POST .../proposals/{pid}/decision` on `ProposalController` + `DecideProposalRequest.java` (optional `editedDelta`) + GM gating + `GlobalExceptionHandler` mapping (+ `@WebMvcTest`)

**Scope (out):** Notifications (not in v2 scope anywhere — do not add).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** D-018, D-043  **Dependencies:** F5.4.4, F5.3.3

#### F5.5 — Backend: proposal TTL expiry scheduler

> **Umbrella task — run the F5.5.N sub-tasks below, not this.**

Proposals with no action within the configured TTL flip to `expired` automatically, keeping the GM
queue clean (D-019).

#### F5.5.1 — Expiry port + bulk-expiry adapter

**Goal:** Driven port and persistence adapter that flip stale `open`/`cosigned` proposals to `expired` in one statement.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/proposal/ProposalExpiryPort.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/proposal/ProposalExpiryAdapter.java` (bulk-flip `open|cosigned` past `expires_at` → `expired`) (+ Testcontainers IT)

**Scope (out):** The scheduler trigger (F5.5.2).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-019  **Dependencies:** F5.1.5

#### F5.5.2 — `ProposalExpiryScheduler`

**Goal:** Periodic sweep invoking the expiry port, patterned on `SessionTimeoutRecoveryScheduler`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalExpiryScheduler.java` (`@Scheduled`, `@Value`-bound env-overridable TTL + sweep interval) (+ unit test, mocked port)

**Scope (out):** Changing `expires_at` semantics (fixed by the gate decision).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-019, D-105  **Dependencies:** F5.5.1

#### F5.6 — Backend: concurrent-proposal conflict rule

> **Umbrella task — run the F5.6.N sub-tasks below, not this.**

Enforce the gate-decided rule for multiple open proposals targeting the same entity (e.g., block at
submission, or flag at GM decision time) so approvals never silently clobber each other.

#### F5.6.1 — Open-proposal lookup query

**Goal:** Repository support to detect an existing open/cosigned proposal for a target entity.

**Scope (in):**
- `existsOpenProposalForTarget(campaignId, targetType, targetId)` on `ProposalJpaRepository` + adapter wiring (+ Testcontainers IT)

**Scope (out):** Enforcement / error code (F5.6.2).

**Skills:** `backend-testing`  **Decisions:** D-106  **Dependencies:** F5.1.5

#### F5.6.2 — Enforce the conflict rule

**Goal:** Apply the gate-decided rule and surface a dedicated error code.

**Scope (in):**
- enforce the decided rule (block-at-submission in `ProposalCreationService` and/or flag at `ProposalDecisionService`) + new conflict exception + `GlobalExceptionHandler` 409 mapping (+ unit + handler tests)

**Scope (out):** Merge/rebase tooling for deltas (out of v2 scope entirely).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** D-106  **Dependencies:** F5.6.1, F5.4.4

#### F5.7 — Frontend: activate "Propose a change" + submission overlay

> **Umbrella task — run the F5.7-SETUP checklist then the F5.7.N sub-tasks below, not this.**

The disabled `ProposeChangeButton` stub (D-012) becomes live on every **actor/space** profile
(event/relation deferred — v2 target scope): a FocusedOverlay form captures the proposed field
changes (and the provenance session) and submits.

#### F5.7-SETUP — Human scaffolding (run once before F5.7.1)

> 👤 **Human step — not a pipeline sub-task.** Deterministic CLI scaffolding only. All other infra
> (Vite, Tailwind v4, TanStack Query, RHF, zod, `FocusedOverlay`, `InlineBanner`, `apiClient`,
> `createTestQueryClient`, `api/sessions.ts`) already exists.

```bash
cd apps/web
npx shadcn@latest add select --yes   # session/field picker → src/components/ui/select.tsx
```

#### F5.7.1 — `types/proposal.ts`

**Goal:** Hand-mirrored TypeScript types for the proposal DTOs (D-076).

**Scope (in):**
- `apps/web/src/types/proposal.ts`: `Proposal` (incl provenance `sessionId` + `resultingEntityVersionId`), `ProposalStatus`, `ProposalTargetType` (**`'actor' | 'space'`** — v2 scope), `VoteKind`, `CreateProposalRequest` (incl `sessionId`), `ProposalListPage` — mirrored from the F5.2.4 DTOs (verified by `npm run type-check`)

**Scope (out):** API client (F5.7.2); components (F5.7.3+).

**Skills:** `frontend-api-resource`  **Decisions:** D-076  **Dependencies:** F5.2.4

#### F5.7.2 — `api/proposals.ts` (create + list)

**Goal:** Typed client functions and TanStack hooks for submission and listing.

**Scope (in):**
- `apps/web/src/api/proposals.ts` (+ test): query-key factory, `getProposals`/`createProposal` via `apiClient`, `useProposals`/`useCreateProposal` hooks (invalidate on success)

**Scope (out):** Co-sign hook (F5.8.2); decide hook (F5.9.1).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-076  **Dependencies:** F5.7.1, F5.7-SETUP

#### F5.7.3 — `ProposalSubmitForm.tsx`

**Goal:** The submission form: RHF + zod with a session selector and field-change capture, with inline feedback.

**Scope (in):**
- `apps/web/src/components/domain/ProposalSubmitForm.tsx` (+ test): RHF+zod (ref `features/auth/LoginPage.tsx`), receives the entity's **current snapshot as a prop** (from `EntityProfileView` `data`) to render the editable field-delta, a provenance session `<Select>` ("which session does this relate to?") from `useSessions`, captures field changes into `proposed_delta` (mirroring `changed_fields`), submits via `useCreateProposal`, `InlineBanner` feedback (no toasts, D-083), maps `400` `field` errors

**Scope (out):** The button/overlay wiring (F5.7.4).

**Skills:** `react-hook-form`, `ux-inline-feedback`, `frontend-api-resource`, `frontend-testing`  **Decisions:** D-076, D-083  **Dependencies:** F5.7.2, F5.7-SETUP

#### F5.7.4 — Activate `ProposeChangeButton`

**Goal:** Replace the disabled stub with a live button that opens the submission form in a FocusedOverlay.

**Scope (in):**
- rewrite `apps/web/src/components/domain/ProposeChangeButton.tsx` (+ update test): live `Button` (drop the disabled stub) opening `FocusedOverlay` (no modals, D-082) hosting `ProposalSubmitForm`; accepts `targetType`/`targetId` **+ current snapshot** props; rendered only on actor/space profiles

**Scope (out):** Event/relation profiles (deferred — v2 target scope); browsing proposals (F5.8); GM actions (F5.9).

**Skills:** `ux-focused-overlay`, `frontend-testing`  **Decisions:** D-012, D-082  **Dependencies:** F5.7.3

#### F5.8 — Frontend: proposal list/detail on profiles + co-sign

> **Umbrella task — run the F5.8.N sub-tasks below, not this.**

Members see a proposal thread on each entity profile (status badges for the five states) and
players co-sign others' proposals.

#### F5.8.1 — `ProposalStatusBadge`

**Goal:** A badge mapping the five proposal states to shadcn `Badge` styling.

**Scope (in):**
- `apps/web/src/components/domain/ProposalStatusBadge.tsx` (+ test, axe)

**Scope (out):** The thread (F5.8.4).

**Skills:** `frontend-exploration`, `frontend-testing`  **Decisions:** D-017  **Dependencies:** F5.7.1

#### F5.8.2 — Co-sign hook

**Goal:** Extend the proposal API resource with the co-sign mutation.

**Scope (in):**
- extend `apps/web/src/api/proposals.ts` with `coSignProposal` + `useCoSignProposal` (+ test)

**Scope (out):** Decide hook (F5.9.1).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-017, D-076  **Dependencies:** F5.7.2

#### F5.8.3 — `ProposalThreadSkeleton`

**Goal:** DTO-derived skeleton for the proposal thread (D-086, no spinners).

**Scope (in):**
- `apps/web/src/components/domain/ProposalThreadSkeleton.tsx` (+ test): `animate-pulse`, dimensions from `types/proposal.ts`, zero layout shift

**Scope (out):** The thread itself (F5.8.4).

**Skills:** `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-086  **Dependencies:** F5.7.1

#### F5.8.4 — `ProposalThread`

**Goal:** Per-entity proposal list with status badges and a role/author-gated co-sign action.

**Scope (in):**
- `apps/web/src/components/domain/ProposalThread.tsx` (+ test): `useProposalsForTarget` (server-side entity filter), `ProposalStatusBadge`, co-sign button gated to **any non-author member that is not the GM** (D-017) via `useCoSignProposal`, `InlineBanner` feedback, `ProposalThreadSkeleton` while loading

**Scope (out):** Mounting it on the profile (F5.8.5); GM approve/veto (F5.9).

**Skills:** `frontend-exploration`, `ux-inline-feedback`, `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-017, D-076  **Dependencies:** F5.8.1, F5.8.2, F5.8.3

#### F5.8.5 — Mount the thread on the profile

**Goal:** Surface the proposal thread on the entity profile beside the annotation thread.

**Scope (in):**
- mount `<ProposalThread>` in `apps/web/src/features/exploration/components/EntityProfileView.tsx` (+ update test)

**Scope (out):** GM review queue (F5.9).

**Skills:** `frontend-exploration`, `frontend-testing`  **Decisions:** D-076  **Dependencies:** F5.8.4, F5.7.4

#### F5.9 — Frontend: GM review queue (approve/veto)

> **Umbrella task — run the F5.9.N sub-tasks below, not this.**

The GM gets a campaign-level queue of `cosigned` proposals and decides each one — approving (with the
option to edit the delta first) or vetoing; approval feedback links to the resulting entity version.

#### F5.9.1 — Decide + queue hooks

**Goal:** Extend the proposal API resource with the decision mutation and the cosigned-queue query.

**Scope (in):**
- extend `apps/web/src/api/proposals.ts`: `decideProposal` (optional `editedDelta`) + `useDecideProposal`, plus campaign-level `useCosignedProposals` (status filter) (+ test)

**Scope (out):** UI (F5.9.2+).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-018, D-076  **Dependencies:** F5.7.2

#### F5.9.2 — `ProposalReviewCard`

**Goal:** A single proposal with approve (editable) / veto actions and a link to the resulting version.

**Scope (in):**
- `apps/web/src/features/exploration/components/ProposalReviewCard.tsx` (+ test): approve opens a `FocusedOverlay` with an editable, pre-filled delta form (submits `editedDelta`); veto confirmation overlay; `InlineBanner` outcomes; on approve success, link to the resulting entity version via `resultingEntityVersionId`

**Scope (out):** The queue page (F5.9.3); routing (F5.9.4).

**Skills:** `ux-focused-overlay`, `ux-inline-feedback`, `react-hook-form`, `frontend-testing`  **Decisions:** D-018, D-076, D-082, D-083, D-110  **Dependencies:** F5.9.1, F5.7.3

#### F5.9.3 — `ProposalReviewQueuePage`

**Goal:** GM-gated campaign-level queue listing cosigned proposals.

**Scope (in):**
- `apps/web/src/features/exploration/components/ProposalReviewQueuePage.tsx` (+ test): GM-gated (`useCampaignStore(s => s.activeRole) === 'gm'`), `useCosignedProposals`, list of `ProposalReviewCard`, skeleton loading

**Scope (out):** Route/nav wiring (F5.9.4).

**Skills:** `frontend-exploration`, `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-018, D-086  **Dependencies:** F5.9.2

#### F5.9.4 — Route + GM-gated nav entry

**Goal:** Wire the queue into routing and the sidebar, visible to GMs only.

**Scope (in):**
- route in `apps/web/src/main.tsx` + GM-gated nav entry in `apps/web/src/components/domain/Sidebar.tsx` (+ update tests)

**Scope (out):** Player-facing views (F5.8).

**Skills:** `ux-navigation-logic`, `frontend-testing`  **Decisions:** D-018  **Dependencies:** F5.9.3

---

### Phase 6 — Input & Query Enhancements

**Purpose:** Close the two workflow gaps v1 consciously deferred — manually adding entities the AI
missed during diff review (D-053), and persisting Query Mode history (D-058) — plus the contingent
streaming path (D-052).

#### Summary

| # | Feature | Status |
|---|---|---|
| F6.1 | Backend: commit-payload `add` action *(umbrella)* | ✅ |
| F6.1.1 | Model: `AddedEntity` + `CommitPayload.addedEntities` + ARCHITECTURE §7.6 | ✅ |
| F6.1.2 | Web DTO `AddedEntityRequest` + map to payload in `SessionController` | ✅ |
| F6.1.3 | Validator: replace check #8 with `addedEntities` validation | ✅ |
| F6.1.4 | `CommitService` creates added entities + first versions (embeds) | ✅ |
| F6.1.5 | IT: commit with `addedEntities` end-to-end | ✅ |
| F6.2 | Frontend: "Add entity" affordance in diff review *(umbrella)* | ✅ |
| F6.2.1 | `types/session.ts` — `AddedEntityPayload` + `CommitPayload.addedEntities` | ✅ |
| F6.2.2 | `useDiffState` — track added entities | ✅ |
| F6.2.3 | `useCommitPayload` — emit `addedEntities` | ✅ |
| F6.2.4 | `AddEntityForm.tsx` — RHF+zod form + InlineBanner | ✅ |
| F6.2.5 | `AddedEntityCard.tsx` — new card category + remove | ✅ |
| F6.2.6 | `DiffReviewPage` — FocusedOverlay trigger + render + wiring | ✅ |
| F6.3 | Backend: Q&A log persistence *(umbrella)* | ✅ |
| F6.3.1 | Migration 0028 — `query_log` table + master include | ✅ |
| F6.3.2 | `QueryLogEntry` model + `QueryLogRepository` port | ✅ |
| F6.3.3 | `QueryLogJpaEntity` + JPA repo (IT) | ✅ |
| F6.3.4 | `QueryLogPersistenceAdapter` + retention bound (IT) | ✅ |
| F6.3.5 | `QueryService` persistence hook (success-only) | ✅ |
| F6.4 | Backend: Q&A log read API *(umbrella)* | ✅ |
| F6.4.1 | `GetQueryHistoryUseCase` port + page/result models | ✅ |
| F6.4.2 | `QueryHistoryService` — member auth, offset, newest-first | ✅ |
| F6.4.3 | `QueryController` `GET /history` + DTOs (no rate-limit consume) | ✅ |
| F6.5 | Frontend: Q&A history panel in Query Mode *(umbrella)* | ✅ |
| F6.5.1 | `types/query.ts` — `QueryHistoryEntry` + page meta | ✅ |
| F6.5.2 | `api/queries.ts` — `fetchQueryHistory` + `useQueryHistory` | ✅ |
| F6.5.3 | `QueryHistorySkeleton.tsx` — DTO-derived loading state | ✅ |
| F6.5.4 | `QueryHistoryPanel.tsx` — list + select answer/citations | ✅ |
| F6.5.5 | Wire panel into `QueryPage.tsx` | ✅ |
| F6.6 | Query streaming / SSE (contingent — latency evidence only) | 🔲 |

#### F6.1 — Backend: commit-payload `add` action

> **Umbrella task — run the F6.1.N sub-tasks below, not this.**

Reviewers can introduce an entity the extraction missed: the commit payload gains a dedicated
`addedEntities` list (the chosen representation — added entities have no stored-diff card, so
reusing `cardDecisions` would collide with validator checks #1–#3) that creates new entities +
first versions at commit, with the same campaign/session traceability as extracted ones. The
former `422 UNSUPPORTED_ACTION` rejection becomes positive validation of the new list.

#### F6.1.1 — Model: `AddedEntity` + `CommitPayload.addedEntities` + contract doc

**Goal:** Define the added-entity wire shape on the commit payload and record it in the canonical contract.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/commit/AddedEntity.java` — new record `{String entityType, String name, Map<String,Object> fields}` (camelCase, no `@JsonProperty`)
- extend `apps/api/src/main/java/com/bluesteel/application/model/commit/CommitPayload.java` with a fourth component `List<AddedEntity> addedEntities`
- update `docs/ARCHITECTURE.md` §7.6 CommitPayload contract to document `addedEntities` (D-076)
- (+ serialization unit test asserting the camelCase JSON round-trip)

**Scope (out):** Web DTO (F6.1.2); validation (F6.1.3); world-state write (F6.1.4).

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-053, D-076, D-001  **Dependencies:** v1 F2.x commit path

#### F6.1.2 — Web DTO `AddedEntityRequest` + map to payload

**Goal:** Accept added entities on the commit request DTO with format validation and map them into the application `CommitPayload`.

**Scope (in):**
- extend `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/CommitSessionRequest.java` with a nested `AddedEntityRequest(String entityType, String name, Map<String,Object> fields)` (Bean Validation: `@NotBlank` entityType + name) on a new `addedEntities` list
- map `addedEntities` into `CommitPayload` where the request is assembled in `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java`
- (+ `@WebMvcTest` covering the new field and its `400` on blank type/name)

**Scope (out):** Business-rule validation (F6.1.3); write path (F6.1.4).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-076  **Dependencies:** F6.1.1

#### F6.1.3 — Validator: replace check #8 with `addedEntities` validation

**Goal:** Turn the defensive `UNSUPPORTED_ACTION` check into positive validation of the added-entity list.

**Scope (in):**
- replace the `UNSUPPORTED_ACTION` switch (D-081 check #8) in `apps/api/src/main/java/com/bluesteel/application/service/session/CommitPayloadValidator.java` with validation of `payload.addedEntities`: known `entityType`, non-blank `name`, field contract → `422` `INVALID_ADDED_ENTITY` (the `CardAction` switch on `cardDecisions` stays as-is, ACCEPT/EDIT/DELETE only)
- (+ unit test for each failure mode + the happy path)

**Scope (out):** Creating the entities (F6.1.4).

**Skills:** `session-ingestion-pipeline`, `error-handling`, `backend-testing`  **Decisions:** D-053, D-078, D-081  **Dependencies:** F6.1.1

#### F6.1.4 — `CommitService` creates added entities + first versions

**Goal:** Write each added entity as a brand-new entity + first version at commit, stamped with the session, so it rides the same traceability and embedding path as extracted ones.

**Scope (in):**
- extend `apps/api/src/main/java/com/bluesteel/application/service/session/CommitService.java` to build an `EntityWriteCommand` per `addedEntities` entry (`entityId=null`, full snapshot from `fields`, `session.id()` stamp — mirrors the existing `NewEntityCard` branch) in the existing actor/space→event→relation write order, and add the resulting versions to the `SessionCommittedEvent` (drives async embeddings, D-063)
- (+ unit test with mocked `WorldStatePort` asserting the write commands + event contents)

**Scope (out):** End-to-end DB coverage (F6.1.5).

**Skills:** `session-ingestion-pipeline`, `backend-testing`  **Decisions:** D-001, D-063  **Dependencies:** F6.1.1, F6.1.3

#### F6.1.5 — IT: commit with `addedEntities` end-to-end

**Goal:** Prove a commit carrying `addedEntities` creates the entities + first versions against Postgres.

**Scope (in):**
- Testcontainers integration test exercising `POST .../sessions/{sid}/commit` (or the `CommitService` against a real DB) with an `addedEntities` payload, asserting the new `*` head rows + `*_versions` first versions exist with the session stamp

**Scope (out):** Frontend (F6.2).

**Skills:** `backend-testing`  **Decisions:** D-053  **Dependencies:** F6.1.2, F6.1.4

#### F6.2 — Frontend: "Add entity" affordance in diff review

> **Umbrella task — run the F6.2.N sub-tasks below, not this.**

An "Add entity" action on the diff review page opens a `FocusedOverlay` form (D-082); added entities
appear as a new card category and ride the commit payload via `addedEntities` (F6.1). Commit-button
gating is unchanged. All UI primitives already exist (`FocusedOverlay`, `InlineBanner`, shadcn
`select`/`input`/`textarea`/`form`/`button`, RHF+zod) — no SETUP required.

#### F6.2.1 — `types/session.ts` — `AddedEntityPayload` + `CommitPayload.addedEntities`

**Goal:** Mirror the F6.1 wire shape so the rest of the diff-review FE compiles against it (D-076).

**Scope (in):**
- `apps/web/src/types/session.ts` — add `AddedEntityPayload { entityType: EntityType; name: string; fields: Record<string, unknown> }` and `addedEntities: AddedEntityPayload[]` on `CommitPayload`

**Scope (out):** State (F6.2.2); payload assembly (F6.2.3); UI (F6.2.4–F6.2.6).

**Skills:** `frontend-api-resource`  **Decisions:** D-076  **Dependencies:** F6.1.1

#### F6.2.2 — `useDiffState` — track added entities

**Goal:** Hold the reviewer's added entities in the single diff-review state source.

**Scope (in):**
- extend `apps/web/src/features/input/hooks/useDiffState.ts` — an `addedEntities` slice + `addEntity`/`removeAddedEntity` reducer actions + a derived list (client-side id keying)
- (+ extend `useDiffState.test.ts`)

**Scope (out):** Serialization to the commit payload (F6.2.3).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-053  **Dependencies:** F6.2.1

#### F6.2.3 — `useCommitPayload` — emit `addedEntities`

**Goal:** Serialize the tracked added entities into the §7.6 commit payload.

**Scope (in):**
- extend `apps/web/src/features/input/hooks/useCommitPayload.ts` (`buildCommitPayload`) to map the `addedEntities` slice into `CommitPayload.addedEntities` (drop client-only ids)
- (+ extend `useCommitPayload.test.ts`)

**Scope (out):** The form/overlay (F6.2.4); card rendering (F6.2.5).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-076  **Dependencies:** F6.2.1, F6.2.2

#### F6.2.4 — `AddEntityForm.tsx` — RHF+zod form + InlineBanner

**Goal:** A form to capture a new entity (type, name, fields) with inline validation feedback.

**Scope (in):**
- `apps/web/src/features/input/components/AddEntityForm.tsx` — React Hook Form + zod; `entityType` shadcn `<Select>`, `name` `<Input>`, a simple key/value fields editor; `InlineBanner` for validation feedback (no toasts, D-083); `onAdd(entity)` / `onCancel` callbacks
- (+ `AddEntityForm.test.tsx` incl. axe assertion)

**Scope (out):** The overlay host + page wiring (F6.2.6).

**Skills:** `react-hook-form`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-082, D-083  **Dependencies:** F6.2.2

#### F6.2.5 — `AddedEntityCard.tsx` — new card category + remove

**Goal:** Render an added entity as its own diff card with a remove affordance.

**Scope (in):**
- `apps/web/src/features/input/components/AddedEntityCard.tsx` — shows the added entity's type/name/fields and a remove button wired to `removeAddedEntity`
- (+ `AddedEntityCard.test.tsx`)

**Scope (out):** Page composition (F6.2.6).

**Skills:** `frontend-diff-review`, `frontend-testing`  **Decisions:** D-007  **Dependencies:** F6.2.2

#### F6.2.6 — `DiffReviewPage` — FocusedOverlay trigger + render + wiring

**Goal:** Compose the affordance: button → overlay form → added-entity section, all feeding the commit payload.

**Scope (in):**
- `apps/web/src/features/input/DiffReviewPage.tsx` — an "Add entity" button that opens a `FocusedOverlay` hosting `AddEntityForm`; render added entities via `AddedEntityCard` in a new section; commit-button gating unchanged
- (+ extend `DiffReviewPage.test.tsx`)

**Scope (out):** Backend (F6.1).

**Skills:** `frontend-diff-review`, `ux-focused-overlay`, `frontend-testing`  **Decisions:** D-053, D-082  **Dependencies:** F6.2.3, F6.2.4, F6.2.5

#### F6.3 — Backend: Q&A log persistence

> **Umbrella task — run the F6.3.N sub-tasks below, not this.**

Successful queries are persisted (question, answer, citations, asker, timestamp) per campaign so the
table can revisit past answers (D-058 lifts v1 statelessness). An append-only log — modelled as an
application-model record + driven port + persistence adapter (no rich domain aggregate; minimal
invariants) — with an env-overridable per-campaign retention bound. Failures and timeouts are never
logged, and a log-write failure must never fail the query.

#### F6.3.1 — Migration 0028 — `query_log` table + master include

**Goal:** Append-only campaign-scoped query-log table.

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0028_create_query_log.xml` — `query_log` (`id UUID PK`, `campaign_id UUID` FK → `campaigns(id)` ON DELETE CASCADE, `asker_id UUID` FK → `users(id)`, `question text`, `answer text`, `citations jsonb`, `created_at timestamptz`); index `(campaign_id, created_at DESC)`. Register the `<include>` in `db.changelog-master.xml`.

**Scope (out):** Any Java code (F6.3.2+).

**Skills:** `database-migration`  **Decisions:** D-058  **Dependencies:** v1 F3.x query pipeline

#### F6.3.2 — `QueryLogEntry` model + `QueryLogRepository` port

**Goal:** The shared read/write record and the driven persistence port every later task depends on.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/query/QueryLogEntry.java` — record `{UUID id, UUID campaignId, UUID askerId, String question, String answer, List<Citation> citations, Instant createdAt}` (compact-constructor non-blank `question`/`answer`)
- `apps/api/src/main/java/com/bluesteel/application/port/out/query/QueryLogRepository.java` — `save`, `findByCampaign(campaignId, offset, limit)`, `countByCampaign`, and a retention prune (`deleteOldestBeyond(campaignId, maxRows)`)
- (+ model unit test for the invariants)

**Scope (out):** JPA + adapter (F6.3.3, F6.3.4); the service hook (F6.3.5).

**Skills:** `query-pipeline`, `backend-testing`  **Decisions:** D-058  **Dependencies:** F6.3.1

#### F6.3.3 — `QueryLogJpaEntity` + JPA repo

**Goal:** JPA entity + Spring Data repository over the `query_log` table.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/query/QueryLogJpaEntity.java` (`@JdbcTypeCode(SqlTypes.JSON)` for `citations`) + `QueryLogJpaRepository.java` (campaign-scoped, ordered, paged finder + count)
- (+ repository Testcontainers IT)

**Scope (out):** The domain↔JPA mapper / port impl (F6.3.4).

**Skills:** `backend-testing`  **Decisions:** D-058  **Dependencies:** F6.3.1

#### F6.3.4 — `QueryLogPersistenceAdapter` + retention bound

**Goal:** Adapter implementing `QueryLogRepository` by mapping record ↔ JPA, enforcing the retention bound, validated against Postgres.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/query/QueryLogPersistenceAdapter.java` — maps `citations` ↔ JSONB, implements paged find/count/save, and prunes beyond an env-overridable per-campaign max (`query.history.max-per-campaign`)
- (+ Testcontainers IT covering save/find/prune)

**Scope (out):** The service hook (F6.3.5).

**Skills:** `backend-testing`  **Decisions:** D-058, D-096  **Dependencies:** F6.3.2, F6.3.3

#### F6.3.5 — `QueryService` persistence hook (success-only)

**Goal:** Persist a log entry after a successful grounded answer, without ever affecting the answer path.

**Scope (in):**
- extend `apps/api/src/main/java/com/bluesteel/application/service/query/QueryService.java` — after `withGroundedCitations(...)` succeeds, build + `save` a `QueryLogEntry`; timeouts/cost-cap/failures are not logged; a log-write failure is caught + logged (ERROR) and not propagated
- (+ unit test: `ArgumentCaptor` asserts the saved entry; `verify(..., never())` on timeout/cost-cap paths)

**Scope (out):** Read API (F6.4); UI (F6.5).

**Skills:** `query-pipeline`, `backend-testing`  **Decisions:** D-058, D-096  **Dependencies:** F6.3.2

#### F6.4 — Backend: Q&A log read API

> **Umbrella task — run the F6.4.N sub-tasks below, not this.**

Campaign members browse the campaign's Q&A history, newest first, via
`GET /api/v1/campaigns/{id}/queries/history` (member auth; offset pagination; read-only — does not
consume the query rate limit, same as `GET /queries/usage`). The log is append-only — no
delete/edit.

#### F6.4.1 — `GetQueryHistoryUseCase` port + page/result models

**Goal:** Driving port + offset-paged result models for reading the log.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/query/GetQueryHistoryUseCase.java`
- offset page/result records in `apps/api/src/main/java/com/bluesteel/application/model/query/` (page request + a page wrapper over `QueryLogEntry`)

**Scope (out):** Service (F6.4.2); controller (F6.4.3).

**Skills:** `backend-endpoint`  **Decisions:** D-058, D-055  **Dependencies:** F6.3.2

#### F6.4.2 — `QueryHistoryService` — member auth, offset, newest-first

**Goal:** Use-case implementation enforcing membership and returning newest-first paged history.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/query/QueryHistoryService.java` — resolve caller membership (`401`/`403` via `CampaignMembershipPort`), read `QueryLogRepository.findByCampaign` + `countByCampaign`, newest-first
- (+ unit test with mocked ports)

**Scope (out):** HTTP wiring (F6.4.3).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-058, D-043  **Dependencies:** F6.4.1, F6.3.2

#### F6.4.3 — `QueryController` `GET /history` + DTOs

**Goal:** Expose the history endpoint, read-only and rate-limit-free.

**Scope (in):**
- add `@GetMapping("/history")` to `apps/api/src/main/java/com/bluesteel/adapters/in/web/query/QueryController.java` (offset `page`/`size` params; member auth; does **not** call `rateLimiter.check` — mirrors `GET /usage`) + response DTO(s) (history entry incl. citations, page `meta`)
- (+ `@WebMvcTest`)

**Scope (out):** Deleting/editing entries (out of scope — append-only).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-058, D-043  **Dependencies:** F6.4.2

#### F6.5 — Frontend: Q&A history panel in Query Mode

> **Umbrella task — run the F6.5.N sub-tasks below, not this.**

A history panel inside Query Mode lists past Q&As; selecting one shows its answer and citations
(citations keep linking to session detail, reusing `AnswerDisplay`/`CitationList`). Loading uses a
DTO-derived skeleton (no spinners, D-086); the current-answer flow (v1 F3.4) stays unchanged.
Re-submitting a past question is out of scope (not in D-058). No SETUP required.

#### F6.5.1 — `types/query.ts` — `QueryHistoryEntry` + page meta

**Goal:** Mirror the F6.4 read-API shape (D-076).

**Scope (in):**
- `apps/web/src/types/query.ts` — `QueryHistoryEntry { id: string; question: string; answer: string; citations: Citation[]; createdAt: string }` + an offset page-meta type

**Scope (out):** Fetching (F6.5.2); UI (F6.5.3–F6.5.5).

**Skills:** `frontend-api-resource`  **Decisions:** D-058, D-076  **Dependencies:** F6.4.3

#### F6.5.2 — `api/queries.ts` — `fetchQueryHistory` + `useQueryHistory`

**Goal:** Typed resource + hook for the offset-paginated history (genuine server state, unlike the stateless query mutation).

**Scope (in):**
- extend `apps/web/src/api/queries.ts` — `queryHistoryKey(campaignId, page)`, `fetchQueryHistory(campaignId, page)`, `useQueryHistory(campaignId, page)` (offset pagination; `enabled` once a campaign is selected)
- (+ extend the `queries` test with a mocked fetch)

**Scope (out):** Panel + skeleton (F6.5.3, F6.5.4).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-058  **Dependencies:** F6.5.1

#### F6.5.3 — `QueryHistorySkeleton.tsx` — DTO-derived loading state

**Goal:** Zero-layout-shift skeleton matching the history list (D-086).

**Scope (in):**
- `apps/web/src/features/query/components/QueryHistorySkeleton.tsx` — `animate-pulse` blocks derived from `QueryHistoryEntry` dimensions
- (+ `QueryHistorySkeleton.test.tsx`)

**Scope (out):** The panel that uses it (F6.5.4).

**Skills:** `ux-skeleton-crafting`, `frontend-testing`  **Decisions:** D-086  **Dependencies:** F6.5.1

#### F6.5.4 — `QueryHistoryPanel.tsx` — list + select answer/citations

**Goal:** The panel: a newest-first list whose selection surfaces the entry's answer + citations.

**Scope (in):**
- `apps/web/src/features/query/components/QueryHistoryPanel.tsx` — `useQueryHistory` list; `QueryHistorySkeleton` while loading; selecting an entry renders its answer + citations via the existing `AnswerDisplay`/`CitationList`
- (+ `QueryHistoryPanel.test.tsx` incl. axe assertion)

**Scope (out):** Mounting into the page (F6.5.5).

**Skills:** `frontend-query-mode`, `frontend-testing`  **Decisions:** D-058, D-086  **Dependencies:** F6.5.2, F6.5.3

#### F6.5.5 — Wire panel into `QueryPage.tsx`

**Goal:** Surface the history panel in Query Mode without disturbing the live-answer flow.

**Scope (in):**
- `apps/web/src/features/query/QueryPage.tsx` — mount `QueryHistoryPanel`; the current-answer (v1 F3.4) flow unchanged
- (+ extend `QueryPage.test.tsx`)

**Scope (out):** Re-submitting a past question (not in D-058 scope).

**Skills:** `frontend-query-mode`, `frontend-testing`  **Decisions:** D-058  **Dependencies:** F6.5.4

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

**Phase 7 Gate — resolved; recorded as D-112 in DECISIONS.md before F7.1 starts (run F7-GATE):**

- [x] Scope tension resolved: the narrower pre-deletion export proceeds in v2; PRD §7 "public sharing / sharable links" stays post-v2 (D-112)
- [x] Format = single **structured JSON archive** (campaign + members + actors/spaces/events/relations with full version history + annotations + sessions); download = raw archive JSON as a file attachment (`Content-Disposition: attachment`), **not** the `{data,meta,errors}` envelope; authorization = **GM or admin** (D-112)

#### Summary

| # | Feature | Status |
|---|---|---|
| F7-GATE | 👤 Human: record export format + GM/admin authz as D-112 in DECISIONS.md | ✅ |
| F7.1 | Backend: campaign export endpoint | 🔲 |
| F7.1.1 | Archive model records for the campaign export tree | 🔲 |
| F7.1.2 | CampaignExportReadPort + native-SQL bulk-read adapter (+IT) | 🔲 |
| F7.1.3 | ExportCampaignUseCase + ExportCampaignService (GM/admin authz, size cap) | 🔲 |
| F7.1.4 | CampaignExportController — raw-JSON attachment download (+WebMvc test) | 🔲 |
| F7.2 | Frontend: export affordance in campaign danger zone | 🔲 |
| F7.2.1 | apiClient.download() — blob + Content-Disposition filename (+test) | 🔲 |
| F7.2.2 | api/campaigns.ts exportCampaign + useExportCampaign (+test) | 🔲 |
| F7.2.3 | CampaignExportButton — pending state, file save, InlineBanner (+test) | 🔲 |
| F7.2.4 | Wire export into CampaignHomePage danger zone (GM+admin gating) | 🔲 |

#### F7-GATE — Human: record the Phase 7 Gate decision (run once before F7.1.1)

> 👤 **Human step — not a pipeline sub-task.** Add **D-112** to `docs/DECISIONS.md` capturing:
> export format = structured JSON archive; download = raw archive JSON file attachment (not the
> response envelope); authorization = GM or admin; PRD §7 public/sharable links remain post-v2.
> Check the two Phase 7 Gate boxes above. No code or CLI scaffolding.

#### F7.1 — Backend: campaign export endpoint

> **Umbrella task — run the F7.1.N sub-tasks below, not this.**

One endpoint assembles a campaign's complete dataset (members, actors/spaces/events/relations with
full version history, annotations, sessions) into a single downloadable JSON archive, authorized to
GM or admin.

> ⚠️ **Memory budget — Render free tier is ~512 MB RAM (heap << that after the JVM + app baseline).**
> Export is the one "load the whole campaign at once" path in the system, and the dominant heap cost
> is the **full JSONB snapshot stored per entity version** (N entities × M versions × snapshot size).
> Three mitigations, split across the sub-tasks below, must all be present:
> 1. **Fail-fast cap** (F7.1.3) — a cheap `COUNT` precheck rejects oversized campaigns with
>    `422 EXPORT_TOO_LARGE` *before* any rows are materialized; `campaign.export.max-entities` is
>    env-overridable (raise from the Render dashboard, never re-implement — `apps/api/CLAUDE.md` §7).
> 2. **Bounded reads** (F7.1.2) — set-based queries (no N+1) with an explicit JDBC fetch size so the
>    driver streams rows instead of buffering the whole result set client-side.
> 3. **Streaming serialization** (F7.1.4) — write JSON straight to the response `OutputStream` via
>    `StreamingResponseBody` + `ObjectMapper.writeValue(outputStream, …)`; never build a full
>    `byte[]`/`String` of the archive, and never let the converter pre-buffer for `Content-Length`.

#### F7.1.1 — Archive model records (`application/model/campaign/`)

**Goal:** Provider-neutral value records describing the export archive tree the read port returns
and the service assembles (no Spring/JPA/web imports — ARCH-01/ARCH-07).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/model/campaign/CampaignArchive.java` — top record:
  campaign metadata, members, world-state entities (by type, each with ordered version history),
  annotations, sessions, and an `exportedAt` / schema-version marker
- supporting nested records as needed (e.g. `ArchivedEntity` { type, id, name, ownerId, versions[] },
  `ArchivedEntityVersion`, `ArchivedAnnotation`, `ArchivedSession`) in the same package
- (+ a small domain unit test asserting record construction / null-handling if any invariant exists)

**Scope (out):** Persistence reads (F7.1.2); orchestration (F7.1.3); web serialization (F7.1.4).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-112, D-001, ARCH-07  **Dependencies:** F7-GATE

#### F7.1.2 — `CampaignExportReadPort` + native-SQL bulk-read adapter

**Goal:** One driven port + native-SQL adapter that bulk-reads everything an archive needs for a
campaign — all actors/spaces/events/relations with their full ordered version history, plus
annotations and sessions — in bounded queries (heavy lifting in SQL, D-062), returning F7.1.1
model records.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/out/campaign/CampaignExportReadPort.java` —
  `long countEntities(UUID campaignId)` (cheap cap precheck, mitigation 1) **plus** the bulk-read
  methods returning F7.1.1 records (per-section methods, or one `readForExport`)
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapter.java` —
  `JdbcTemplate` + `ObjectMapper` (JSONB), few set-based queries (heads + all versions per type
  joined by entity id, ordered by version_number), **no N+1**; set an explicit fetch size
  (`JdbcTemplate.setFetchSize` / statement fetch size) so the driver streams rather than buffering
  the full result set (mitigation 2)
- (+ Testcontainers IT seeding a campaign with multi-version entities + annotations + sessions and
  asserting the assembled data + `countEntities`)

**Scope (out):** Authorization + cap-exceeded error (F7.1.3); campaign/member reads (reuse existing
`CampaignRepository`/`CampaignMembershipRepository` in F7.1.3); web layer + streaming serialization (F7.1.4).

> ⚠️ **Memory:** the per-version full JSONB snapshot is the heap multiplier — keep the queries
> set-based and the fetch size bounded; do not eager-load related entities or re-read per row.

**Skills:** `database-migration`, `backend-testing`, `security-hardening`  **Decisions:** D-112, D-062, D-001  **Dependencies:** F7.1.1, F7-GATE

#### F7.1.3 — `ExportCampaignUseCase` + `ExportCampaignService`

**Goal:** Driving port + service that authorizes (GM via `resolveRole`, or admin) and composes the
campaign, members, and the F7.1.2 bulk reads into a `CampaignArchive`, enforcing an env-overridable
entity cap.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/campaign/ExportCampaignUseCase.java` —
  `CampaignArchive export(UUID campaignId, UUID callerId, boolean callerIsAdmin)`
- `apps/api/src/main/java/com/bluesteel/application/service/campaign/ExportCampaignService.java` —
  `CampaignNotFoundException` if absent; `UnauthorizedException` unless admin or
  `resolveRole == GM` (D-043); reuses `CampaignRepository` + `CampaignMembershipRepository`
  + `CampaignExportReadPort`. **Order matters for memory:** authorize → `countEntities` → if it
  exceeds `campaign.export.max-entities` (env-overridable, conservative default sized for ~512 MB
  Render free tier) throw `422 EXPORT_TOO_LARGE` **before** loading any rows (mitigation 1) → only
  then assemble the bounded archive
- (+ application unit test, mocked ports, real expected values per TEST-02: GM allowed, editor/player
  rejected, admin allowed, not-found, **cap-exceeded throws before any bulk read is invoked**)

**Scope (out):** Controller + HTTP/download + streaming serialization (F7.1.4); the SQL itself (F7.1.2).

**Skills:** `backend-endpoint`, `security-hardening`, `backend-testing`  **Decisions:** D-112, D-043, D-001  **Dependencies:** F7.1.2

#### F7.1.4 — `CampaignExportController` — raw-JSON attachment download

**Goal:** `GET /api/v1/campaigns/{id}/export` streams the archive as a downloadable
`application/json` attachment (raw archive, **not** the response envelope), with a
`Content-Disposition: attachment; filename="<campaign>-export.json"` header, writing JSON directly
to the response output stream so no full copy is buffered in heap (mitigation 3).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/campaign/CampaignExportController.java` —
  resolves `callerId` + admin authority from the security context (mirroring `CampaignController`),
  delegates to `ExportCampaignUseCase`, and returns a `StreamingResponseBody` that serializes the
  archive with `ObjectMapper.writeValue(outputStream, response)` (streams to the socket, no
  `byte[]`/`String`, no `Content-Length` pre-buffer); sets the `Content-Disposition` +
  `application/json` headers; 403/404/422 flow through `GlobalExceptionHandler` (thrown by the use
  case **before** streaming begins, so the error envelope is still emitted cleanly)
- optional `CampaignExportResponse` web record in the same package if the model must not leak directly
- (+ `@WebMvcTest` slice: 200 + `Content-Disposition` + streamed body for GM/admin, 403 for
  non-member/player, 404; assert the cap path surfaces `422` before any body is written)

**Scope (out):** Chunked/multipart or ZIP-per-entity transfer (deferred — the cap + streaming
serialization bound memory for v2; revisit only if the cap proves too small, D-112); import (post-v2).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-112, D-043  **Dependencies:** F7.1.3

#### F7.2 — Frontend: export affordance in campaign danger zone

> **Umbrella task — run the F7.2.N sub-tasks below, not this.**

The campaign home danger zone offers "Export campaign" (visible to GM + admin, beside the
admin-only delete), downloading the JSON archive with a clear pending state and `InlineBanner`
success/failure feedback. Export is non-destructive → no confirmation overlay, no skeleton. No SETUP
required (no new shadcn components or packages).

#### F7.2.1 — `apiClient.download()` — blob + Content-Disposition filename

**Goal:** Add a download transport to the shared client that fetches a binary/file response (no
`Content-Type: application/json` forcing, reads `blob()`), parses the `Content-Disposition`
filename, and reuses the existing 401 silent-refresh path.

**Scope (in):**
- extend `apps/web/src/api/client.ts` — `apiClient.download(path): Promise<{ blob: Blob; filename: string }>`
  built on the existing auth/refresh machinery (factor a shared helper if cleaner); parse the
  filename from `Content-Disposition`, fall back to a default
- (+ extend `apps/web/src/api/client.test.ts` — mocked fetch returning a blob + header; assert
  filename parse + that a 401 triggers one refresh+retry)

**Scope (out):** The campaign-specific call + hook (F7.2.2); browser save (F7.2.3).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-112  **Dependencies:** F7.1.4

#### F7.2.2 — `api/campaigns.ts` — `exportCampaign` + `useExportCampaign`

**Goal:** Typed resource fn + TanStack mutation hook for the export download (no cache invalidation —
read-only).

**Scope (in):**
- extend `apps/web/src/api/campaigns.ts` — `exportCampaign(id): Promise<{ blob; filename }>` via
  `apiClient.download(\`/api/v1/campaigns/${id}/export\`)`; `useExportCampaign()` `useMutation`
  returning the blob+filename (no `onSuccess` invalidation)
- (+ extend `apps/web/src/api/campaigns.test.ts` — mocked `apiClient.download`, assert hook success
  payload + error surfacing)

**Scope (out):** The DOM file-save + button UI (F7.2.3); page wiring (F7.2.4).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-112  **Dependencies:** F7.2.1

#### F7.2.3 — `CampaignExportButton.tsx` — pending state, file save, InlineBanner

**Goal:** The danger-zone button: triggers `useExportCampaign`, saves the returned blob to disk, and
shows inline success/error feedback with a pending ("Exporting…") state.

**Scope (in):**
- `apps/web/src/features/campaigns/components/CampaignExportButton.tsx` — `Button` calling
  `useExportCampaign().mutate`; on success saves via a small `downloadBlob(blob, filename)` helper
  (createObjectURL → anchor click → revoke), then `InlineBanner variant="success"`; on error
  `InlineBanner variant="error"`; disabled + "Exporting…" while pending (no toast D-083, no modal,
  no skeleton)
- `apps/web/src/lib/downloadBlob.ts` — the pure DOM save helper
- (+ `CampaignExportButton.test.tsx` incl. axe; mock the hook + `URL.createObjectURL`/anchor)

**Scope (out):** Placement + role gating in the page (F7.2.4).

**Skills:** `frontend-api-resource`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-112, D-083  **Dependencies:** F7.2.2

#### F7.2.4 — Wire export into `CampaignHomePage` danger zone (GM + admin gating)

**Goal:** Mount `CampaignExportButton` in the danger zone for GM **and** admin, while the existing
delete stays admin-only.

**Scope (in):**
- `apps/web/src/features/campaigns/CampaignHomePage.tsx` — render the danger-zone section when
  `isAdmin || campaign.role === 'gm'`; show `CampaignExportButton` to GM+admin and keep the delete
  button/overlay gated to `isAdmin` only
- (+ extend `CampaignHomePage.test.tsx` — export visible for GM and admin, hidden for editor/player;
  delete still admin-only)

**Scope (out):** The button internals (F7.2.3); backend (F7.1.*).

**Skills:** `frontend-api-resource`, `ux-navigation-logic`, `frontend-testing`  **Decisions:** D-112, D-043  **Dependencies:** F7.2.3

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
| F8.1 | Backend: user profile/settings persistence + domain (umbrella) | ✅ |
| F8.1.1 | Backend: Liquibase 0029 — profile/settings columns on `users` | ✅ |
| F8.1.2 | Backend: extend `User` aggregate + `UserProfile` read model | ✅ |
| F8.1.3 | Backend: `UserJpaEntity` columns + `UserPersistenceAdapter` mapping | ✅ |
| F8.2 | Backend: `GET /me` extension + `PATCH /me` profile/settings API (umbrella) | ✅ |
| F8.2.1 | Backend: `UpdateCurrentUserProfileUseCase` + command + service | ✅ |
| F8.2.2 | Backend: `PATCH /me` + `UpdateProfileRequest` + `UserMeResponse` fields | ✅ |
| F8.3 | Frontend: settings store + API hooks + DTO types (umbrella) | ✅ |
| F8.3.1 | Frontend: profile/settings DTO + `Theme`/`UiLocale` types | ✅ |
| F8.3.2 | Frontend: `useSettingsStore` (Zustand persist) + login hydration | ✅ |
| F8.3.3 | Frontend: `updateProfile()` + `useUpdateProfile()` (refetch `/me` → authStore + store) | ✅ |
| F8.4 | Frontend: top-right account menu (header redesign) (umbrella) | ✅ |
| F8.4-SETUP | Human: `npx shadcn@latest add dropdown-menu` | ✅ |
| F8.4.1 | Frontend: `InitialsAvatar` (initials + accent) | ✅ |
| F8.4.2 | Frontend: `UserMenu` dropdown (name/email, Settings, theme + EN/ES, Log out) | ✅ |
| F8.4.3 | Frontend: wire `UserMenu` into `AppBar`; retire Sidebar Settings stub | ✅ |
| F8.5 | Frontend: User Settings page (`/settings`) (umbrella) | ✅ |
| F8.5.1 | Frontend: accent palette + `AccentColorPicker` | ✅ |
| F8.5.2 | Frontend: `UserSettingsPage` (form + live preview + InlineBanner) | ✅ |
| F8.5.3 | Frontend: global `/settings` route wiring | ✅ |
| F8.6 | Frontend: i18n infrastructure + EN/ES catalogs (umbrella) | ✅ |
| F8.6-SETUP | Human: `npm install i18next react-i18next` | 👤 |
| F8.6.1 | Frontend: i18next init + provider driven by `useSettingsStore.uiLocale` | ✅ |
| F8.6.2 | Frontend: EN/ES catalogs + extract Sidebar/AppBar/UserMenu strings | ✅ |
| F8.7 | Frontend: dark-mode theme system (umbrella) | ✅ |
| F8.7.1 | Frontend: `.dark` CSS-variable overrides in `index.css` | ✅ |
| F8.7.2 | Frontend: theme-apply hook (toggle `<html class="dark">`, `system` via matchMedia) | ✅ |
| F8.7.3 | Frontend: no-flash pre-paint script in `index.html` | ✅ |
| F8.8 | Frontend: migrate raw color utilities → semantic tokens for full dark-mode coverage (non-blocking follow-on) | ✅ |
| F8.9 | Frontend: i18n per-page string extraction — remaining write/admin/overlay surfaces (F8.6 follow-on) | ✅ |
| F8.10 | Repo: decide + enforce repo-wide Prettier formatting (CI `--check`) | ✅ |

#### F8.1 — Backend: user profile/settings persistence + domain

> **Umbrella task — run the F8.1.N sub-tasks below, not this.** No SETUP (hand-written migration).

Persist the four new profile/settings fields (`display_name`, `avatar_accent_color`, `ui_locale`,
`theme`) so every later task reads and writes a stable user model. Split into schema (F8.1.1),
pure-domain model (F8.1.2), and persistence mapping (F8.1.3).

**Acceptance (whole task):**
- A `users` row created before the migration loads afterward with `ui_locale='en'` and `theme='system'` (defaults applied), `display_name`/`avatar_accent_color` null.
- The domain user model round-trips all four fields through persistence (write → read returns the same values).

#### F8.1.1 — Liquibase 0029: profile/settings columns on `users`

**Goal:** Append-only migration adding the four columns so later tasks read a stable schema.

**Scope (in):**
- `apps/api/src/main/resources/db/changelog/0029_add_user_profile_settings.xml` — `<addColumn tableName="users">` with `display_name TEXT NULL`, `avatar_accent_color TEXT NULL`, `ui_locale TEXT NOT NULL DEFAULT 'en'`, `theme TEXT NOT NULL DEFAULT 'system'`; include a `<rollback>` dropping the columns
- register the `<include file="db/changelog/0029_add_user_profile_settings.xml"/>` in `apps/api/src/main/resources/db/changelog/db.changelog-master.xml`
- (+ Testcontainers IT seeding a `users` row, then asserting it loads with `ui_locale='en'`, `theme='system'`, and `display_name`/`avatar_accent_color` null)

**Scope (out):** Entity/domain/mapper (F8.1.2/F8.1.3); API (F8.2).

**Skills:** `database-migration`, `backend-testing`  **Decisions:** D-100, D-101  **Dependencies:** —

#### F8.1.2 — `User` aggregate + `UserProfile` read model

**Goal:** Carry the four fields through the pure-domain model with an immutable update mutator.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/domain/user/User.java` — add the four fields, extend the `create(...)` factory, and add `withUpdatedProfile(displayName, avatarAccentColor, uiLocale, theme)` returning a new `User` (mirroring the existing `withUpdatedPassword` shape)
- `apps/api/src/main/java/com/bluesteel/application/model/user/UserProfile.java` — add `displayName`, `avatarAccentColor`, `uiLocale`, `theme` to the record
- (+ domain unit test: `withUpdatedProfile` sets the four fields and leaves id/email/passwordHash/etc. untouched)

**Scope (out):** JPA/mapper (F8.1.3); service + web (F8.2).

**Skills:** `backend-testing`  **Decisions:** D-100, D-101  **Dependencies:** — *(pure Java; no Spring/DB)*

#### F8.1.3 — `UserJpaEntity` columns + `UserPersistenceAdapter` mapping

**Goal:** Persist and read the four fields end-to-end.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/user/UserJpaEntity.java` — add the four `@Column`s, extend the constructor, add package-private getters
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/user/UserPersistenceAdapter.java` — map the four fields in both `toDomain` and `toEntity`
- (+ persistence IT: save a `User` carrying profile values → `findById` returns the same four values)

**Scope (out):** API (F8.2).

**Skills:** `database-migration`, `backend-testing`  **Decisions:** D-100, D-101  **Dependencies:** F8.1.1, F8.1.2

#### F8.2 — Backend: `GET /me` extension + `PATCH /me` profile/settings API

> **Umbrella task — run the F8.2.N sub-tasks below, not this.** No SETUP. (400 validation rides the
> existing `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` — **no handler change
> needed**; the Bean-Validation annotations on the request DTO are sufficient.)

Expose the profile/settings on the current-user endpoint and let the user update them. Split into the
application layer (F8.2.1) and the HTTP boundary (F8.2.2).

**Acceptance (whole task):**
- `GET /me` returns the four new fields for the authenticated user.
- `PATCH /me` with valid values persists them; a subsequent `GET /me` reflects the change.
- Invalid `uiLocale`, `theme`, or accent-color → `400` with a field-level error; one user's update never affects another's settings.

#### F8.2.1 — `UpdateCurrentUserProfileUseCase` + command + service

**Goal:** Application-layer update mirroring the `ChangePassword*` shape.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/port/in/user/UpdateCurrentUserProfileUseCase.java` — `void update(UpdateProfileCommand command)`
- `apps/api/src/main/java/com/bluesteel/application/model/user/UpdateProfileCommand.java` — `record(UUID callerId, String displayName, String avatarAccentColor, String uiLocale, String theme)`
- `apps/api/src/main/java/com/bluesteel/application/service/user/UpdateCurrentUserProfileService.java` — `@Service @Transactional`: load by `callerId` → `UserNotFoundException` if absent → `user.withUpdatedProfile(...)` → `userRepository.save(...)`
- (+ application unit test, mocked `UserRepository`, real expected values per TEST-02: persists updated profile; not-found throws)

**Scope (out):** HTTP boundary (F8.2.2).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-100, D-101, D-043  **Dependencies:** F8.1.2

#### F8.2.2 — `PATCH /me` + `UpdateProfileRequest` + `UserMeResponse` fields

**Goal:** Expose the four fields on `GET /me` and accept updates on `PATCH /me`.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/user/UpdateProfileRequest.java` — `displayName` optional `@Size`; `uiLocale` `@Pattern("^(en|es)$")`; `theme` `@Pattern("^(light|dark|system)$")`; `avatarAccentColor` nullable hex `@Pattern`
- extend `apps/api/src/main/java/com/bluesteel/adapters/in/web/user/UserMeResponse.java` with `displayName`, `avatarAccentColor`, `uiLocale`, `theme`
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/user/UserController.java` — new `@PatchMapping` on `/api/v1/users/me` mapping `UpdateProfileRequest` + security-context `callerId` to `UpdateProfileCommand` → `UpdateCurrentUserProfileUseCase`; extend the `getMe()` response mapping with the four fields
- (+ `@WebMvcTest` slice: GET returns the four fields; PATCH valid → 200 + persists; invalid `uiLocale`/`theme`/hex → 400 field-level error)

**Scope (out):** UI (F8.3+).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** D-100, D-101, D-043  **Dependencies:** F8.2.1, F8.1.3

#### F8.3 — Frontend: settings store + API hooks + DTO types

> **Umbrella task — run the F8.3.N sub-tasks below, not this.** No SETUP (`zustand` + `@tanstack/react-query` already installed).

Client-side settings state synced to the server, with a localStorage mirror for no-flash first paint.
**Correction to the original framing:** `/me` is fetched imperatively in `api/auth.ts` (`getCurrentUser()`,
called inside `useLogin`) and stored in `store/authStore.ts` `currentUser` — there is **no TanStack `/me`
query to invalidate**. So the update hook re-fetches `/me` on success and writes `authStore` +
`settingsStore` directly. Split into types (F8.3.1), store (F8.3.2), and the mutation hook (F8.3.3).

**Acceptance (whole task):**
- After login, the settings store is populated from `/me`.
- Calling the update hook writes to the server and `authStore.currentUser` + the settings store reflect the change without a manual refetch.
- `theme`/`uiLocale` survive a page reload (read from the localStorage mirror) even before `/me` resolves.

#### F8.3.1 — Profile/settings DTO + `Theme`/`UiLocale` types

**Goal:** Typed contract shared by the store and the api hook (type-check is the verification — no runtime test).

**Scope (in):**
- `apps/web/src/types/auth.ts` — extend `UserMeResponse` with `displayName: string | null`, `avatarAccentColor: string | null`, `uiLocale: UiLocale`, `theme: Theme`; add `export type UiLocale = 'en' | 'es'`, `export type Theme = 'light' | 'dark' | 'system'`, and `export interface UpdateProfilePayload { displayName?: string | null; avatarAccentColor?: string | null; uiLocale?: UiLocale; theme?: Theme }`

**Scope (out):** Store (F8.3.2); api mutation (F8.3.3).

**Skills:** `frontend-api-resource`  **Decisions:** D-101, D-076  **Dependencies:** F8.2.2 *(contract source)*

#### F8.3.2 — `useSettingsStore` (Zustand persist) + login hydration

**Goal:** Client mirror of `theme` + `uiLocale` for no-flash first paint, hydrated from `/me` on login.

**Scope (in):**
- `apps/web/src/store/settingsStore.ts` — Zustand `persist` (key `blue-steel-settings`, following the `uiStore` pattern) with `theme` + `uiLocale` and actions `setTheme`, `setUiLocale`, `hydrateFromUser(me: UserMeResponse)`
- wire `hydrateFromUser` into the existing `useLogin` success path in `apps/web/src/api/auth.ts` (right after `setCurrentUser`)
- (+ store test: `hydrateFromUser` populates `theme`/`uiLocale`; values survive a reload via the localStorage mirror)

**Scope (out):** The mutation hook (F8.3.3); visible UI (F8.4/F8.5).

**Skills:** `frontend-api-resource`, `frontend-testing`, `auth`  **Decisions:** D-101  **Dependencies:** F8.3.1

#### F8.3.3 — `updateProfile()` + `useUpdateProfile()`

**Goal:** PATCH the profile and propagate the new server truth (no `/me` query exists → refetch + restore).

**Scope (in):**
- `apps/web/src/api/users.ts` — `updateProfile(payload: UpdateProfilePayload): Promise<void>` via `apiClient.patch('/api/v1/users/me', payload)`; `useUpdateProfile()` `useMutation` whose `onSuccess` calls `getCurrentUser()` → `useAuthStore.getState().setCurrentUser(me)` + `useSettingsStore.getState().hydrateFromUser(me)`
- (+ test: PATCH body asserted; on success `authStore.currentUser` + `settingsStore` reflect the refetched `/me`)

**Scope (out):** Visible UI (F8.4/F8.5).

**Skills:** `frontend-api-resource`, `frontend-testing`  **Decisions:** D-101, D-076  **Dependencies:** F8.3.2

#### F8.4 — Frontend: top-right account menu (header redesign)

> **Umbrella task — run F8.4-SETUP then the F8.4.N sub-tasks below, not this.**

Replace the raw-email header element and retire the sidebar Settings stub with a standard top-right
account menu. Split into the avatar primitive (F8.4.1), the menu (F8.4.2), and the AppBar/Sidebar
wiring (F8.4.3).

**Acceptance (whole task):**
- The top-right header shows an avatar rendered from initials (display name, or email fallback); the campaign sidebar no longer shows a Settings item.
- Opening the menu reveals name + email, a Settings link, theme and EN/ES toggles, and Log out; logging out from it signs the user out from any page.
- The menu is keyboard-operable and passes an axe check.

##### F8.4-SETUP 👤 (human, run before the sub-tasks)

```
cd apps/web
npx shadcn@latest add dropdown-menu     # generates src/components/ui/dropdown-menu.tsx
```

No new npm packages (`radix-ui` is already a dependency). The avatar is custom (initials + accent), so
no shadcn `avatar` component is added.

#### F8.4.1 — `InitialsAvatar` (initials + accent)

**Goal:** Reusable initials+accent avatar (also used by the F8.5 settings live preview).

**Scope (in):**
- `apps/web/src/components/domain/InitialsAvatar.tsx` — props `{ displayName?: string | null; email: string; accentColor?: string | null; size? }`; deterministic initials (display name → email fallback); accent background with a readable foreground; `aria-hidden` glyph + an accessible label
- (+ `InitialsAvatar.test.tsx` incl. axe)

**Scope (out):** The menu (F8.4.2); the settings page (F8.5).

**Skills:** `frontend-testing`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-100, D-087  **Dependencies:** —

#### F8.4.2 — `UserMenu` dropdown

**Goal:** The top-right account menu itself.

**Scope (in):**
- `apps/web/src/components/domain/UserMenu.tsx` — `InitialsAvatar` trigger opening `@/components/ui/dropdown-menu`; shows display name + email; a Settings `NavLink` to `/settings`; an inline theme toggle and EN/ES switch (read/write `useSettingsStore`, persisted via `useUpdateProfile`); Log out (relocated logic: `authStore.logout()` + navigate `/login`)
- (+ `UserMenu.test.tsx` incl. axe + keyboard open/close)

**Scope (out):** AppBar/Sidebar wiring (F8.4.3). The toggles here only **persist** the preference; the
visible theme effect lands with F8.7 (no dependency on F8.7).

**Skills:** `ux-navigation-logic`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-102, D-087  **Dependencies:** F8.4-SETUP, F8.4.1, F8.3.3

#### F8.4.3 — Wire `UserMenu` into `AppBar`; retire Sidebar Settings stub

**Goal:** Replace the raw-email header chrome and remove the inert sidebar Settings item.

**Scope (in):**
- `apps/web/src/components/domain/AppBar.tsx` — replace the email span and the standalone Log out button with `<UserMenu/>`
- `apps/web/src/components/domain/Sidebar.tsx` — remove the `ComingSoonItem label="Settings"` entry and the now-unused `Settings` lucide import
- (+ update `AppBar.test.tsx` and `Sidebar.test.tsx`)

**Scope (out):** Menu internals (F8.4.2); the Settings page (F8.5).

**Skills:** `ux-navigation-logic`, `frontend-testing`  **Decisions:** D-102, D-087  **Dependencies:** F8.4.2

#### F8.5 — Frontend: User Settings page (`/settings`)

> **Umbrella task — run the F8.5.N sub-tasks below, not this.** No SETUP (`select`, `radio-group`,
> `input`, `label`, `form`, `button` already in `src/components/ui/`).

A full settings surface for profile + preferences, on a global (non-campaign) route. Split into the
accent picker (F8.5.1), the page (F8.5.2), and the route (F8.5.3).

**Acceptance (whole task):**
- `/settings` is reachable only while authenticated and works independently of any active campaign.
- Editing display name / accent color / locale / theme and saving shows inline success feedback (no toast) and the changes persist across reload.
- The initials preview updates live as the display name or accent color changes.

#### F8.5.1 — Accent palette + `AccentColorPicker`

**Goal:** A bounded accent-color choice (the palette is settled here per the Phase-8 gate).

**Scope (in):**
- `apps/web/src/features/settings/accentPalette.ts` — the named hex preset list
- `apps/web/src/features/settings/components/AccentColorPicker.tsx` — controlled `value`/`onChange` swatch group built on `@/components/ui/radio-group`, with accessible labels
- (+ `AccentColorPicker.test.tsx` incl. axe)

**Scope (out):** The page (F8.5.2).

**Skills:** `frontend-testing`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-100, D-087  **Dependencies:** —

#### F8.5.2 — `UserSettingsPage` (form + live preview + InlineBanner)

**Goal:** The full profile/preferences surface with inline feedback.

**Scope (in):**
- `apps/web/src/features/settings/UserSettingsPage.tsx` — react-hook-form (`@/components/ui/form`) with a display-name `input`, `AccentColorPicker`, locale `@/components/ui/select`, and a theme control; a live `InitialsAvatar` preview bound to the form values; submit via `useUpdateProfile`; `InlineBanner` success/error (no toast, D-083; no modal, D-082); low-density layout (`p-8`) per UX
- (+ `UserSettingsPage.test.tsx` incl. axe: edit → save shows the success banner; the preview updates live)

**Scope (out):** Account-menu chrome (F8.4); the route (F8.5.3).

**Skills:** `frontend-api-resource`, `react-hook-form`, `ux-inline-feedback`, `frontend-testing`  **Decisions:** D-100, D-102, D-082, D-083, D-087  **Dependencies:** F8.5.1, F8.4.1, F8.3.3

#### F8.5.3 — Global `/settings` route wiring

**Goal:** Reach the page on a global (non-campaign) authenticated route.

**Scope (in):**
- `apps/web/src/main.tsx` — add `<Route path="/settings" element={<UserSettingsPage/>} />` as a **sibling of `/`** inside the `RequireAuth`/`AuthenticatedLayout` group (**not** under `/campaigns/:campaignId`)
- (+ routing test: `/settings` renders only while authenticated and independently of any active campaign)

**Scope (out):** Page body (F8.5.2).

**Skills:** `ux-navigation-logic`, `frontend-testing`  **Decisions:** D-102  **Dependencies:** F8.5.2

#### F8.6 — Frontend: i18n infrastructure + EN/ES catalogs

> **Umbrella task — run F8.6-SETUP then the F8.6.N sub-tasks below, not this.** Scope this phase =
> **mechanism + nav chrome only**; per-page string extraction (Query / Sessions / Settings / older
> pages) is deferred to incremental follow-on tasks.

Internationalize the UI with English and Spanish catalogs, driven by the user's UI locale. Split into
the i18n runtime (F8.6.1) and the catalogs + nav-chrome extraction (F8.6.2).

*Note:* there is still no i18n skill in `skills/SKILLS_INDEX.md` — `frontend-testing` +
`docs/UX_CONSTITUTION.md` are the authority here; optionally add a `frontend-i18n` skill later.

**Acceptance (this phase's scope):**
- Switching the UI locale (menu or settings) re-renders the nav-chrome strings in the chosen language without a full page reload.
- A string with no translation falls back to English rather than displaying a raw key.
- The chosen locale persists across reloads.

**Scope (out):** Campaign content language (Phase 9); per-page extraction beyond nav chrome (follow-on).

##### F8.6-SETUP 👤 (human, run before the sub-tasks)

```
cd apps/web
npm install i18next react-i18next
```

#### F8.6.1 — i18next init + provider driven by `useSettingsStore.uiLocale`

**Goal:** An i18n runtime driven by the user's UI locale, with an English fallback.

**Scope (in):**
- `apps/web/src/i18n/index.ts` — configure `i18next` + `react-i18next` (`fallbackLng: 'en'`, `resources` from the en/es catalogs, initial `lng` from `useSettingsStore.getState().uiLocale`)
- import-init in `apps/web/src/main.tsx`; subscribe `i18n.changeLanguage` to `useSettingsStore` `uiLocale` changes
- (+ test: changing `uiLocale` switches language without a reload; a missing key falls back to EN, not a raw key)

**Scope (out):** String extraction (F8.6.2).

**Skills:** `frontend-testing`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-099, D-101, D-087  **Dependencies:** F8.6-SETUP, F8.3.2 *(locale source)*

#### F8.6.2 — EN/ES catalogs + extract Sidebar/AppBar/UserMenu strings

**Goal:** Prove end-to-end translation on the always-visible nav chrome.

**Scope (in):**
- `apps/web/src/i18n/locales/en.json` + `apps/web/src/i18n/locales/es.json` — keys for `Sidebar`, `AppBar`, `UserMenu`
- replace the hardcoded strings in `components/domain/Sidebar.tsx`, `AppBar.tsx`, and `UserMenu.tsx` with `t('…')`
- (+ update those component tests to assert the translated text per locale)

**Scope (out):** Per-page extraction (Query / Sessions / Settings / etc.) — deferred follow-on; add sibling `F8.6.N` extraction tasks later if wanted.

**Skills:** `frontend-testing`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-099, D-101, D-087  **Dependencies:** F8.6.1, F8.4.2

#### F8.7 — Frontend: dark-mode theme system

> **Umbrella task — run the F8.7.N sub-tasks below, not this.** No SETUP (Tailwind v4 + shadcn
> `cssVariables: true` already configured).

Light/dark/system theming wired to the user's theme preference, with no flash on load. Split into the
dark palette (F8.7.1), the reactive theme-apply hook (F8.7.2), and the first-paint guard (F8.7.3).

*Note:* there is still no dark-mode skill in `skills/SKILLS_INDEX.md` — `docs/UX_CONSTITUTION.md` is the
authority here.

**Acceptance (whole task):**
- Selecting Dark applies the dark palette to semantic-token surfaces (shadcn components and any component using the design tokens); Light restores it; System follows the OS preference and reacts to OS changes live.
- On reload the correct theme is applied on first paint — no flash of the wrong theme.
- shadcn components render in the active theme.

> *Coverage note (post-implementation):* the `.dark` overrides only recolour components that use the
> semantic design tokens (`bg-background`, `bg-card`, `text-foreground`, `border-border`, …). Most of
> the app and the always-visible chrome (`AuthenticatedLayout`, `AppBar`, `Sidebar`) still hardcode
> raw `slate-*` utilities and therefore stay light in Dark mode. Full app-wide coverage is the
> non-blocking follow-on **F8.8** below (it does not reopen the Phase 8 milestone).

#### F8.7.1 — `.dark` CSS-variable overrides in `index.css`

**Goal:** A dark palette for the existing color tokens.

**Scope (in):**
- `apps/web/src/index.css` — add a `.dark { … }` block overriding the `@theme` color tokens (surface / background / border / foreground / card / popover / primary / secondary / muted / destructive / input / ring) with dark values
- (verification is manual/visual — CSS has no unit test; shadcn `cssVariables: true` is already wired)

**Scope (out):** The toggle logic (F8.7.2); per-component restyle beyond variable wiring.

**Skills:** design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-101, D-087  **Dependencies:** —

#### F8.7.2 — Theme-apply hook

**Goal:** Apply the user's theme to `<html>`, with live `system` following.

**Scope (in):**
- `apps/web/src/hooks/useApplyTheme.ts` — toggle `document.documentElement.classList` `dark` from `useSettingsStore.theme`; resolve `system` via `matchMedia('(prefers-color-scheme: dark)')` with a change listener; mount it in `main.tsx` / `AuthenticatedLayout`
- (+ test: `theme='dark'` adds `.dark`; `'light'` removes it; `'system'` follows and reacts to a mocked `matchMedia` change)

**Scope (out):** First-paint flash (F8.7.3).

**Skills:** `frontend-testing`  **Decisions:** D-101, D-087  **Dependencies:** F8.3.2

#### F8.7.3 — No-flash pre-paint script

**Goal:** Apply the correct theme on first paint, before React mounts.

**Scope (in):**
- `apps/web/index.html` — a small inline `<script>` reading `localStorage['blue-steel-settings']` (the Zustand persist mirror), resolving `system` via `matchMedia`, and setting `<html class="dark">` synchronously before the module bundle loads

**Scope (out):** Reactive toggling (F8.7.2).

**Skills:** design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-101, D-087  **Dependencies:** F8.7.2

#### F8.8 — Frontend: migrate raw color utilities → semantic tokens for full dark-mode coverage

> **Non-blocking follow-on.** Discovered while shipping F8.7: the `.dark` overrides only recolour the
> semantic design tokens, so the dark palette currently applies to the shadcn `components/ui/`
> primitives but **not** to the ~155 files that hardcode raw `slate-*`/`white`/`blue-*` utilities
> (~489 occurrences vs ~15 semantic-token usages), including the always-visible chrome. This task does
> **not** reopen the Phase 8 milestone (same status as F8.6's deferred per-page i18n extraction);
> tackle it incrementally.

**Goal:** Dark mode visibly applies across the whole UI, not just shadcn primitives.

**Scope (in):**
- Replace raw color utilities with the existing semantic tokens defined in `apps/web/src/index.css`
  (`bg-white`/`bg-slate-50` → `bg-surface`/`bg-background`/`bg-card`; `text-slate-900`/`text-slate-500`
  → `text-foreground`/`text-muted-foreground`; `border-slate-200` → `border-border`;
  `hover:bg-slate-100` → `hover:bg-muted`; etc.), preserving the light-mode appearance.
- Highest-value first slice — the chrome: `apps/web/src/components/domain/AuthenticatedLayout.tsx`,
  `AppBar.tsx`, `Sidebar.tsx` (then feature pages under `apps/web/src/features/`).
- Keep accent semantics intact (`blue-*` active/primary states map to `--color-primary`/`--color-accent*`).

**Scope (out):** New tokens or palette changes (the dark palette already exists); per-feature redesign.

**Skills:** design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-101, D-087  **Dependencies:** F8.7

#### F8.9 — Frontend: i18n per-page string extraction (remaining surfaces)

> **Non-blocking follow-on, surfaced by the F8 coherence review (2026-06-21).** F8.6 shipped the i18n
> mechanism + nav chrome; a later pass extracted the all-member-facing **Exploration** read surfaces
> (Entities/Spaces lists, entity/space profiles, relation detail) under the `exploration` namespace.
> The remaining feature strings are still hardcoded English. Do this in a focused future session.

**Goal:** All user-facing strings switch with the UI locale, with English fallback for missing keys.

**Scope (in):**
- Add namespaces to `apps/web/src/i18n/locales/en.json` + `es.json` (e.g. `input`, `campaigns`,
  `proposals`, `auth`, plus a shared `common`) and replace hardcoded strings with `t(...)` across the
  audit-flagged files: `features/input/SubmitSessionPage.tsx`, `components/ConflictWarningCard.tsx`,
  `components/AddEntityForm.tsx`, `DiffReviewPage.tsx` ("Added"), `components/DiscardConfirmOverlay.tsx`;
  `features/campaigns/components/DeleteCampaignConfirmOverlay.tsx`, `RemoveMemberConfirmOverlay.tsx`;
  `features/proposals/ProposalReviewCard.tsx` (approve/veto aria-labels); plus the auth/admin pages.
- Overlay/notice bodies that embed inline markup (bolded `{campaignName}`/`{memberEmail}` mid-sentence)
  need i18next `<Trans>` rather than a flat `t(...)` string.
- Keep English catalog values identical to the prior hardcoded text so existing English-text test
  assertions stay green; add ES-locale assertions where useful (pattern: `EntitiesPage.test.tsx`).

**Scope (out):** New i18n infrastructure (exists, F8.6.1); per-campaign content language (Phase 9, D-099).

**Skills:** `frontend-testing`; design authority `docs/UX_CONSTITUTION.md`  **Decisions:** D-099, D-101, D-087  **Dependencies:** F8.6

#### F8.10 — Repo: decide + enforce repo-wide Prettier formatting

> **Process follow-on, surfaced by the F8 coherence review (2026-06-21).** Running `prettier --write
> src/` reformats ~190 files in `apps/web`, i.e. the committed tree is **not** Prettier-clean under the
> current config (quote/whitespace/line-ending drift), because formatting is "keep clean locally, not
> in CI" (`apps/web/CLAUDE.md`). Until resolved, contributors must avoid `prettier --write src/`
> (format only changed files) to prevent mass churn burying real diffs in review.

**Goal:** Either the repo is Prettier-clean and kept so by CI, or the drift is an accepted, documented decision.

**Scope (in):**
- Decide direction. If enforcing: one isolated repo-wide format commit (`chore(web): prettier --write`),
  **its own PR** with no feature changes, then wire `npx prettier --check src/` into
  `.github/workflows/frontend.yml` (before/with lint). If accepting drift: document it explicitly in
  `apps/web/CLAUDE.md` and drop the "keep code clean locally" expectation.
- Confirm `endOfLine` config vs the repo's line-ending normalization so the format commit doesn't
  introduce CRLF/LF churn.

**Scope (out):** Backend formatting (already enforced via Spotless in CI).

**Skills:** `ci-cd`  **Decisions:** —  **Dependencies:** —

> ⚠️ **On Phase 8 completion (after the last F8.x.N is ✅):**
> 1. **Update `docs/app_feature_inventory/` if needed** — the new user profile/settings, account menu,
>    i18n, and theming surfaces touch `user_management.md`, `authentication.md`, `system_platform.md`,
>    and `deferred_and_planned.md` (move the now-shipped items out of deferred). Reconcile the inventory
>    with what actually shipped.
> 2. **Repo-wide version bump (D-090)** — completing the Phase 8 milestone triggers a SemVer bump:
>    raise `apps/web/package.json` and `apps/api/pom.xml` `<version>` **together** (they must stay
>    equal) in one `chore:` commit on a branch, then an annotated `vMAJOR.MINOR.PATCH` tag on `main`
>    **after merge** (never tag a branch; don't push/tag unless asked).

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
| F9.1 | Backend: campaign `content_language` schema + domain + create API | ✅ |
| F9.2 | Backend: thread language into extraction + conflict-detection prompts | ✅ |
| F9.3 | Backend: language in query-answering prompt | ✅ |
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
