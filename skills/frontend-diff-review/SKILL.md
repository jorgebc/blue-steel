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

The diff payload from `GET /sessions/{id}/diff` matches the canonical schema in ARCHITECTURE.md §7.6.
TypeScript types in `src/types/sessions.ts` must mirror this exactly.

```typescript
// src/types/sessions.ts — mirrors ARCHITECTURE.md §7.6 exactly

export type CardType = 'EXISTING' | 'NEW' | 'UNCERTAIN';

// Base shape shared by EXISTING and NEW cards
export interface ExistingDiffCard {
  card_id: string;        // stable identifier; used in CommitPayload.card_decisions
  card_type: 'EXISTING';
  entity_id: string;      // world state entity this card refers to
  entity_type: 'actor' | 'space' | 'event' | 'relation';
  name: string;
  changed_fields: Record<string, unknown>;  // delta only (D-006)
}

export interface NewDiffCard {
  card_id: string;
  card_type: 'NEW';
  entity_type: 'actor' | 'space' | 'event' | 'relation';
  name: string;
  full_profile: Record<string, unknown>;    // complete extracted profile (D-007)
}

// UNCERTAIN: AI cannot determine match vs. new — user must resolve
export interface UncertainDiffCard {
  card_id: string;
  card_type: 'UNCERTAIN';
  entity_type: 'actor' | 'space' | 'event' | 'relation';
  extracted_mention: string;          // raw text mention from the summary
  candidate_entity_id: string;        // best candidate match in world state
  candidate_entity_name: string;      // display name of candidate
}

export type DiffCard = ExistingDiffCard | NewDiffCard | UncertainDiffCard;

// ConflictCard — non-blocking; user must acknowledge before commit (D-033)
export interface ConflictCard {
  conflict_id: string;        // referenced in CommitPayload.acknowledged_conflicts
  entity_id: string;
  entity_type: 'actor' | 'space' | 'event' | 'relation';
  description: string;
  extracted_fact: string;
  existing_fact: string;
}

export interface DiffPayload {
  narrative_summary_header: string;   // D-005: AI-generated 1-3 sentence summary
  actors: DiffCard[];
  spaces: DiffCard[];
  events: DiffCard[];
  relations: DiffCard[];
  detected_conflicts: ConflictCard[];
}
```

### 2. Manage card-level user decisions in state

Each card's user decision is tracked in a state map keyed by `DiffCard.card_id`. Use a proper
discriminated union — never use `as any` to access variant-specific fields.

```typescript
// src/features/input/hooks/useDiffState.ts
export type CardDecision =
  | { action: 'accept' }
  | { action: 'edit'; edited_fields: Record<string, unknown> }
  | { action: 'delete' };

export type UncertainResolution =
  | { card_id: string; resolution: 'MATCH'; matched_entity_id: string }
  | { card_id: string; resolution: 'NEW'; matched_entity_id: null };

// Type guards — use these to narrow the union safely
export function isEdit(d: CardDecision): d is Extract<CardDecision, { action: 'edit' }> {
  return d.action === 'edit';
}

export interface DiffState {
  decisions: Map<string, CardDecision>;           // keyed by card_id
  uncertainResolutions: Map<string, UncertainResolution>;  // keyed by card_id
  acknowledgedConflicts: Set<string>;             // conflict_ids
  setDecision: (cardId: string, decision: CardDecision) => void;
  resolveUncertain: (resolution: UncertainResolution) => void;
  acknowledgeConflict: (conflictId: string) => void;
  unresolvedUncertainCount: number;
  unacknowledgedConflictCount: number;
}
```

The default state for a non-UNCERTAIN card is `accept`. Users only need to interact with cards
they want to change, delete, or resolve.

### 3. The UNCERTAIN card — mandatory resolution

`UncertainCard` must force the user to make a binary choice. There is no "skip" or "decide later"
option:

```tsx
// src/features/input/components/UncertainCard.tsx
interface Props {
  card: UncertainDiffCard;
  onResolve: (resolution: UncertainResolution) => void;
}

export function UncertainCard({ card, onResolve }: Props) {
  return (
    <Card>
      <CardHeader>
        <Badge variant="warning">Requires Resolution</Badge>
        <h3>Is this the same entity?</h3>
      </CardHeader>
      <CardContent>
        <p>Extracted: <strong>{card.extracted_mention}</strong></p>
        <p>Possible match: <strong>{card.candidate_entity_name}</strong></p>
      </CardContent>
      <CardFooter>
        <Button
          onClick={() => onResolve({
            card_id: card.card_id,
            resolution: 'MATCH',
            matched_entity_id: card.candidate_entity_id,
          })}
        >
          Same entity (link to existing)
        </Button>
        <Button
          variant="outline"
          onClick={() => onResolve({
            card_id: card.card_id,
            resolution: 'NEW',
            matched_entity_id: null,
          })}
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

`useCommitPayload` translates `DiffState` into the canonical `CommitPayload` shape from ARCHITECTURE.md §7.6.
Use the type guards defined in §2 — no `as any` casts.

```typescript
// src/types/sessions.ts — CommitPayload (mirrors ARCHITECTURE.md §7.6)
export interface CardDecisionPayload {
  card_id: string;
  action: 'accept' | 'edit' | 'delete';
  edited_fields?: Record<string, unknown>;  // required + non-empty when action = 'edit'
}

export interface UncertainResolutionPayload {
  card_id: string;
  resolution: 'MATCH' | 'NEW';
  matched_entity_id: string | null;  // non-null when resolution = MATCH
}

export interface CommitPayload {
  card_decisions: CardDecisionPayload[];
  uncertain_resolutions: UncertainResolutionPayload[];
  acknowledged_conflicts: Array<{ conflict_id: string }>;
}

// src/features/input/hooks/useCommitPayload.ts
export function buildCommitPayload(diff: DiffPayload, state: DiffState): CommitPayload {
  // card_decisions: every non-UNCERTAIN card must have an explicit entry
  const allNonUncertainCards = [
    ...diff.actors, ...diff.spaces, ...diff.events, ...diff.relations
  ].filter((c): c is ExistingDiffCard | NewDiffCard => c.card_type !== 'UNCERTAIN');

  const card_decisions: CardDecisionPayload[] = allNonUncertainCards.map(card => {
    const decision = state.decisions.get(card.card_id) ?? { action: 'accept' as const };
    return {
      card_id: card.card_id,
      action: decision.action,
      ...(isEdit(decision) ? { edited_fields: decision.edited_fields } : {}),
    };
  });

  const uncertain_resolutions: UncertainResolutionPayload[] =
    [...state.uncertainResolutions.values()].map(r => ({
      card_id: r.card_id,
      resolution: r.resolution,
      matched_entity_id: r.resolution === 'MATCH' ? r.matched_entity_id : null,
    }));

  const acknowledged_conflicts = [...state.acknowledgedConflicts].map(id => ({
    conflict_id: id,
  }));

  return { card_decisions, uncertain_resolutions, acknowledged_conflicts };
}
```

**Important:** `acknowledged_conflicts` must be non-empty when conflicts exist. An empty array
`[]` is valid only when the diff had no conflicts. Sending `[]` when conflicts were detected
triggers `422 CONFLICTS_NOT_ACKNOWLEDGED`.

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
