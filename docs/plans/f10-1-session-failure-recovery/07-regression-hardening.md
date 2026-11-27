# F10.1.7 — Harden recovery contract and regressions

**Estimate:** M · **Status:** Not started · **Depends on:** F10.1.3, F10.1.4, F10.1.6

## Goal

Verify cross-layer concurrency, retained provenance, export/query behavior, and documentation
coherence after retry and hide are integrated. This phase is mergeable as final test/documentation
hardening and does not add new product behavior.

## Verified files

- Backend regression tests:
  - `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionRecoveryAdapterIT.java`
  - `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapterIT.java`
  - `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapterIT.java`
  - `apps/api/src/test/java/com/bluesteel/adapters/in/web/campaign/CampaignExportFlowIT.java`
  - `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/worldstate/EntityLinksReadAdapterIT.java`
  - `apps/api/src/test/java/com/bluesteel/adapters/in/web/query/QueryHistoryReadFlowIT.java`
  - `apps/api/src/test/java/com/bluesteel/adapters/in/web/session/SessionControllerTest.java`
- Frontend tests:
  - `apps/web/src/api/sessions.test.ts`
  - `apps/web/src/features/input/SessionDetailPage.test.tsx`
  - `apps/web/src/features/input/ProcessingStatusView.test.tsx`
  - `apps/web/src/features/input/SessionsListPage.test.tsx`
  - `apps/web/src/components/domain/ProposalSubmitForm.test.tsx`
  - `apps/web/src/components/domain/FocusedOverlay.test.tsx`
- Documentation:
  - Review `docs/ARCHITECTURE.md` and `docs/app_feature_inventory/session_ingestion.md`; root
    `CLAUDE.md` marks docs read-only, so modify them only when that implementation task explicitly
    authorizes documentation edits.
  - `docs/roadmap/ROADMAP_V3.md` only if phase IDs/statuses or delivered behavior must be updated;
    preserve the V3 inactive note.

## Tasks

1. Add/complete race integration coverage for concurrent retry, active-session index conflict,
   timeout-vs-retry, old attempt vs new attempt, and old attempt vs hide. Verify that late writes
   affect zero rows and never change the new/hidden record.
2. Verify soft hide preserves narrative blocks, proposals, query history citations, and relevant
   world-state provenance; ordinary lists/exports/appearances omit hidden sessions.
3. Verify proposal creation rejects hidden session sources while existing proposal history and
   `sessionId` provenance remain intact; verify the proposal provenance picker receives only
   visible sessions. Do not assume current proposal reads join session content.
4. Verify status polling from `PENDING`, `PROCESSING`, retry, and terminal state; verify deleted
   detail/status returns 404 and clears frontend cache.
5. Verify 403/404/409/422 error envelope and public safe failure allowlist with no raw exception,
   stack trace, prompt, or narrative.
6. Prepare updates for the API catalogue and session feature inventory to reflect final behavior
   and correct existing drift (oversized input status, actual polling interval, missing
   failure-stage handling). Modify those read-only docs only if explicitly authorized; otherwise
   include the exact corrections in a documentation follow-up request. Do not edit `ROADMAP_V2.md`
   or `.ai/` references.
7. Update roadmap statuses only after each implementation phase is actually merged and only with
   explicit documentation authorization; do not mark V3 active or claim future phases are complete
   prematurely.

## Tests

- PostgreSQL integration tests exercise true concurrent conditional claims and existing FK
  behavior. Avoid replacing them with mocked repository tests.
- Run the focused backend session, export, proposal/query and world-state suites listed above.
- Run the focused frontend session/API/overlay tests; include axe checks if shared overlay changed.
- Run API `mvn spotless:check` and `mvn test`; run web `npm run type-check` and `npm test`.
- Run `mvn verify` only where container-backed integration tests were not covered by the focused
  selectors or when repository CI requires it.

## Acceptance criteria

- All race/fencing cases pass against PostgreSQL; old workers cannot write after recovery, retry,
  or hide.
- Proposal/export/query/session appearance consumers preserve historical data while hiding content
  from normal access.
- Error envelope, safe failure mapping, role gates, cache invalidation, polling, locale, and
  accessibility contracts agree end to end.
- Architecture and feature-inventory corrections are applied only with explicit documentation
  authorization or recorded for an authorized follow-up; prohibited `.ai/` and V2 files remain
  untouched.
- Roadmap status accurately reflects merged phases; V3 remains inactive.

## Rollback

Revert only test/documentation changes that are inaccurate; do not revert already-shipped additive
schema or runtime behavior as part of this verification phase. If a race test reveals unsafe
behavior, stop release and return to the owning phase; do not weaken the test or mark the phase done.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/backend-testing/SKILL.md`,
`skills/frontend-testing/SKILL.md`, `skills/database-migration/SKILL.md`, `docs/ARCHITECTURE.md`,
and `docs/app_feature_inventory/session_ingestion.md`.

## Kickoff prompt

```text
Implement F10.1.7 only: complete cross-layer recovery regression tests and prepare corrections for
the API catalogue and session feature inventory. Read root CLAUDE.md, both app CLAUDE.md files, SKILLS_INDEX,
backend-testing, frontend-testing, database-migration, docs/ARCHITECTURE.md, and
docs/app_feature_inventory/session_ingestion.md. Verify all preceding phases are merged and
their actual contracts match 00-overview.md; if a race exposes unsafe behavior, stop and identify
the owning phase instead of weakening the test. Cover concurrent retry, active index conflict,
scheduler/worker fencing, hide/late write, proposal provenance, export/query/appearance filters,
safe failure exposure, frontend polling/cache, and overlay accessibility. Correct the documented
input-size status, polling interval, and missing resolution/conflict failure handling only if
documentation edits are explicitly authorized; otherwise record them for follow-up. Do not edit
ROADMAP_V2.md or any .ai file; preserve the V3 inactive notice. Run relevant PostgreSQL integration
tests, focused frontend tests, spotless:check, backend tests, npm run type-check, and npm test.
```
