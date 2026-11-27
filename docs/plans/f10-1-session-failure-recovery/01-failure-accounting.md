# F10.1.1 — Correct pipeline failure accounting

**Estimate:** M · **Status:** Not started · **Depends on:** none

## Goal

Make every pipeline stage failure reach a stable failed-session state promptly, without losing the
stage-specific safe code or masking the original exception. Persisted stage metadata is added in
F10.1.2; this phase first closes the resolution/conflict failure gap.

## Verified files

- `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/ExtractionPipelineService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/EntityResolutionService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/ConflictDetectionService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/DiffGenerationService.java`
- `apps/api/src/test/java/com/bluesteel/application/session/SessionIngestionEventListenerTest.java`

Only touch the stage implementations/tests needed to preserve existing exception and transaction
patterns. Do not add schema or retry APIs in this phase.

## Tasks

1. Trace the full listener pipeline and existing extraction/diff failure writes; settle on one
   failure-recording owner so failures are not written twice.
2. Ensure entity-resolution and conflict-detection exceptions persist the safe codes
   `ENTITY_RESOLUTION_FAILED` and `CONFLICT_DETECTION_FAILED` before propagating. Preserve the
   existing exception for logs and test diagnostics; do not put raw exception text into session
   response fields. Persisted `failure_stage` is added in F10.1.2.
3. Ensure event/listener failures that occur after a session is claimed but before a stage begins
   are recorded as an appropriate safe failure, without changing pre-session input validation.
4. Preserve transaction boundaries and ensure a failure-recording exception does not silently
   replace the original pipeline failure. If failure persistence itself fails, log both failures
   with session identifiers and surface the persistence failure to the async error handler.
5. Add `@DisplayName` to any new/modified `@Test` method missing one.

## Tests

In `SessionIngestionEventListenerTest` add focused cases:

- `shouldMarkSessionFailedWhenEntityResolutionThrows`
- `shouldMarkSessionFailedWhenConflictDetectionThrows`
- `shouldPreserveOriginalFailureWhenFailurePersistenceSucceeds`
- `shouldRecordFailurePersistenceErrorWithoutReportingPipelineSuccess`
- `shouldNotChangeInputValidationFailuresBeforeSessionCreation`

Adapt names to existing method style, but cover these behaviors and ensure existing extraction and
diff failure cases still pass.

## Acceptance criteria

- Resolution/conflict failures no longer remain `PROCESSING` until timeout recovery.
- Failure code/stage is safe and stable; exception text is not exposed through API fields.
- The original exception remains available to logs, not swallowed or replaced by a success result.
- Existing successful pipeline, draft creation, and pre-session validation behavior is unchanged.

## Rollback

Revert only this phase's listener/stage/test changes. The behavior is backward-compatible with
current persistence; it adds no schema dependency. Do not revert unrelated worktree changes.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/session-ingestion-pipeline/SKILL.md`,
`skills/backend-testing/SKILL.md`, and `apps/api/CLAUDE.md`. Follow current service names and
pipeline boundaries; the skill examples may be stale.

## Kickoff prompt

```text
Implement F10.1.1 only: make every existing ingestion stage failure, especially Entity Resolution
and Conflict Detection, persist a safe failure code/stage before the async listener exits. Read
CLAUDE.md, apps/api/CLAUDE.md, skills/SKILLS_INDEX.md, skills/session-ingestion-pipeline, and
skills/backend-testing first. Inspect the listener and current extraction/diff failure paths and
choose one failure-recording owner to avoid duplicate writes. Do not add retry/delete endpoints,
schema/migrations, frontend changes, or edit ROADMAP_V2.md/.ai files. Add focused tests for both
missing failure paths, error preservation, and existing extraction/diff behavior; add @DisplayName
to every test. Run the focused backend tests and spotless:check from apps/api. Leave all unrelated
files and user changes untouched.
```
