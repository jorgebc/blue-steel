# F10.1.3 — Add bounded manual retry

**Estimate:** M · **Status:** Not started · **Depends on:** F10.1.2

## Goal

Add a GM-authorized, campaign-scoped retry endpoint that atomically claims a failed session,
restarts from its retained original narrative, and returns a stable response for status polling.

## Verified files

- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java`
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionStatusResponse.java`
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionDetailResponse.java`
- New `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionRetryResponse.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionStatusService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionDetailService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/SubmitSessionService.java`
- `apps/api/src/main/java/com/bluesteel/application/port/out/session/SessionRepository.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java`
- `apps/api/src/main/java/com/bluesteel/application/event/SessionSubmittedEvent.java`
- New `apps/api/src/main/java/com/bluesteel/application/service/session/RetrySessionService.java`
- `apps/api/src/test/java/com/bluesteel/domain/session/SessionTest.java`
- `apps/api/src/test/java/com/bluesteel/adapters/in/web/session/SessionControllerTest.java`
- `apps/api/src/test/java/com/bluesteel/application/session/GetSessionStatusServiceTest.java`
- New `apps/api/src/test/java/com/bluesteel/application/session/RetrySessionServiceTest.java`.

## Tasks

1. Add a dedicated retry use case and repository atomic claim; validate campaign membership,
   GM role, same-campaign session, `FAILED`, visible row, retryable code, and count below three.
2. On accepted claim, assign a new attempt UUID, increment once, clear prior failure/diff metadata,
   preserve `narrative_blocks`, and publish the existing event carrying the claimed ID after commit.
3. If another `processing|draft` session exists in the campaign, or a duplicate request loses the
   claim, translate the constraint/claim result to the API's documented `409` code. Never leak
   SQL/constraint details.
4. Return `202 Accepted` with `SessionRetryResponse(sessionId,status,attemptCount)`. Expose no
   attempt UUID or raw failure text.
5. Extend detail/status DTOs with safe failure code/stage and `retryable`; ensure status remains
   useful to the frontend for the new attempt.
6. Ensure a dispatch failure transitions only the claimed attempt to a safe failed state.
   Do not add a queue/outbox or automatic retry.

## Tests

- `RetrySessionServiceTest`: GM success; non-GM 403; missing/wrong-campaign/hidden 404; wrong
  status 422; non-retryable 422; cap 422; duplicate retry 409; active campaign conflict 409;
  original narrative retained and new attempt ID/count emitted.
- `SessionControllerTest`: `POST /{sid}/retry` returns 202 and envelope; maps all listed errors.
- `GetSessionStatusServiceTest`: safe failure code/stage/retryable and no raw exception message.
- PostgreSQL `SessionPersistenceAdapterIT`: concurrent claim exactly one winner and unique-index
  conflict translation.

Every `@Test` needs a human-readable `@DisplayName`.

## Acceptance criteria

- Retry is manual, GM-only, from the beginning, and capped at 3 total attempts.
- Only one attempt can claim a session or campaign active slot.
- Failed or hidden sessions cannot be retried; original input remains available.
- API returns the documented status/error envelope and never exposes internal attempt token or
  raw exception details.

## Rollback

Disable/remove the endpoint and retry UI integration only after no new retry attempt is in flight.
Keep additive migration columns and data; do not roll back F10.1.2 schema from this phase. Drain
workers and use a compatibility release before removing any code that consumes attempt IDs.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/backend-endpoint/SKILL.md`,
`skills/backend-testing/SKILL.md`, `skills/session-ingestion-pipeline/SKILL.md`, and
`apps/api/CLAUDE.md`. Use existing membership/role guards and the standard error envelope.

## Kickoff prompt

```text
Implement F10.1.3 only: create a GM-only POST retry use case/endpoint for FAILED sessions, with
manual restart from retained original narrative, attempt cap 3 total, atomic claim, safe error
mapping, and 202 response. Read CLAUDE.md, apps/api/CLAUDE.md, skills/SKILLS_INDEX.md,
backend-endpoint, backend-testing, and session-ingestion-pipeline. Verify the F10.1.2 attempt
metadata/fencing is present before coding; stop and report if it is not. Map active-session and
duplicate claim conflicts to the contract in 00-overview.md; never leak SQL errors or exception
text. Do not implement soft hide or frontend work; do not add a durable queue/outbox, automatic
retry, edit historical migrations, ROADMAP_V2.md, or .ai files. Add focused use-case/controller and
PostgreSQL concurrent-claim tests; @DisplayName every test. Run focused API tests, relevant
integration tests, and spotless:check from apps/api.
```
