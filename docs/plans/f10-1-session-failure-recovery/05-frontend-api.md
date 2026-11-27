# F10.1.5 — Add frontend session recovery API contract

**Estimate:** S · **Status:** Not started · **Depends on:** F10.1.3 and F10.1.4 API contracts

## Goal

Add typed retry/hide API operations and consistent query-key behavior without changing UI
presentation. This is independently testable through API client/hook tests.

## Verified files

- `apps/web/src/api/sessions.ts`
- `apps/web/src/api/sessions.test.ts`
- `apps/web/src/types/session.ts`
- `apps/web/src/features/input/SessionDetailPage.tsx` (read call sites only unless hook integration
  requires a minimal update)
- `apps/web/src/features/input/SessionsListPage.tsx` (read call sites only)
- `apps/web/src/features/input/ProcessingStatusView.tsx` (read call sites only)

## Tasks

1. Add typed mutation methods for `POST /retry` and `DELETE` using the existing API client and
   standard error-envelope handling.
2. Add shared session query-key helpers if needed; retain distinct detail/status keys but provide
   explicit operations for each.
3. Define typed response fields for attempt count, safe failure code/stage, retryable, and nullable
   `sequenceNumber`. Do not expose internal `attemptId`.
4. Retry success updates detail/status to `PROCESSING` and invalidates list; hide success removes
   detail/status cache entries and invalidates list/count.
5. Failed mutation preserves cached data and exposes typed error codes for inline UI handling.
6. Ensure no broad catch/success-shaped fallback hides network or contract errors.

## Tests

In `apps/web/src/api/sessions.test.ts` cover:

- Retry uses the expected method/path and parses a 202 response.
- Retry success updates detail/status and invalidates list.
- Hide uses the expected method/path and removes detail/status while invalidating list.
- 403/404/409/422 preserve cache and surface the original machine-readable error.
- Nullable sequence numbers and attempt/failure fields parse without coercion.

Use existing Vitest patterns; test observed behavior, not query-key constants alone.

## Acceptance criteria

- API layer is fully typed and uses existing transport/error conventions.
- Query invalidation matrix matches `00-overview.md`.
- No UI controls or locale text are introduced in this phase.

## Rollback

Remove the new API methods/query helpers and restore prior types only after dependent UI is reverted.
Backend endpoints and schema remain backward-compatible and must not be rolled back by this phase.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/frontend-api-resource/SKILL.md`,
`skills/frontend-testing/SKILL.md`, and `apps/web/CLAUDE.md`.

## Kickoff prompt

```text
Implement F10.1.5 only: add typed frontend retry/hide API mutations, response types, shared query-key
helpers, and cache invalidation for detail/status/list. Read root CLAUDE.md, apps/web/CLAUDE.md,
skills/SKILLS_INDEX.md, frontend-api-resource, and frontend-testing. Verify the backend endpoint
and DTO contract from 00-overview.md is implemented; do not guess field/error names. Set
sequenceNumber to number | null and add only documented attempt/failure fields; never expose
attemptId. Cover retry/hide transport, success invalidation, and 403/404/409/422 errors in
apps/web/src/api/sessions.test.ts. Do not add UI/buttons/locales, change unrelated API resources,
ROADMAP_V2.md, or .ai files. Run the focused API tests and npm run type-check from apps/web.
```
