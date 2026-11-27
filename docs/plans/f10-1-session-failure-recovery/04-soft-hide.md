# F10.1.4 — Add failed-session soft hide

**Estimate:** M · **Status:** Not started · **Depends on:** F10.1.2

## Goal

Add a campaign-scoped GM-only operation that hides a failed session from ordinary access while
retaining its row, narrative, proposals, query references, and provenance.

## Verified files

- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java`
- New `apps/api/src/main/java/com/bluesteel/application/service/session/HideFailedSessionService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/ListSessionsService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionDetailService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionStatusService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionDiffService.java`
- `apps/api/src/main/java/com/bluesteel/application/service/proposal/ProposalCreationService.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/proposal/ProposalPersistenceAdapter.java`
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/proposal/ProposalResponse.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionJpaRepository.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapter.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapter.java`
- `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/worldstate/EntityLinksReadAdapter.java`
- `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionSummaryResponse.java`
- Tests: `apps/api/src/test/java/com/bluesteel/adapters/in/web/session/SessionControllerTest.java`,
  `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/session/SessionPersistenceAdapterIT.java`,
  `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapterIT.java`,
  `apps/api/src/test/java/com/bluesteel/adapters/out/persistence/worldstate/EntityLinksReadAdapterIT.java`,
  `apps/api/src/test/java/com/bluesteel/application/proposal/ProposalCreationServiceTest.java`,
  plus new `apps/api/src/test/java/com/bluesteel/application/session/HideFailedSessionServiceTest.java`.

## Tasks

1. Extend existing `DELETE /{sid}` behavior: preserve draft discard as-is, and dispatch `FAILED`
   to a GM-authorized, campaign-scoped conditional soft hide. Preserve `FAILED`; set `deleted_at`;
   repeated hide returns 404.
2. Ensure all regular session list/count/detail/status/diff queries exclude hidden rows; return
   404 for hidden detail/status/diff. Preserve campaign scoping and not-found concealment.
3. Ensure campaign export and entity/session appearance browsing exclude hidden rows from ordinary
   results but preserve underlying references/data. Existing proposal responses must retain their
   `sessionId` provenance; current reads do not join session content, so do not add a new
   placeholder UI.
4. Reject new proposal creation that cites a hidden session with `404 SESSION_NOT_FOUND` while
   keeping existing proposal records and `proposals.session_id` unchanged.
   The existing provenance picker uses the normal session-list endpoint; verify hidden rows are
   absent there rather than adding a second client-side filter.
5. Ensure a late worker cannot write after hide: every write already uses attempt fencing from
   F10.1.2; additionally confirm all paths include `deleted_at IS NULL`.
6. Map an invalid state to the documented 422 error. Do not hard-delete rows or invoke cascade
   cleanup.

## Tests

- `HideFailedSessionServiceTest`: GM success; non-GM 403; missing/wrong campaign/hidden 404;
  non-FAILED status 422; no narrative/proposal deletion.
- Controller: `DELETE /{sid}` preserves draft discard; for `FAILED`, soft hides and returns the
  existing success envelope with `data: null`; repeat hide 404.
- `SessionPersistenceAdapterIT`: hidden rows absent from list/count/detail/status/diff.
- `CampaignExportReadAdapterIT`: hidden session omitted; other export rows and provenance retained.
- `EntityLinksReadAdapterIT`: ordinary appearance views omit hidden sessions without deleting links.
- `ProposalCreationServiceTest`: new proposal against hidden session returns 404; existing proposal
  and `sessionId` provenance remain unchanged.

Use `@DisplayName` on every new backend `@Test`.

## Acceptance criteria

- Only a visible `FAILED` session can be hidden, and only by a GM in the owning campaign.
- Hide never issues SQL `DELETE`; FK data and historical references remain intact.
- Hidden rows are omitted from ordinary list/count/detail/status/diff/export/session-appearance
  paths; proposal source creation rejects hidden rows.
- Late attempt writes cannot mutate a hidden row.

## Rollback

Disable the endpoint and visibility filters only after confirming no hidden rows have been created.
Do not clear `deleted_at` values automatically. If rolling back after use, preserve the column and
document an explicit, audited restoration procedure; do not make hidden records visible silently.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/backend-endpoint/SKILL.md`,
`skills/backend-testing/SKILL.md`, `skills/database-migration/SKILL.md`, and `apps/api/CLAUDE.md`.
No new FK cleanup or destructive migration is permitted.

## Kickoff prompt

```text
Implement F10.1.4 only: add a GM-only campaign-scoped DELETE endpoint that soft-hides FAILED
sessions using deleted_at, retaining the session row, narrative, proposal FK, and all provenance.
Read CLAUDE.md, apps/api/CLAUDE.md, skills/SKILLS_INDEX.md, backend-endpoint, backend-testing,
and database-migration. Verify F10.1.2 schema/fencing exists first. Filter hidden sessions from
ordinary list/count/detail/status/diff, campaign export, and entity/session appearance views;
reject new proposal creation against a hidden source while preserving existing proposals. Return
404 for hidden records and use the standard error envelope. Do not hard-delete, change
ROADMAP_V2.md, or edit .ai files. Add focused service/controller/PostgreSQL tests and @DisplayName
on all tests. Run API unit and relevant integration tests plus spotless:check from apps/api.
```
