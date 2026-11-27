# F10.1.6 — Add recovery UI, polling, and localization

**Estimate:** M · **Status:** Not started · **Depends on:** F10.1.5

## Goal

Expose safe retry/hide actions and failure feedback on session pages, align polling and role gates
with the backend, and deliver localized accessible interactions.

## Verified files

- `apps/web/src/features/input/SessionDetailPage.tsx`
- `apps/web/src/features/input/SessionDetailPage.test.tsx`
- `apps/web/src/features/input/SessionsListPage.tsx`
- `apps/web/src/features/input/SessionsListPage.test.tsx`
- `apps/web/src/features/input/ProcessingStatusView.tsx`
- `apps/web/src/features/input/ProcessingStatusView.test.tsx`
- `apps/web/src/features/input/components/DiscardConfirmOverlay.tsx`
- `apps/web/src/features/input/components/DiscardConfirmOverlay.test.tsx`
- `apps/web/src/components/domain/FocusedOverlay.tsx`
- `apps/web/src/components/domain/FocusedOverlay.test.tsx`
- `apps/web/src/components/domain/AnnotationThread.test.tsx`
- `apps/web/src/components/domain/ProposeChangeButton.test.tsx`
- `apps/web/src/features/proposals/ProposalReviewCard.test.tsx`
- `apps/web/src/features/input/DiffReviewPage.test.tsx`
- `apps/web/src/features/campaigns/components/DeleteCampaignConfirmOverlay.test.tsx`
- `apps/web/src/features/campaigns/components/RemoveMemberConfirmOverlay.test.tsx`
- `apps/web/src/features/input/components/EditCardOverlay.test.tsx`
- `apps/web/src/i18n/locales/en.json`
- `apps/web/src/i18n/locales/es.json`
- `apps/web/src/i18n/index.test.ts`
- Design authority: `docs/UX_CONSTITUTION.md`

`FocusedOverlay` has consumers beyond session input. If changing it, test
`AnnotationThread`, `ProposalReviewCard`, `DiffReviewPage`, `ProposeChangeButton`, campaign
confirmation overlays, discard, and edit-card overlay. Do not refactor unrelated overlay users.

## Tasks

1. Correct the role mismatch: only GM sees Retry/Hide, and only on a visible `FAILED` session;
   Retry also requires `retryable` and count below cap. Backend remains authoritative.
2. Add Retry pending/error state and immediate `PROCESSING` cache update. Mount/share polling on
   session detail for both `PENDING` and `PROCESSING`; retain 2-second interval and stop on terminal
   states, error, or 404.
3. On `DRAFT`, use existing diff-review navigation. On retry 403/404/409/422, show a localized
   inline error and retain the page/input.
4. Add a destructive Hide confirmation with cancel/pending/error. On success remove detail/status
   cache and navigate to campaign session history; refresh list/count.
5. Render safe failure code/stage through localized copy only; never render raw API `message` or
   exception details. Render `sequenceNumber=null` with a localized neutral fallback.
6. Add English and Spanish keys under `input.sessionFailure.*`.
   Verify both locale trees contain the same required keys using the existing i18n test patterns.
7. Follow `docs/UX_CONSTITUTION.md`, `ux-focused-overlay`, `ux-inline-feedback`, and
   `ux-skeleton-crafting`. Ensure dialog accessible name/description, initial focus, focus
   containment/restoration, Escape/cancel behavior, keyboard operation, and `aria-live` feedback.
   The documented anchored-overlay requirement conflicts with current centered/fixed
   `FocusedOverlay`; resolve by following UX Constitution and do not silently repeat the discrepancy.

## Tests

- `SessionDetailPage.test.tsx`: GM-only controls; retryability/attempt cap; retry pending/errors;
  failure code rendering; hide confirm/cancel/pending/success navigation.
- `ProcessingStatusView.test.tsx`: poll `PENDING` and `PROCESSING`; stop at `DRAFT`/`FAILED`;
  draft navigation remains unchanged.
- `SessionsListPage.test.tsx`: GM-only actions and null sequence fallback if list displays it.
- `DiscardConfirmOverlay.test.tsx` and `FocusedOverlay.test.tsx`: keyboard/focus behavior and
  existing discard semantics.
- Run all listed tests for existing `FocusedOverlay` consumers if that shared component changes.
- Run axe checks for the new confirmation and live error/pending states.

Every test must use the existing descriptive naming/display-name convention.

## Acceptance criteria

- UI gates mirror backend roles/status without being treated as security.
- Pending/error states are visible and actionable; cache/list/navigation behavior matches contract.
- Polling works from initial `PENDING` through retry and stops on terminal states.
- English/Spanish translations are complete; null sequence is never rendered as `#null`.
- Confirmation and status updates meet keyboard and screen-reader requirements; existing overlay
  behavior remains green.

## Rollback

Revert UI/hook integration and locale additions together; retain backend endpoints/schema. Do not
reintroduce stale `#null` rendering or Editor-only frontend authorization mismatch.

## Skills

Read `skills/SKILLS_INDEX.md`, `skills/frontend-api-resource/SKILL.md`,
`skills/frontend-testing/SKILL.md`, `skills/ux-focused-overlay/SKILL.md`,
`skills/ux-inline-feedback/SKILL.md`, `skills/ux-skeleton-crafting/SKILL.md`, and
`docs/UX_CONSTITUTION.md` before UI design.

## Kickoff prompt

```text
Implement F10.1.6 only: add session detail retry/hide UI, safe failure presentation, polling,
confirmation, navigation, and EN/ES localization. Read root and apps/web CLAUDE.md, SKILLS_INDEX,
frontend-api-resource, frontend-testing, ux-focused-overlay, ux-inline-feedback,
ux-skeleton-crafting, and docs/UX_CONSTITUTION.md first. Verify F10.1.5 typed mutations exist.
Controls are GM-only and only for FAILED; Retry also requires retryable and remaining attempts.
Poll PENDING and PROCESSING every existing 2 seconds, stop at terminal/error/404. Hide uses an
accessible confirmation and on success clears detail/status and returns to session history. Never
render raw exception text. Fix null sequence display. The current FocusedOverlay conflicts with
the UX skill's anchored guidance; follow UX_CONSTITUTION and if changing the shared overlay test
every listed consumer. Do not edit backend, migrations, ROADMAP_V2.md, or .ai files. Add focused
component/API integration tests and axe/keyboard coverage. Run focused Vitest tests and npm run
type-check from apps/web.
```
