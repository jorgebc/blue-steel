# ROADMAP V3 — Blue Steel

> **Status: initial improvement scoped; implementation not started.** This draft starts with the
> first post-v2 improvement below. Phases 5–9 are already assigned to V2, so V3 continues at
> Phase 10 and uses globally unique feature IDs.
>
> `ROADMAP_V2.md` remains the active roadmap until a separate roadmap rollover updates the index
> and the `.ai/` pipeline references. This document does not activate V3.

---

## Phases

### Phase 10 — Session Failure Recovery & Cleanup

**Purpose:** Let campaign GMs safely remove failed ingestion attempts or retry their processing,
without deleting provenance used elsewhere, corrupting world state, or allowing stale workers to
overwrite current state.

**Phase 10 Gate:** Recommendations and the outstanding owner choices are captured in
[`docs/plans/f10-1-session-failure-recovery/00-overview.md`](../plans/f10-1-session-failure-recovery/00-overview.md).
The plan is decision-complete for engineering; resolve or explicitly accept the recommended
defaults for owner decisions before shipping the affected role/execution behavior. Other phases are
not blocked.

#### Summary

| # | Feature | Status |
|---|---|---|
| F10.1 | Safely delete or retry a failed session | 🔲 |
| F10.1.1 | Correct pipeline failure accounting | 🔲 |
| F10.1.2 | Add attempt fencing and recovery persistence | 🔲 |
| F10.1.3 | Add bounded manual retry | 🔲 |
| F10.1.4 | Add failed-session soft hide | 🔲 |
| F10.1.5 | Add frontend recovery API types and mutations | 🔲 |
| F10.1.6 | Add recovery UI, polling, and localization | 🔲 |
| F10.1.7 | Harden recovery contract and regression coverage | 🔲 |

#### F10.1 — Safely delete or retry a failed session

**Goal:** Add failed-session actions that are enforced and race-safe in the backend, then expose
them through the existing session UI with clear confirmation, progress, and safe error feedback.

**Scope (in):** Campaign-scoped manual retry from retained narrative input; reversible-in-database
soft hiding of failed sessions; bounded attempts; server-enforced roles and status; attempt fencing;
safe failure stage/code; frontend cache, polling, error, and accessibility behavior.

**Scope (out):** Hard deletion; deletion or retry of `DRAFT`/`COMMITTED` sessions; resume from an
intermediate stage; automatic retries; durable queue/outbox; changes to extraction/commit business
rules beyond failure accounting and write fencing; audio/file ingestion.

##### 1. Verified technical report

**Verdicts:** Delete is **NOT SAFE as a hard delete**; use a nullable `deleted_at` soft hide and
preserve the row and provenance. Retry is **NOT SAFE as implemented; SAFE WITH CHANGES**; use a
manual restart from the beginning with a capped attempt count and fenced writes.

**Lifecycle and pipeline.** Current persisted states are `PENDING`, `PROCESSING`, `DRAFT`,
`COMMITTED`, `FAILED`, and `DISCARDED`. Aggregate methods guard the normal transitions, but the
recovery adapter directly updates `PROCESSING` rows to `FAILED` with SQL. Entity-resolution and
conflict-detection exceptions are logged by the listener without marking the session failed;
extraction and diff generation do mark it failed. Recovery is not cancellation. The pipeline
does not write world state before explicit review and transactional commit. See the corrected
claims and evidence in the verification log and overview. (`apps/api/src/main/java/com/bluesteel/domain/session/SessionStatus.java:3-11`;
`apps/api/src/main/java/com/bluesteel/domain/session/Session.java:80-128`;
`apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapter.java:27-54`;
`apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java:58-110`;
`apps/api/src/main/java/com/bluesteel/application/service/session/EntityResolutionService.java:65-121`;
`apps/api/src/main/java/com/bluesteel/application/service/session/ConflictDetectionService.java:62-95`;
`apps/api/src/main/java/com/bluesteel/application/service/session/CommitService.java:70-153`)

**Persistence and references.** Narrative input cascades from a deleted session; migration 0022
also cascades session links to world-state heads/versions and entity embeddings, and 0025 adds
cascading event/actor joins. `proposals.session_id` has no `ON DELETE` action. Query citations are
JSONB, not session foreign keys. These mixed semantics make hard deletion unsafe even though normal
pre-commit failures should have no world-state writes. Soft hiding preserves proposal provenance,
narrative input, and query history. (`apps/api/src/main/resources/db/changelog/0021_narrative_blocks_cascade_delete.xml:13-21`;
`apps/api/src/main/resources/db/changelog/0022_world_state_cascade_delete.xml:27-180`;
`apps/api/src/main/resources/db/changelog/0025_event_involved_actors_cascade_delete.xml:7-35`;
`apps/api/src/main/resources/db/changelog/0026_add_proposal_session_and_result.xml:9-24`;
`apps/api/src/main/resources/db/changelog/0028_create_query_log.xml:15-46`;
`apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalCreationService.java:93-120`)

**Execution and retry.** The configured ingestion executor is in-memory, not a durable job queue,
and has no cancellation handle. A status-only timeout can therefore race a still-running worker.
The partial unique index allows one `processing|draft` session per campaign, so claiming retry while
another session is active/draft must return existing `409 ACTIVE_SESSION_EXISTS`, not leak a database
constraint error. Use an attempt UUID as a fencing token, conditional writes by status and attempt,
and a bounded manual retry. A durable queue/outbox is not required for this bounded retry contract;
dispatch loss is recovered as a failed attempt and retried manually. (`apps/api/src/main/java/com/bluesteel/config/ApplicationConfig.java:26-38`;
`apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java:58-68`;
`apps/api/src/main/resources/db/changelog/0006_create_sessions.xml:56-65`;
`apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapter.java:27-54`)

**Frontend corrections.** Status polling currently stops unless the latest status is `PROCESSING`,
although submit returns the initially persisted `PENDING` status. Poll both states. Detail and status
queries have distinct keys; mutations must invalidate detail, status, and list. The frontend permits
Editor draft discard although the backend requires GM. `sequenceNumber` is nullable in Java but
typed/rendered as a number in TypeScript. `FocusedOverlay` is a centered fixed overlay, not the
anchored pattern described by the UX skill; implementation must follow `docs/UX_CONSTITUTION.md`
and verify all existing overlay consumers if the shared component changes.
(`apps/api/src/main/java/com/bluesteel/application/service/session/SubmitSessionService.java:103`;
`apps/web/src/api/sessions.ts:7-18,95-114,129-147`;
`apps/web/src/features/input/ProcessingStatusView.tsx:38-67`;
`apps/web/src/features/input/SessionsListPage.tsx:69-103,125-140,210-215`;
`apps/web/src/components/domain/FocusedOverlay.tsx:1-42`;
`apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionSummaryResponse.java:8-12`;
`apps/web/src/types/session.ts:132-136`;
`apps/web/src/features/input/SessionsListPage.tsx:78-83`)

Other consumers that must remain coherent include campaign exports, proposal provenance and session
selection, query citations, entity/session appearance reads, and campaign session list/count. No
standalone OpenAPI contract file was found; `docs/ARCHITECTURE.md` is the API catalogue. The
feature inventory has outdated claims about 422 input-size errors, polling backoff, and all pipeline
stages marking failures. The `.ai/` roadmap/pipeline references are V2-oriented and are intentionally
not changed while V3 remains inactive. (`apps/api/src/main/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapter.java:191-207`;
`apps/api/src/main/java/com/bluesteel/adapters/out/persistence/worldstate/EntityLinksReadAdapter.java:217-244`;
`apps/api/src/main/java/com/bluesteel/adapters/out/persistence/query/QueryLogJpaEntity.java:33-34`;
`apps/api/src/main/java/com/bluesteel/application/service/session/ListSessionsService.java:44-55`;
`apps/web/src/components/domain/ProposalSubmitForm.tsx:57,121-136`;
`docs/ARCHITECTURE.md:712-731`;
`docs/app_feature_inventory/session_ingestion.md:15,22-23,33,40`;
`.ai/pipeline/orchestrator.py:365-366`;
`.ai/pipeline/agents/planning/planning_crew.py:62-63`)

##### 2. Phase Gate decisions

| Decision | Recommendation | Consequence |
|---|---|---|
| Delete semantics | Soft hide with `sessions.deleted_at`; do not reuse `DISCARDED`, which means a draft was discarded. | Add a nullable timestamp and visibility filters. Keep narrative, proposals, query citations, and world-state provenance. Return 404 for hidden rows. |
| Roles | GM only for retry and hide, matching current draft discard authorization. | Enforce in both use cases; UI hides actions for non-GMs. **NEEDS OWNER DECISION:** confirm whether Editors should manage failed ingestion. Default remains GM-only; if unresolved, role policy is blocked but other work is not. |
| Retry policy | Manual restart from original input; at most 3 total attempts, including the initial attempt; no automatic retry. | Persist `attempt_count`; reject exhausted attempts with 422 `SESSION_RETRY_LIMIT_REACHED`; retain input and clear attempt-scoped output. |
| Worker fencing | Attempt UUID plus conditional status/attempt/deleted checks on every pipeline write and recovery update. Do not add a queue/outbox. | Carry the attempt ID in the ingestion event and stage writes; old attempts cannot change a newer attempt or hidden row. A lost in-memory dispatch becomes a failed attempt, recoverable by a later manual retry. This does not cancel an in-flight provider call. |
| Physical overlap after timeout | **NEEDS OWNER DECISION.** Default: tolerate bounded provider-call overlap after a timed-out attempt while stale writes are fenced and retry count is capped. | No code change for the default. If zero overlapping provider calls are required, retry remains blocked until a separately designed cancellable-runner/provider-timeout contract exists. |
| Retryability | Only explicit permanent input/configuration failures are non-retryable; infrastructure/model/timeout and unknown failures remain manually retryable within the cap. | Store stable error code and stage; expose `retryable`; do not classify by raw exception text. |
| Proposal references | Preserve them; do not null/delete proposal provenance. | Soft hide has no FK change. Proposal creation must reject new use of hidden sessions; existing proposal responses retain `sessionId` provenance and currently do not join session content. |
| Failure detail | Show localized safe failure code/stage, never exception text, stack trace, prompt, or narrative. | Add `failure_stage` and safe-code mapping; retain technical cause only in logs with identifiers and without raw input. |

##### 3. Target contract

The final state machine, schema/migration, API/error envelope, concurrency rules, safe failure
mapping, frontend query matrix, role/polling behavior, i18n, and accessibility requirements are
specified in `00-overview.md`. No hard delete is introduced. Existing `DISCARDED` remains draft-only.
New `FAILED → PROCESSING` is a conditional retry claim, not an unguarded aggregate mutation;
`FAILED → FAILED` with `deleted_at` is the soft-hide operation. All pipeline output writes require
the current attempt UUID and `deleted_at IS NULL`.

##### 4. Implementation phases

| ID | Goal | Estimate | Depends on |
|---|---|---:|---|
| F10.1.1 | Mark all pipeline-stage failures consistently and safely | M | — |
| F10.1.2 | Add attempt metadata, conditional claims/writes, and fenced recovery | L | F10.1.1 |
| F10.1.3 | Add capped manual retry endpoint and response contract | M | F10.1.2 |
| F10.1.4 | Add failed-session soft hide and provenance-safe filters | M | F10.1.2 |
| F10.1.5 | Add frontend recovery API types and mutations | S | F10.1.3, F10.1.4 |
| F10.1.6 | Add recovery UI, polling, and localization | M | F10.1.5 |
| F10.1.7 | Verify cross-consumer, concurrency, and regression behavior | M | F10.1.3, F10.1.4, F10.1.6 |

Every phase is specified with exact paths, tasks, dependencies, tests, acceptance criteria,
rollback, skills, and a copy-ready kickoff prompt in
[`docs/plans/f10-1-session-failure-recovery/`](../plans/f10-1-session-failure-recovery/).
Phase order and required integration checks are defined there. Effort estimates are relative
engineering sizes, not calendar estimates.

##### 5. Verified related findings and caveats

- `SessionRecoveryAdapter` has a startup transition and a timeout transition; both are direct SQL
  and neither fences a worker. Startup recovery may affect a still-running worker in another app
  instance.
- Retry must account for the per-campaign partial unique index. A violated active/draft constraint
  is translated to existing `409 ACTIVE_SESSION_EXISTS`.
- `sequenceNumber` is nullable for uncommitted sessions. Display a neutral fallback, never `#null`.
- The existing UI polls every two seconds without backoff. Preserve that interval for this scope;
  poll in `PENDING` and `PROCESSING` only, and stop on terminal states.
- Campaign export and proposal/session reads must exclude hidden sessions from ordinary browsing,
  while preserving historical references. Hiding must not delete exported/provenance data.
- Skills cited by the original draft exist under `skills/`, not `.ai/skills/`. Their conventions
  and stale examples are recorded in the overview. No `.ai/` files are changed.

##### 6. Source-of-truth documents

- [Overview and final contract](../plans/f10-1-session-failure-recovery/00-overview.md)
- [Phase plans and kickoff prompts](../plans/f10-1-session-failure-recovery/)
- [Claim-by-claim verification log](../plans/f10-1-session-failure-recovery/verification-log.md)

**Skills:** `session-ingestion-pipeline`, `backend-endpoint`, `backend-testing`,
`database-migration`, `frontend-api-resource`, `frontend-testing`, `ux-focused-overlay`,
`ux-inline-feedback`, and `ux-skeleton-crafting`.
