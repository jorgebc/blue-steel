# F10.1.2 — Add attempt persistence and fencing

**Estimate:** L · **Status:** Not started · **Depends on:** F10.1.1

## Goal

Persist attempt identity/count and soft-hide metadata, and make initial processing, pipeline writes,
and recovery conditional on the current attempt. This phase establishes the safety primitive used
by retry and hide; it does not expose a retry/delete endpoint.

## Verified files

- `apps/api/src/main/resources/db/changelog/0031_session_failure_recovery.xml` (new; confirm
  migration sequence at execution)
- `apps/api/src/main/resources/db/changelog/db.changelog-master.xml`
- `apps/api/src/main/java/com/bluesteel/domain/session/Session.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionJpaEntity.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionJpaRepository.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapter.java`
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRepository.java`
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRecoveryPort.java`
- `apps/api/src/main/java/com/bluesteel/application/event/SessionSubmittedEvent.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/ExtractionPipelineService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/DiffGenerationService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionTimeoutRecoveryScheduler.java`
- `apps/api/src/test/java/com/bluesteel/domain/session/SessionTest.java`
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapterIT.java`
- `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapterIT.java`
- `apps/api/src/test/java/com/bluesteel/application/session/SessionTimeoutRecoverySchedulerTest.java`
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java`
- `apps/api/src/test/java/com/bluesteel/application/session/ExtractionPipelineServiceTest.java`
- `apps/api/src/test/java/com/bluesteel/application/session/DiffGenerationServiceTest.java`

Keep the migration additive and append-only. A change to the migration sequence is allowed only if
a newer change set was merged after this plan was written.

## Tasks

1. Add `attempt_count`, `attempt_id`, `failure_stage`, and `deleted_at` columns and the check
   constraint described in `00-overview.md`. Backfill `PENDING` as zero attempts and all started
   pipeline states as one; assign UUIDs to existing started rows.
2. Leave the existing active-session partial unique index unchanged. Since soft hide accepts only
   `FAILED`, hidden rows cannot occupy its `processing|draft` predicate. Do not alter historical
   migration files.
3. Extend domain/persistence mapping for metadata and nullable sequence numbers without treating
   attempt UUID as public API data.
4. Add an atomic initial claim from `PENDING` with a fresh attempt ID. Carry that ID on the
   submitted event and require it in all stage success/failure writes.
5. Change timeout/startup recovery from unconditional status update to conditional updates scoped
   to status, captured attempt ID, age, and non-hidden row. Startup must not fail recently updated
   `PROCESSING` rows belonging to another live instance. Zero-row update is an expected stale
   result, not a failure.
6. Make every write of `diff_payload`, failure metadata, or final stage status reject a stale
   attempt. Ensure commit/discard reject hidden rows; do not change unrelated domain behavior.
7. Record deployment/runbook constraints: drain old workers/schedulers before activating new
   unfenced code; validate active rows and backfill; document rollback after feature use.

## Tests

- Domain `SessionTest` cases for attempt metadata invariants and guarded transitions.
- `SessionRecoveryAdapterIT`: stale scheduler attempt does not fail a newer attempt; current
  expired attempt is failed; hidden row is never recovered.
- `SessionPersistenceAdapterIT`: stale attempt cannot save diff/failure; duplicate initial claims
  produce one winner; existing active-session index still rejects a second `processing|draft`
  session in a campaign.
- `SessionTimeoutRecoverySchedulerTest`: scheduler delegates conditional recovery and handles
  zero updated rows without reporting a successful transition.
- `ExtractionPipelineServiceTest` and `DiffGenerationServiceTest`: writes with stale attempt IDs
  do not persist processing/draft/failure output.
- Liquibase integration coverage through the existing PostgreSQL test harness; verify existing
  rows backfill correctly.

All backend tests use `@DisplayName`.

## Acceptance criteria

- All relevant pipeline writes and recovery updates are fenced by the current UUID.
- Timeout/failure state cannot be overwritten by a late old attempt.
- Existing sessions migrate with correct count/ID values; `PENDING` remains at zero.
- Existing unique index still enforces one active/draft session per campaign; hiding failed rows
  does not alter its predicate.
- Migration is append-only, registered, rollback-described, and passes PostgreSQL integration tests.

## Rollback

Before feature use, rollback may drop the new columns/check constraint; the existing partial unique
index is unchanged.
After attempt/hide data exists, do not drop those columns without preserving required audit data;
first deploy code that no longer reads/writes them, export/retain required data, then run the
documented rollback. Drain workers before rolling back to code that does not fence writes.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/session-ingestion-pipeline/SKILL.md`,
`skills/database-migration/SKILL.md`, `skills/backend-testing/SKILL.md`, and
`apps/api/CLAUDE.md`. Migration history is append-only; do not edit prior change sets.

## Kickoff prompt

```text
Implement F10.1.2 only: add the additive Liquibase metadata and attempt-ID conditional claim/write
fencing for initial ingestion and recovery. Read CLAUDE.md, apps/api/CLAUDE.md, skills/SKILLS_INDEX.md,
skills/session-ingestion-pipeline, database-migration, and backend-testing. Start by checking the
latest migration number and actual persistence/event flow. Implement attempt_count, attempt_id,
failure_stage, deleted_at; safe backfill and rollback; leave the active-session partial unique index
unchanged; fence all worker/recovery writes by current attempt ID and visibility. Do not
add retry/delete API endpoints or frontend changes. Do not edit historical migrations, ROADMAP_V2.md,
or .ai files. Add PostgreSQL integration tests for backfill, duplicate claims, stale worker writes,
stale scheduler writes, and index semantics; add @DisplayName on every test. Run focused API tests,
the relevant integration tests, and spotless:check from apps/api. Document required worker drain
before rollout and do not claim that fencing cancels an in-flight model call.
```
