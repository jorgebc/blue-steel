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
- [ ] **Proposal↔session linkage (provenance) + version stamp** — the author selects a campaign `session_id` at creation as **provenance/context only** (`proposals.session_id`, FK `sessions`). On approval the resulting entity version is written through the existing `WorldStatePort.writeEntity` path but stamped with the campaign's **latest committed session id**, keeping `version_number`↔`sessions.sequence_number` co-monotonic (current state stays `MAX(version_number)`; no as-of-session reconstruction anomaly). Record as a new D-number. Shapes F5.1.1, F5.2.2, F5.4.2–F5.4.4.
- [ ] **v2 target scope = `actor` + `space` only** — defer event/relation (structured endpoints) and new-entity proposals; D-012's "relation" affordance is explicitly deferred. Creation validation rejects other target types (422). Record as a new D-number. Shapes F5.1.2, F5.2.2, F5.4.3, F5.7.1.
- [ ] **GM decision recorded as a vote** — approve/veto writes a `proposal_votes('approve'|'reject')` row (who/when audit) in addition to flipping `proposal.status`; co-sign writes a `cosign` row. Record as a new D-number. Shapes F5.3.2, F5.4.4.
- [ ] **GM edit-on-approve** — the GM decision accepts an optional GM-edited delta applied in place of the author's `proposed_delta` when approving; record as a new D-number. Shapes F5.4.1, F5.4.3, F5.9.2.
- [ ] **`proposed_delta` shape (resolve first)** — field-level JSONB contract that **mirrors entity-version `changed_fields`** (ARCHITECTURE §7.6) so the approve-time apply-mapper is trivial. Note: `target_entity_type`/`target_entity_id`/`proposed_delta` stay nullable in the DB but are enforced **non-null in the application** at creation.
- [ ] v2 build sequence (Phase 5 → 6 → 7 or otherwise) + post-1.0 version mapping (extends D-090)

#### Summary

| # | Feature | Status |
|---|---|---|
| F5.1 | Backend: proposal domain model + ports *(umbrella)* | 🔲 |
| F5.1.1 | Migration 0020 — add `session_id` (FK sessions) to `proposals` | 🔲 |
| F5.1.2 | Domain: `Proposal` aggregate + `ProposalStatus` + `ProposalTargetType` + exceptions | 🔲 |
| F5.1.3 | Domain: `ProposalVote` + `VoteKind` | 🔲 |
| F5.1.4 | Application: `ProposalRepository` driven port + `application/model/proposal` records | 🔲 |
| F5.1.5 | Persistence: `ProposalJpaEntity` + `ProposalVoteJpaEntity` + JPA repositories | 🔲 |
| F5.1.6 | Persistence: `ProposalPersistenceAdapter` (mapper, impl `ProposalRepository`) + IT | 🔲 |
| F5.2 | Backend: proposal submission + listing API *(umbrella)* | 🔲 |
| F5.2.1 | Driving ports `Create`/`ListProposalsUseCase` + command/result models | 🔲 |
| F5.2.2 | `ProposalCreationService` — validate target+session in campaign, set `expires_at` | 🔲 |
| F5.2.3 | `ListProposalsService` — filter by target/status, offset pagination | 🔲 |
| F5.2.4 | `ProposalController` POST+GET + request/response DTOs | 🔲 |
| F5.2.5 | Error mapping in `GlobalExceptionHandler` (404 target/session, 422 invalid delta) | 🔲 |
| F5.3 | Backend: co-sign flow *(umbrella)* | 🔲 |
| F5.3.1 | Driving port `CoSignProposalUseCase` + `CoSignProposalCommand` | 🔲 |
| F5.3.2 | `ProposalCoSignService` — author-cannot-cosign, duplicate→409, `open→cosigned` | 🔲 |
| F5.3.3 | Vote endpoint + `CastVoteRequest` DTO + error mapping (`@WebMvcTest`) | 🔲 |
| F5.4 | Backend: GM decision — approve applies delta, veto rejects *(umbrella)* | 🔲 |
| F5.4.1 | Driving port `DecideProposalUseCase` + command (approve/reject + optional edited delta) | 🔲 |
| F5.4.2 | `SessionRepository.findLatestCommittedSessionId` + adapter (IT) — supplies the version stamp | 🔲 |
| F5.4.3 | `ProposalDeltaMapper` (actor/space) — reads current snapshot, stamps latest committed session | 🔲 |
| F5.4.4 | `ProposalDecisionService` — GM-only; approve writes version + embeds + vote row, veto rejects | 🔲 |
| F5.4.5 | Decision endpoint + DTO + GM gating + error mapping (`@WebMvcTest`) | 🔲 |
| F5.5 | Backend: proposal TTL expiry scheduler *(umbrella)* | 🔲 |
| F5.5.1 | `ProposalExpiryPort` + bulk-expiry SQL adapter (IT) | 🔲 |
| F5.5.2 | `ProposalExpiryScheduler` (@Scheduled, env-overridable TTL/interval) | 🔲 |
| F5.6 | Backend: concurrent-proposal conflict rule *(umbrella)* | 🔲 |
| F5.6.1 | Repository `existsOpenProposalForTarget` query (IT) | 🔲 |
| F5.6.2 | Enforce gate rule in creation/decision + error code → handler | 🔲 |
| F5.7 | Frontend: activate "Propose a change" + submission overlay *(umbrella)* | 🔲 |
| F5.7-SETUP | Human: `shadcn add select` | 👤 |
| F5.7.1 | `types/proposal.ts` — DTO mirrors (Proposal incl `sessionId`, requests, statuses) | 🔲 |
| F5.7.2 | `api/proposals.ts` — keys + `getProposals`/`createProposal` + hooks | 🔲 |
| F5.7.3 | `ProposalSubmitForm.tsx` — RHF+zod, session `<Select>`, field-change capture, banner | 🔲 |
| F5.7.4 | Activate `ProposeChangeButton.tsx` → opens `FocusedOverlay` with the form | 🔲 |
| F5.8 | Frontend: proposal list/detail on profiles + co-sign *(umbrella)* | 🔲 |
| F5.8.1 | `ProposalStatusBadge.tsx` — five states → shadcn `Badge` | 🔲 |
| F5.8.2 | `api/proposals.ts` — add `useCoSignProposal` hook | 🔲 |
| F5.8.3 | `ProposalThreadSkeleton.tsx` — DTO-derived loading state | 🔲 |
| F5.8.4 | `ProposalThread.tsx` — per-entity list + co-sign (role/author gated) | 🔲 |
| F5.8.5 | Mount `ProposalThread` in `EntityProfileView.tsx` | 🔲 |
| F5.9 | Frontend: GM review queue (approve/veto) *(umbrella)* | 🔲 |
| F5.9.1 | `api/proposals.ts` — add `useDecideProposal` + cosigned-queue hook | 🔲 |
| F5.9.2 | `ProposalReviewCard.tsx` — approve (editable overlay) / veto + result link | 🔲 |
| F5.9.3 | `ProposalReviewQueuePage.tsx` — GM-gated queue + skeleton | 🔲 |
| F5.9.4 | Route + Sidebar nav entry (GM-gated) | 🔲 |

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
- `apps/api/src/main/resources/db/changelog/0020_add_proposal_session_and_result.xml` — nullable `session_id UUID` + `fk_proposals_session` FK → `sessions(id)` (provenance/context; non-null enforced in the domain at creation); nullable `resulting_entity_version_id UUID` (**no FK** — polymorphic across the four `*_versions` tables; set on approval, traceability). Register it in the master changelog include list.

**Scope (out):** Any domain/JPA code (F5.1.2+); any change to `*_versions` tables (the approved version is stamped with the **latest committed session id**, not a new version-table column).

**Skills:** `database-migration`  **Decisions:** D-016, D-021, *gate(session-linkage)*  **Dependencies:** Phase 5 Gate

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

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-016, D-019, D-043, *gate(session-linkage, target-scope, delta-shape, TTL)*  **Dependencies:** F5.2.1, F5.1.6

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
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalCoSignService.java` (+ unit test): writes a `proposal_votes('cosign')` row; allowed for **any non-author member** (gate rule); author cannot co-sign own proposal (422); duplicate vote → 409 (backed by `uidx_proposal_votes_proposal_voter`); first co-sign transitions `open → cosigned`

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

**Skills:** `backend-endpoint`  **Decisions:** D-018, *gate(gm-edit)*  **Dependencies:** F5.1.4

#### F5.4.2 — Latest-committed-session lookup

**Goal:** Supply the session the approved version is stamped with, preserving monotonic version↔session ordering.

**Scope (in):**
- extend `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRepository.java` with `findLatestCommittedSessionId(campaignId)` + its query in `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java` (max `sequence_number` where `status='committed'`) (+ Testcontainers IT)

**Scope (out):** Delta mapping (F5.4.3); decision orchestration (F5.4.4).

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-001, *gate(session-linkage)*  **Dependencies:** F5.1.4

#### F5.4.3 — `ProposalDeltaMapper`

**Goal:** Translate the effective delta into an `EntityWriteCommand` for the existing write path (actor/space only).

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalDeltaMapper.java` (+ unit test): read the target's current head (`ownerId`, `name`) + latest `full_snapshot` via `WorldStateReadPort`; merge the effective delta (GM-edited if present, else `proposed_delta`) → `EntityWriteCommand` with `changed_fields` mirroring the delta, merged `full_snapshot`, and **`sessionId =` the latest committed session id (F5.4.2)**

**Scope (out):** Event/relation targets (deferred); orchestration (F5.4.4); endpoint (F5.4.5).

**Skills:** `backend-domain-model`, `backend-testing`  **Decisions:** D-001, D-076, *gate(session-linkage, target-scope, gm-edit)*  **Dependencies:** F5.4.1, F5.4.2, F5.1.6

#### F5.4.4 — `ProposalDecisionService`

**Goal:** GM-only decision orchestration: approve writes a new (embedded) entity version and records the decision vote; veto rejects unilaterally.

**Scope (in):**
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalDecisionService.java` (+ unit test): GM-only via `CampaignMembershipPort`; `cosigned` precondition; `@Transactional`. **Approve** → `ProposalDeltaMapper` + `WorldStatePort.writeEntity` + **publish `SessionCommittedEvent(latestCommittedSessionId, campaignId, [version])`** (embeddings) + write `proposal_votes('approve')` + status `approved` + store `resulting_entity_version_id`. **Veto** → write `proposal_votes('reject')` + status `rejected`.

**Scope (out):** Endpoint/DTOs (F5.4.5); concurrent-proposal rule (F5.6).

**Skills:** `backend-endpoint`, `backend-domain-model`, `backend-testing`  **Decisions:** D-001, D-017, D-018, D-063, *gate(gm-decision-vote)*  **Dependencies:** F5.4.3, F5.3.2

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

**Skills:** `backend-endpoint`, `backend-testing`  **Decisions:** D-019, *gate(TTL)*  **Dependencies:** F5.5.1, Phase 5 Gate (TTL decision)

#### F5.6 — Backend: concurrent-proposal conflict rule

> **Umbrella task — run the F5.6.N sub-tasks below, not this.**

Enforce the gate-decided rule for multiple open proposals targeting the same entity (e.g., block at
submission, or flag at GM decision time) so approvals never silently clobber each other.

#### F5.6.1 — Open-proposal lookup query

**Goal:** Repository support to detect an existing open/cosigned proposal for a target entity.

**Scope (in):**
- `existsOpenProposalForTarget(campaignId, targetType, targetId)` on `ProposalJpaRepository` + adapter wiring (+ Testcontainers IT)

**Scope (out):** Enforcement / error code (F5.6.2).

**Skills:** `backend-testing`  **Decisions:** *gate(concurrent-rule)*  **Dependencies:** F5.1.5

#### F5.6.2 — Enforce the conflict rule

**Goal:** Apply the gate-decided rule and surface a dedicated error code.

**Scope (in):**
- enforce the decided rule (block-at-submission in `ProposalCreationService` and/or flag at `ProposalDecisionService`) + new conflict exception + `GlobalExceptionHandler` 409 mapping (+ unit + handler tests)

**Scope (out):** Merge/rebase tooling for deltas (out of v2 scope entirely).

**Skills:** `backend-endpoint`, `error-handling`, `backend-testing`  **Decisions:** *gate(concurrent-rule, new D-number)*  **Dependencies:** F5.6.1, F5.4.4

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
- `apps/web/src/components/domain/ProposalThread.tsx` (+ test): `useProposals` filtered by entity, `ProposalStatusBadge`, co-sign button gated to **any non-author member** (gate rule) via `useCoSignProposal`, `InlineBanner` feedback, `ProposalThreadSkeleton` while loading

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

**Skills:** `ux-focused-overlay`, `ux-inline-feedback`, `react-hook-form`, `frontend-testing`  **Decisions:** D-018, D-076, D-082, D-083, *gate(gm-edit)*  **Dependencies:** F5.9.1, F5.7.3

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
