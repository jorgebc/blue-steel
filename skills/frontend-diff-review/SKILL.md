---
name: frontend-diff-review
description: >
  Use this skill whenever you are building or modifying any part of the diff review screen in
  `apps/web/src/features/input/`. Triggers include: "diff review", "UNCERTAIN card", "conflict
  card", "commit button", "diff card", "resolution required", "session review UI", "accept card",
  "delete card", "edit card", "narrative summary header", "DiffCard component", or any task
  involving the session ingestion review flow. This is the most complex and business-critical UI
  in the application — read this before touching any part of it.
---

# Frontend — Diff Review Screen

The diff review screen is the trust boundary between AI extraction and world state. It is
displayed when a session reaches `draft` status. It renders four types of extraction result
cards, enforces mandatory resolution of UNCERTAIN entities, and constructs a commit payload that
the backend validates independently. Getting this screen wrong can result in corrupt world state.

## Context

**How the diff review screen is reached:**

1. User submits a session summary (`POST /campaigns/{id}/sessions`).
2. Frontend polls `GET /sessions/{id}/status` until `status === 'draft'` or `'failed'`.
3. On `draft`: frontend fetches `GET /sessions/{id}/diff` and renders the review screen.
4. On `failed`: frontend renders the failure message with `failureReason`.

If the user closes the browser mid-review, the draft diff is stored server-side in
`sessions.diff_payload`. On return, the frontend checks for an existing draft and offers to
resume (D-054, D-056).

**Relevant decisions:**
- D-004: Structured diff review model
- D-005: Narrative summary header shown before the diff
- D-006: Existing entities show delta only (changed fields)
- D-007: New entities show full extracted profile
- D-033: Conflict warnings are non-blocking but must be acknowledged
- D-042: UNCERTAIN cards must be resolved before commit; backend also enforces with `422`
- D-053: No "add" action in v1 — only `accept`, `edit`, `delete`

**Card types in the diff:**

| Card Type | When shown | User action |
|---|---|---|
| **New entity** | Entity not in world state | Accept, Edit inline, Delete |
| **Delta (existing entity)** | Entity exists, changed this session | Accept, Edit inline, Delete |
| **UNCERTAIN** | AI cannot determine match vs. new | Choose: Same entity / Different entity |
| **Conflict warning** | New extraction contradicts world state | Acknowledge (non-blocking) |

## Component Structure

```
src/features/input/
├── SessionIngestionPage.tsx      ← container: submission form + status polling
├── DiffReviewPage.tsx            ← container: fetches diff, manages overall state
├── components/
│   ├── NarrativeSummaryHeader.tsx  ← AI-generated 1-3 sentence session summary
│   ├── DiffCategorySection.tsx     ← collapsible section per category (Actors, Spaces, etc.)
│   ├── DiffCard.tsx                ← base card: Accept / Edit / Delete actions
│   ├── NewEntityCard.tsx           ← full profile display for new entities
│   ├── DeltaCard.tsx               ← delta-only display for existing entities
│   ├── UncertainCard.tsx           ← UNCERTAIN resolution: Same entity / Different entity
│   ├── ConflictWarningCard.tsx     ← non-blocking conflict warning with acknowledge action
│   └── CommitButton.tsx            ← controlled button: disabled until all UNCERTAIN resolved
└── hooks/
    ├── useDiffState.ts             ← manages card-level user decisions
    └── useCommitPayload.ts         ← assembles commit payload from diff state
```

## Workflow: Building a New Card Type

### 1. Understand the diff payload shape

The diff payload from `GET /sessions/{id}/diff` contains:

```typescript
interface DiffPayload {
  narrativeSummary: string;     // D-005: shown at top before cards
  actors: DiffItem[];
  spaces: DiffItem[];
  events: DiffItem[];
  relations: DiffItem[];
}

type DiffItemKind = 'new' | 'delta' | 'uncertain' | 'conflict';

interface DiffItem {
  id: string;                   // temporary ID for this diff item
  kind: DiffItemKind;
  entityType: 'actor' | 'space' | 'event' | 'relation';
  // For 'new': full extracted profile
  extractedData: Record<string, unknown>;
  // For 'delta': only changed fields
  changedFields: Record<string, { previous: unknown; current: unknown }>;
  // For 'uncertain': the mention + the candidate existing entity
  mention: ExtractedMention | null;
  candidateEntity: WorldStateEntity | null;
  // For 'conflict': the contradiction
  conflictDescription: string | null;
  conflictId: string | null;
}
```

### 2. Manage card-level user decisions in state

Each card's user decision is tracked in a state map keyed by `DiffItem.id`:

```typescript
// src/features/input/hooks/useDiffState.ts
type CardDecision =
  | { action: 'accept' }
  | { action: 'edit'; data: Record<string, unknown> }
  | { action: 'delete' }
  | { action: 'uncertain_resolved'; resolution: 'match' | 'new'; matchedEntityId?: string }
  | { action: 'conflict_acknowledged' };

interface DiffState {
  decisions: Map<string, CardDecision>;
  setDecision: (itemId: string, decision: CardDecision) => void;
  unresolvedUncertainCount: number;
  unacknowledgedConflictCount: number;
}
```

The default state for a card is `accept`. Users only need to interact with cards they want to
change, delete, or resolve.

### 3. The UNCERTAIN card — mandatory resolution

`UncertainCard` must force the user to make a binary choice. There is no "skip" or "decide later"
option:

```tsx
// src/features/input/components/UncertainCard.tsx
export function UncertainCard({ item, onResolve }: Props) {
  return (
    <Card>
      <CardHeader>
        <Badge variant="warning">Requires Resolution</Badge>
        <h3>Is this the same entity?</h3>
      </CardHeader>
      <CardContent>
        {/* Show extracted mention side-by-side with candidate existing entity */}
        <MentionPreview mention={item.mention} />
        <CandidatePreview entity={item.candidateEntity} />
      </CardContent>
      <CardFooter>
        <Button
          onClick={() => onResolve(item.id, { action: 'uncertain_resolved', resolution: 'match',
            matchedEntityId: item.candidateEntity?.id })}
        >
          Same entity (link to existing)
        </Button>
        <Button
          variant="outline"
          onClick={() => onResolve(item.id, { action: 'uncertain_resolved', resolution: 'new' })}
        >
          Different entity (create new)
        </Button>
      </CardFooter>
    </Card>
  );
}
```

### 4. The Commit button — always controlled

The Commit button's `disabled` state is derived from `unresolvedUncertainCount`:

```tsx
// src/features/input/components/CommitButton.tsx
export function CommitButton({ unresolvedCount, onCommit, isPending }: Props) {
  const isDisabled = unresolvedCount > 0 || isPending;
  return (
    <div>
      {unresolvedCount > 0 && (
        <p>{unresolvedCount} item{unresolvedCount !== 1 ? 's' : ''} require resolution before commit</p>
      )}
      <Button onClick={onCommit} disabled={isDisabled} loading={isPending}>
        Commit to World State
      </Button>
    </div>
  );
}
```

This is the primary guard (D-042). The backend's `422 UNCERTAIN_ENTITIES_PRESENT` is defence in
depth — treat receiving it as a UI bug to fix.

### 5. Assembling the commit payload

`useCommitPayload` translates the `DiffState` decisions map into the commit payload shape:

```typescript
// src/features/input/hooks/useCommitPayload.ts
export function useCommitPayload(diff: DiffPayload, decisions: Map<string, CardDecision>): CommitPayload {
  const mapItems = (items: DiffItem[]) =>
    items
      .filter(item => item.kind !== 'uncertain' && item.kind !== 'conflict')
      .map(item => {
        const decision = decisions.get(item.id) ?? { action: 'accept' as const };
        return { id: item.id, action: decision.action, data: 'data' in decision ? decision.data : {} };
      });

  const resolvedEntities = [...decisions.entries()]
    .filter(([, d]) => d.action === 'uncertain_resolved')
    .map(([mentionId, d]) => ({
      mentionId,
      resolution: (d as any).resolution,
      matchedEntityId: (d as any).matchedEntityId ?? null,
    }));

  const acknowledgedConflicts = [...decisions.entries()]
    .filter(([, d]) => d.action === 'conflict_acknowledged')
    .map(([conflictId]) => ({ conflictId, accepted: true }));

  return {
    actors: mapItems(diff.actors),
    spaces: mapItems(diff.spaces),
    events: mapItems(diff.events),
    relations: mapItems(diff.relations),
    resolvedEntities,
    acknowledgedConflicts,
  };
}
```

**Important:** `acknowledgedConflicts` must only be non-empty when conflicts exist. Sending an
empty array `[]` when no conflicts were detected is valid — sending `[]` when conflicts were
detected triggers `422 CONFLICTS_NOT_ACKNOWLEDGED`.

### 6. Inline editing

When a user clicks "Edit" on a card, the card switches to an editable form. The edited data is
stored as `{ action: 'edit', data: {...editedFields} }` in the decisions map. On cancel, revert
to `{ action: 'accept' }`.

Only render the editable fields — do not render a full entity form. For `delta` cards, only the
`changedFields` are editable in v1. For `new` entity cards, all `extractedData` fields are editable.

## Rendering the Narrative Summary Header (D-005)

```tsx
<NarrativeSummaryHeader summary={diff.narrativeSummary} />
// Renders: 1-3 sentence summary before the category sections
// Visual purpose: immediate gestalt, surfaces fundamental misinterpretations
```

## Draft Recovery Flow

When the user loads Input Mode for a campaign with an existing `draft` session:

1. Check session list for a session with `status === 'draft'`.
2. If found: show a prompt "You have an unfinished review. Continue where you left off?"
3. On confirm: fetch `GET /sessions/{id}/diff` and render the review screen.
4. On dismiss: do nothing (the draft still exists — the user must explicitly discard it via the
   `DELETE /sessions/{id}` endpoint before submitting a new session).

Do not allow a new submission while a draft or processing session exists (D-054). Show a
clear error if the user tries: "Another session is pending review. Finish or discard it first."

## Accessibility Requirements

Every interactive element in the diff review screen must pass axe-core checks:

- UNCERTAIN cards: the two choices must be keyboard-navigable with visible focus indicators
- Commit button: when disabled, include `aria-disabled` and a descriptive `aria-label`
- Conflict warning cards: use `role="alert"` for the warning content
- Category sections: use `<section>` with descriptive `aria-labelledby` headings
- Inline edit forms: use proper `<label>` associations

Write `vitest-axe` assertions alongside every component test.

## Common Pitfalls

- **Deriving `disabled` on the Commit button from a local flag rather than the decisions map.**
  The disabled state must be computed from the actual unresolved UNCERTAIN count, not a separate
  boolean. If the state can drift, it will drift.

- **Sending an `add` action in the commit payload.** The `add` action is v2 only (D-053). Do not
  render an "Add entity" affordance anywhere in the diff review screen in v1.

- **Not collecting conflict acknowledgments separately.** Conflict acknowledgments are not part of
  the per-card accept/edit/delete decisions — they are in `acknowledged_conflicts`. If you merge
  them into the main decisions map, the payload builder will misformat the commit payload.

- **Rendering the full existing entity profile on a `delta` card.** Delta cards show only
  `changedFields` — what changed this session (D-006). Showing the full existing entity on a
  delta card buries the signal in noise.

- **Not handling the draft recovery flow.** If a user submits a new session while a draft exists,
  the backend returns `409`. The frontend must surface the recovery path proactively — show the
  existing draft before allowing a new submission.

- **Missing the `422 UNCERTAIN_ENTITIES_PRESENT` response in the commit mutation error handler.**
  This should be treated as a bug indicator, not a user-facing error. Log it, show a generic
  "Something went wrong" message, and do NOT silently re-enable the Commit button.

## References

- `apps/web/CLAUDE.md` §6 (diff review concepts), §7 (commit button rule, read-only rule), §10 (gotchas)
- `ARCHITECTURE.md` §7.6 (session ingestion API, commit payload shape)
- `DECISIONS.md` D-002, D-004, D-005, D-006, D-007, D-033, D-042, D-053, D-054
- `frontend-api-resource` skill (for session status polling and commit mutation hooks)
