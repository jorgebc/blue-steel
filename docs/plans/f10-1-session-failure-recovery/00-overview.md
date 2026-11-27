# F10.1 Session Failure Recovery — Overview

This is an implementation plan, not an implementation. The V3 roadmap remains inactive and no
application behavior is changed by this document. Current-state statements cite the verified code;
target behavior is explicitly marked as a recommendation.

## Decisions and Gate

| Topic | Recommendation and code-grounded rationale | Consequences |
|---|---|---|
| Delete semantics | **Soft hide** with `deleted_at`, not hard delete and not reuse of `DISCARDED`. `DISCARDED` is currently the terminal state for a reviewed draft; hard deletion can cascade through narrative/world-state/embedding data and is blocked by proposal references. | Add a nullable timestamp; retain row, raw input, proposal FK and query-citation JSON; hide from ordinary list/detail/status/diff and exports. Keep state `FAILED`. |
| Delete/retry roles | **GM only** for both, matching `DiscardSessionService`; enforce in backend and UI. Submission by Editors is not enough evidence to grant destructive/retry permissions. | No schema change. Frontend action visibility uses the current campaign role. **NEEDS OWNER DECISION:** confirm whether Editors should manage failed ingestion. Recommended default is GM-only; if unresolved, role policy is the only blocked product choice. |
| Retry mode | Manual retry from the beginning with original `narrative_blocks.raw_summary_text`; do not resume a stage and do not retry automatically. | Clear old attempt result metadata and diff, increment count once per successful claim. Maximum 3 total attempts, including the first; `422 SESSION_RETRY_LIMIT_REACHED` after exhaustion. |
| Worker safety | Use an attempt UUID fencing token and conditional updates. No durable queue/outbox. Existing executor is in-memory and has no cancellation handle, but a lost dispatch can be recorded/recovered as a failed attempt and retried manually. | Include `attemptId` in the domain event and every stage write. A timeout may leave an old worker consuming resources, but its writes are rejected once its attempt is no longer current. Do not claim physical cancellation. |
| Retryable failures | Permanent validation/configuration failures are not retryable; transient infrastructure/model/timeout and unknown failures are manually retryable within the cap. Classify by explicit stable codes, never by exception text. | Persist the safe code in the existing `failure_reason` column and add `failure_stage`; return `retryable`, and keep exception detail logs-only. Unknown codes default to retryable but remain bounded. |
| Proposal references | Preserve existing `sessionId` provenance and proposal records. Reject creation of new proposals using a hidden session as `404 SESSION_NOT_FOUND`. | No FK rewrite; soft hide avoids the no-action FK in migration 0026. Current proposal responses carry `sessionId` but do not join/render session content; do not add a new provenance UI. |
| User-visible failure | Localized safe code and stage only; no exception message, stack trace, prompt, or narrative in API/UI. | Keep current `message: null` behavior for status unless compatibility requires a generic localized key; expose existing `failureReason` as the safe code, plus `failureStage` and `retryable`. Technical cause remains structured logs with session/attempt IDs. |

Two product choices are **NEEDS OWNER DECISION**: whether Editors may manage failed sessions, and
whether a late in-flight external model call after timeout may overlap with a manually retried
attempt. Recommended defaults are GM-only and tolerate bounded overlapping provider work while
strictly fencing all writes. If the owner requires zero overlapping provider calls, the execution
design (not the rest of the contract) is blocked pending a cancellable-runner decision.

### Options and trade-offs for the original Gate questions

| Question | Options and code-grounded trade-offs | Chosen recommendation and consequence |
|---|---|---|
| Soft vs hard delete; `DISCARDED` vs new marker | Hard delete risks 0021/0022 cascades and is blocked by 0026 proposal FK; reusing `DISCARDED` conflates draft discard with failed-session hiding; a new status adds another terminal lifecycle state. | Soft hide via `deleted_at`, keep status `FAILED`. Add timestamp and read filters only; no cascade/FK cleanup or active-index change. |
| Roles | GM-only matches current draft discard; GM+Editor matches submission authorization but no current destructive-action precedent. | GM-only for retry/hide. **NEEDS OWNER DECISION** on Editors; server checks and UI gate default to GM. Only role gate/tests are blocked if not decided. |
| Retry start/resume, cap, manual/automatic | Resume requires checkpoints/artifacts that are not persisted; restart uses retained narrative and pre-commit stages have no world-state writes. Auto retry can repeat LLM charges for deterministic errors; unlimited retries are unsafe. | Manual from start, max 3 total attempts, no automatic retries. Add attempt count and reset only attempt result metadata; return 422 at cap. |
| Fencing/lease vs durable queue | Attempt UUID/CAS is a small DB-backed stale-writer fence; an outbox adds durable dispatch and operational lifecycle but not provider-call cancellation. Existing `TaskExecutor` has no durable job or cancel handle. | Use attempt UUID and conditional writes/recovery; no durable queue/outbox. Dispatch loss becomes failed and is manually retryable. Late provider work is fenced from writes but can overlap. **NEEDS OWNER DECISION** if zero physical overlap is required; that blocks retry until a cancellable-runner/provider-timeout design is approved. |
| Non-retryable classification | Retrying all errors risks permanent bad-input loops; parsing exception text is unstable and leaks implementation details; only a narrow permanent-code allowlist can safely prohibit retry. | Mark known permanent validation/configuration codes non-retryable; unknown/transient codes manual-retryable within cap. Persist safe code/stage and expose a boolean. |
| Existing proposals referencing the session | Hard delete can fail on the no-action FK; nulling/removing IDs loses provenance; retaining IDs with soft hide preserves audit. | Preserve proposal rows and `sessionId`; reject new proposal creation from hidden sessions as `404 SESSION_NOT_FOUND`. Current proposal response has provenance ID only and does not join source session content; no new provenance UI. |
| User-visible failure | Raw exception text is informative but can disclose internals/narrative; status-only is safe but not actionable; safe localized code/stage balances both. | Expose existing `failureReason` as safe code plus `failureStage` and `retryable`; keep exception details out of API/UI and logs free of narrative/secrets. |

## Verified current state

- Current lifecycle and SQL guard bypass:
  `apps/api/src/main/java/com/bluesteel/domain/session/Session.java:80-128`,
  `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapter.java:27-54`.
  See the row-by-row source audit in
  [`verification-log.md`](verification-log.md).
- Extraction and diff failures mark failed; resolution/conflict failures only reach the listener's
  logging catch:
  `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java:58-110`,
  `apps/api/src/main/java/com/bluesteel/application/service/session/ExtractionPipelineService.java:37-54`,
  `apps/api/src/main/java/com/bluesteel/application/service/session/DiffGenerationService.java:49-78`.
- Pre-commit output remains in memory/diff payload; world-state mutations occur in transactional
  `CommitService`:
  `apps/api/src/main/java/com/bluesteel/application/service/session/EntityResolutionService.java:65-121`,
  `apps/api/src/main/java/com/bluesteel/application/service/session/ConflictDetectionService.java:62-95`,
  `apps/api/src/main/java/com/bluesteel/application/service/session/CommitService.java:70-153`.
- Current recovery worker is an in-memory executor with no durable job/cancellation handle:
  `apps/api/src/main/java/com/bluesteel/config/ApplicationConfig.java:26-38`,
  `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java:58-68`.
- FK/cascade behavior is mixed. See the migration-specific evidence in the verification log:
  `apps/api/src/main/resources/db/changelog/0021_narrative_blocks_cascade_delete.xml:13-21`,
  `apps/api/src/main/resources/db/changelog/0022_world_state_cascade_delete.xml:27-180`,
  `apps/api/src/main/resources/db/changelog/0025_event_involved_actors_cascade_delete.xml:7-35`,
  `apps/api/src/main/resources/db/changelog/0026_add_proposal_session_and_result.xml:9-24`.
- The partial unique index on `processing|draft` is in
  `apps/api/src/main/resources/db/changelog/0006_create_sessions.xml:56-65`. A retry claim can
  violate it when another active/draft session exists; translate that conflict into the API
  contract below.
- Frontend status polling presently runs only for `PROCESSING`; initial submit returns `PENDING`.
  Separate detail/status query keys, frontend Editor discard gate, and nullable sequence mismatch
  are confirmed in `apps/api/src/main/java/com/bluesteel/application/service/session/SubmitSessionService.java:103`,
  `apps/web/src/api/sessions.ts:7-18,95-114,129-147`,
  `apps/web/src/features/input/SessionsListPage.tsx:69-103,125-140,210-215`,
  `apps/web/src/types/session.ts:132-136`.
- Additional consumers: campaign exports, proposal creation and provenance IDs, query citations,
  entity links/session appearances, list/count, and session selection. Proposal responses preserve
  `sessionId` but do not join/render session content. No standalone OpenAPI file was found;
  references are `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapter.java:191-207`,
  `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/worldstate/EntityLinksReadAdapter.java:217-244`,
  `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/query/QueryLogJpaEntity.java:33-34`,
  `apps/api/src/main/resources/db/changelog/0028_create_query_log.xml:15-46`,
  `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalCreationService.java:93-120`,
  `apps/api/src/main/java/com/bluesteel/adapters/in/web/proposal/ProposalResponse.java:14-38`,
  `apps/web/src/components/domain/ProposalSubmitForm.tsx:57,121-136`,
  `apps/api/src/main/java/com/bluesteel/application/service/session/ListSessionsService.java:44-55`.
  `docs/ARCHITECTURE.md:712-731` is the API catalogue.

## Target state machine

Current normal transitions are guarded by the domain aggregate; the recovery SQL currently
bypasses those guards. Target state transitions are:

| From | To | Initiator | Guard and persistence mechanism |
|---|---|---|---|
| `PENDING` | `PROCESSING` | Initial submission worker | Conditional claim `WHERE status=PENDING AND attempt_count=0 AND deleted_at IS NULL`; assign attempt UUID, set count 1. Duplicate event loses the claim and exits without writes. |
| `PROCESSING` | `DRAFT` | Diff stage | Domain builds draft; persistence update requires `status=PROCESSING`, matching `attempt_id`, and `deleted_at IS NULL`. |
| `PROCESSING` | `FAILED` | Any pipeline stage or recovery scheduler | Failure writer/scheduler conditionally updates only the current attempt and active row. Preserve safe failure code/stage. |
| `FAILED` | `PROCESSING` | Manual retry use case | Atomic conditional claim with `attempt_count < 3` and active row; assign new UUID, increment once, clear prior failure/diff. Unique index conflict maps to 409. |
| `FAILED` | `FAILED` + `deleted_at` | Soft-hide use case | Conditional update for `FAILED AND deleted_at IS NULL`; require GM and campaign match. |
| `DRAFT` | `COMMITTED` | Commit use case | Existing domain transition and transaction; additionally require not deleted and current attempt ID if attempt metadata is passed through. |
| `DRAFT` | `DISCARDED` | Existing discard use case | Existing domain transition; preserve semantics. |
| `COMMITTED`, `DISCARDED` | terminal | — | No new transitions. |

`attempt_id` is an opaque fencing UUID for the current/most recent attempt, not a cancellation
token. Every stage that can persist a failure or diff must supply it. Conditional updates that
affect zero rows mean stale/hidden execution and must stop; they are not success-shaped writes.
Stage code must avoid persisting output before verifying the attempt. Scheduler changes use the
same conditions. Existing aggregate methods remain the authority for ordinary in-process
transitions; SQL is the authority for atomic claim/fencing operations.
A recovery update whose predicate no longer matches is a no-op; a repeated hide returns 404.

## Schema and Liquibase plan

Add **`0031_session_failure_recovery.xml`** and append it to
`apps/api/src/main/resources/db/changelog/db.changelog-master.xml`. Do not modify historical
migrations. Confirm the next sequence at implementation time if another migration has landed.

| Column/index | Definition | Backfill |
|---|---|---|
| `attempt_count` | `INTEGER NOT NULL DEFAULT 0`, check `>= 0` | Existing `PENDING` rows remain `0`; all rows whose pipeline has started (`PROCESSING`, `DRAFT`, `COMMITTED`, `FAILED`, `DISCARDED`) become `1`. |
| `attempt_id` | Nullable UUID; set on every initial/retry claim; retain after terminal status for audit. This is the fencing token, not a time-expiring lease. | Assign UUID to existing started rows. |
| `failure_stage` | Nullable bounded varchar, stable enum-like value | Null for existing rows; do not infer stage from exception text. Existing safe `failure_reason` remains the code source where recognized. |
| `deleted_at` | Nullable `TIMESTAMPTZ` | All existing rows remain visible; no automatic deletion/hide backfill. |

Leave the existing partial unique index unchanged: hide is restricted to `FAILED`, so a hidden row
never occupies its `processing|draft` predicate. Retry moves `FAILED → PROCESSING`; if another
visible session already occupies that campaign slot, translate the existing index violation to the
existing `409 ACTIVE_SESSION_EXISTS` code. Do not add a supporting index without query-plan evidence.

The changes are additive and reversible before use. Rollback removes the new columns/check
constraint; after feature use, dropping columns destroys attempt/hide audit data, so production
rollback must first deploy a compatibility version that stops using these fields and must export
or retain required audit data. No status backfill is required. Migration must run before the new
application version; to avoid old binaries writing unfenced state during rollout, drain/stop old
ingestion workers and schedulers before enabling new retry behavior. Verify active processing
rows and the backfill result on the target database.

## API contract

Use the existing `/api/v1/campaigns/{campaignId}/sessions` route family and existing
`{data, meta, errors}` envelope. Errors use `{errors:[{code,message,field}]}`. Implement as
campaign-scoped use cases; do not put role checks only in the controller.

| Operation | Request / response | Success | Expected errors |
|---|---|---|---|
| `POST /{sid}/retry` | Empty body. Return `data:{sessionId,status:"PROCESSING",attemptCount}`; never include `attemptId`. | `202 Accepted` | `403 FORBIDDEN`; `404 SESSION_NOT_FOUND` (including hidden/wrong-campaign); `409 ACTIVE_SESSION_EXISTS` (another campaign session active/draft), `409 SESSION_ATTEMPT_IN_PROGRESS` (same session already processing); `422 SESSION_INVALID_STATUS`; `422 SESSION_RETRY_LIMIT_REACHED`; `422 SESSION_NOT_RETRYABLE`. |
| `DELETE /{sid}` | Empty body. Preserve existing `DRAFT → DISCARDED` behavior; for `FAILED`, set `deleted_at` and keep status `FAILED`. Return existing `{data:null,meta,errors}` envelope. | `200 OK` | `403 FORBIDDEN`; `404 SESSION_NOT_FOUND` (including hidden/wrong-campaign); `422 SESSION_INVALID_STATUS` for other statuses. |
| Existing `GET /`, `GET /{sid}`, `GET /{sid}/status`, `GET /{sid}/diff` | Existing shapes plus fields below. | Existing success codes | Hidden rows resolve as `404 SESSION_NOT_FOUND`; do not reveal hidden-row existence across campaigns. |

`404` covers absent, wrong-campaign, and hidden sessions. `403` is a recognized member lacking
GM permission; retain the repository's existing authentication/membership conventions. `409`
indicates an active retry already underway or unique-index conflict. `422` indicates a failed
precondition such as unsupported status, exhausted attempt cap, or non-retryable classification.
The standard error envelope must remain intact; do not expose SQL constraint names or exception
messages.

DTO changes:

- Session list: exclude hidden rows; preserve `sequenceNumber: number | null`. Attempt count is not
  added to the shared list/appearance DTO.
- Session list `totalCount` excludes hidden rows; hiding a failed row changes list/count only and
  does not renumber committed sessions (failed sessions normally have a null sequence number).
  Current ordering/count and sequence allocation are in `ListSessionsService.java:44-55`,
  `SessionPersistenceAdapter.java:40-43`, `SessionJpaRepository.java:23-30`, and
  `CommitService.java:145-149`.
- Detail/status: `attemptCount: number`; `failureReason: string | null` (existing
  field, constrained to safe stable codes), `failureStage: string | null`,
  `retryable: boolean` (true only when visible, `FAILED`, below the cap, and not permanent);
  preserve `message: null` unless an existing API compatibility test requires
  a generic message.
- Retry response uses a new `SessionRetryResponse` with `sessionId`, `status`, and `attemptCount`;
  no attempt UUID or raw failure text.
- Status GET includes `PENDING` and `PROCESSING` as in-progress; never return `attemptId`.
- Diff/list/detail/status and campaign exports exclude hidden rows. Existing proposal `sessionId`
  remains available as provenance; current proposal reads do not join session content. New
  proposal creation must reject hidden source sessions as `404 SESSION_NOT_FOUND`. Query citation
  JSON remains historical and is not deleted.

Attempt count starts at one on initial claim, increments once on each accepted retry, and never
exceeds three. Concurrent duplicate retry requests: exactly one conditional claim can succeed;
the loser returns 409. Do not return 202 before the claim commits. Dispatch after commit; if dispatch
fails, conditionally transition that attempt to `FAILED` with a safe dispatch code/stage so it can
be manually retried.

## Concurrency and fencing

| Writer | Required checks before write | Stale/late behavior |
|---|---|---|
| Initial worker | Claim `PENDING` atomically, set new `attempt_id`, count, `PROCESSING`; include attempt ID in event. | Duplicate event fails claim and exits. |
| Pipeline stages/listener | Match session ID + `PROCESSING` + current `attempt_id` + `deleted_at IS NULL` for each output/state write. Mark failure with matching attempt. | Zero affected rows means stale/hidden; stop pipeline and do not overwrite new attempt. |
| Timeout scheduler | Select only expired `PROCESSING` attempts; conditional update with captured `attempt_id` and unchanged timeout predicate. | If retry/stage changed the row, update count is zero. It never directly writes without attempt predicate. |
| Retry use case | Conditional `FAILED → PROCESSING`, `deleted_at IS NULL`, retryable, count < 3; assign UUID/increment; observe unique active-session index. | One winner; duplicate or concurrent active session returns defined 409. |
| Soft hide | Conditional `FAILED AND deleted_at IS NULL`; set timestamp without deleting row. | Any worker writes fail due to status or deleted predicate. Repeat hide is 404. A timed-out worker may still be executing externally; its database writes are fenced. |
| Commit/discard | Existing domain guards plus `deleted_at IS NULL`; commit writes transactionally and verifies current attempt where available. | Hidden/stale session cannot commit. |

No background scheduler may resurrect or overwrite `FAILED` after retry. The in-memory executor
cannot cancel a running model call; fencing guarantees database correctness, not physical
cancellation or zero overlap of external model calls after a timeout. The bounded attempt cap
limits duplicate cost. Persistent outbox delivery is out of scope unless product requires guaranteed
dispatch independent of process restarts; current plan treats a lost dispatch as a failed, manually
retryable attempt. If zero overlapping provider calls is a product requirement, the current
in-memory executor plus conditional writes is insufficient and that requirement needs a separate
cancellable-runner design.

## Safe failure contract

Public failure code is a stable allowlist, not the exception class/message:

| Public code | Stage | Retryable | UI treatment |
|---|---|---:|---|
| `EXTRACTION_FAILED` | `EXTRACTION` | Yes | Localized generic extraction failure |
| `ENTITY_RESOLUTION_FAILED` | `ENTITY_RESOLUTION` | Yes | Localized generic analysis failure |
| `CONFLICT_DETECTION_FAILED` | `CONFLICT_DETECTION` | Yes | Localized generic analysis failure |
| `DIFF_GENERATION_FAILED` | `DIFF_GENERATION` | Yes | Localized draft-generation failure |
| `PIPELINE_TIMEOUT` | Last known stage or `UNKNOWN` | Yes | Localized timeout/retry guidance |
| `PIPELINE_INTERRUPTED` | Last known stage or `UNKNOWN` | Yes | Localized interrupted/retry guidance |
| `PIPELINE_DISPATCH_FAILED` | `DISPATCH` | Yes | Generic retry guidance |
| `INVALID_INPUT` / `CONFIGURATION_ERROR` | Known stage | No | Localized non-retryable message; no retry action |
| Unknown/unmapped | `UNKNOWN` | Yes, within cap | Generic failure; diagnostic code only in logs |

Do not expose root cause, raw exception, LLM/provider name, prompt, generated text, narrative,
stack trace, SQL details, or infrastructure addresses. Logs should include session ID, campaign ID,
attempt UUID, stage, allowlisted code, and exception class/message only under current logging policy;
never log raw narrative or credentials. Verify existing log redaction before changing logging.

## Frontend contract

**Components/API:** `apps/web/src/api/sessions.ts`, `types/session.ts`,
`features/input/SessionDetailPage.tsx`, `SessionsListPage.tsx`,
`ProcessingStatusView.tsx`, `features/input/components/DiscardConfirmOverlay.tsx`, and the shared
`components/domain/FocusedOverlay.tsx` only if its UX/accessibility contract is updated. Update
`apps/web/src/locales/en/translation.json` and `es/translation.json` (verify actual locale files at
implementation time).

**Query keys and mutation effects** (confirmed current keys are separate):

| Mutation | Session detail | Session status | Session list |
|---|---|---|---|
| Retry accepted | Set status to `PROCESSING` immediately, then invalidate/refetch detail | Set to `PROCESSING`, enable polling | Invalidate list |
| Retry 409/422/403 | Preserve cached detail and show inline safe error | No polling unless already active | No invalidate needed |
| Hide accepted | Remove detail/status cache entries; navigate to campaign session history | Remove cache; never keep polling | Invalidate list/count |
| Hide failed | Preserve detail/status | Preserve current state | Preserve list |

Use a single shared session query-key helper; do not invalidate only the list. Poll at the existing
2-second interval only while status is `PENDING` or `PROCESSING`; stop for `DRAFT`, `FAILED`,
`COMMITTED`, `DISCARDED`, error, or 404. The route detail must mount or share the status poller
while an initial/retry attempt is in progress. On `DRAFT`, follow the existing diff-review route.
After hide, return to session history and refresh list/count.

Show retry/hide controls only for `FAILED`, `retryable=true` for Retry, under attempt cap, and GM.
Backend remains authoritative. Retry does not need confirmation; disable while pending and show
inline errors for 403/404/409/422. Hide uses an accessible destructive confirmation overlay with
cancel and pending state. Pending errors keep the user on the session detail page.

`sequenceNumber` is `number | null`; render a translated neutral fallback for null. Localized
failure code/stage and action text must exist in English and Spanish. Required keys use the
`input.sessionFailure.*` namespace: `retry`, `retryPending`, `retryLimit`, `retryError`,
`hideFailed`, `hideTitle`, `hideBody`, `hidePending`, `hideError`, `failure.generic`,
`failure.extraction`, `failure.analysis`, `failure.draft`, `failure.timeout`,
`failure.interrupted`, `failure.nonRetryable`, `sequenceUnavailable`.

Accessibility: use a real button with an explicit localized accessible name; confirmation has
`role=dialog`, `aria-modal=true`, and an accessible name/description; initial focus moves into the
dialog, Tab/Shift+Tab remain contained, Escape/cancel closes it, and focus returns to the trigger.
Announce pending/error/success changes with the repository's inline feedback/`aria-live` pattern.
Do not rely on color alone. `FocusedOverlay` is currently centered/fixed and does not implement all
focus behavior described by the UX skill; if changed, run every existing overlay consumer test:
`AnnotationThread`, `ProposalReviewCard`, `DiffReviewPage`, `ProposeChangeButton`, campaign
confirmation overlays, discard, and edit-card overlay. Follow `docs/UX_CONSTITUTION.md` as design
authority rather than copying the current discrepancy.

## Implementation dependency graph

```text
F10.1.1 Failure accounting
        |
F10.1.2 Schema + claim/fencing + scheduler
       / \
      /   \
F10.1.3   F10.1.4
Retry API  Soft-hide API
      \   /
       F10.1.5 Frontend API
              |
       F10.1.6 Frontend UI
              |
       F10.1.7 Regression hardening
```

F10.1.3 and F10.1.4 may be developed in parallel after F10.1.2 fixes the state/schema/error
contract. Frontend API work starts after both endpoint contracts are stable; UI follows typed
mutations. F10.1.7 follows all behavior phases.

## Skills and conventions

All referenced skill files exist under `skills/` (not `.ai/skills/`): `session-ingestion-pipeline`,
`backend-endpoint`, `backend-testing`, `database-migration`, `frontend-api-resource`,
`frontend-testing`, `ux-focused-overlay`, `ux-inline-feedback`, and `ux-skeleton-crafting`.
`skills/SKILLS_INDEX.md` is the entry point. Applicable conventions:

- `session-ingestion-pipeline`: stage ownership, transaction boundaries, async events, failure
  handling and source-of-truth pipeline flow. Its examples contain stale service/provider names;
  verify against current code.
- `backend-endpoint`: controller delegates to application use case; campaign membership and roles
  are resolved server-side; standard API error envelope and DTO mapping.
- `backend-testing`: focused domain/use-case/controller/persistence tests, PostgreSQL integration
  tests for constraints, and `@DisplayName` on every `@Test`.
- `database-migration`: append-only Liquibase change sets, master changelog registration, rollback,
  forward-compatible additive rollout, and no edits to historical migrations.
- `frontend-api-resource`: typed API methods/hooks, shared query-key conventions, explicit cache
  invalidation and error state; no untyped response payloads.
- `frontend-testing`: test behavior, role/pending/error/keyboard states, and use existing Vitest
  patterns; every test has a descriptive display name where required by project conventions.
- `ux-focused-overlay`: confirmation interaction, focus handling and keyboard behavior. Skill's
  anchored-overlay guidance conflicts with current centered `FocusedOverlay`; follow the UX
  Constitution and explicitly test affected shared consumers.
- `ux-inline-feedback`: inline, actionable success/error states rather than toasts.
- `ux-skeleton-crafting`: preserve loading skeleton behavior and avoid layout shifts while adding
  pending/retry states.

The backend skill examples should not override repository conventions in root
`CLAUDE.md`/`apps/api/CLAUDE.md`; use `mvn` commands from `apps/api` and `npm` commands from
`apps/web`. Do not edit `.ai/` references or `ROADMAP_V2.md`.
