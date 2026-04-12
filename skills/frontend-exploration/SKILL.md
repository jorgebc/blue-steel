---
name: frontend-exploration
description: >
  Use this skill whenever you are building or modifying any part of Exploration Mode in
  `apps/web/src/features/exploration/`. Triggers include: "Timeline view", "Entities view",
  "Spaces view", "Relations graph", "React Flow", "entity profile", "explore", "world state
  browsing", "annotations", "propose a change affordance", "keyset pagination", "timeline
  feed", or any task inside `features/exploration/`. This skill covers the four sub-views, their
  distinct pagination patterns, the annotations feature, the inactive proposal affordance, and
  the Relations graph using React Flow v12.
---

# Frontend — Exploration Mode

Exploration Mode is the read-only face of the world state. It comprises four interconnected views.
No world state mutation is permitted from any view — the only write actions are posting annotations
and (in v2) submitting proposals. Each view has a distinct pagination strategy that must be
implemented correctly. The Relations view uses React Flow v12 for graph visualization.

## Context

**Key decisions:**
- D-009: Four views — Timeline, Entities, Spaces, Relations
- D-010: Exploration Mode is read-only for world state (no direct entity edits)
- D-011: Annotations are a first-class non-canonical feature (any member can post)
- D-012: "Propose a change" affordance is visible but inactive in v1
- D-055: Offset pagination for entity lists; keyset pagination for Timeline

**Directory structure:**

```
src/features/exploration/
├── ExplorationLayout.tsx      ← shared layout: navigation between 4 views
├── timeline/
│   ├── TimelinePage.tsx       ← keyset-paginated event feed
│   └── EventCard.tsx          ← single event display
├── entities/
│   ├── EntitiesPage.tsx       ← offset-paginated actor list
│   ├── EntityProfilePage.tsx  ← full actor profile with version history
│   └── AnnotationThread.tsx   ← annotation section (shared via domain/components)
├── spaces/
│   ├── SpacesPage.tsx         ← offset-paginated space list
│   └── SpaceProfilePage.tsx   ← full space profile with version history
└── relations/
    ├── RelationsPage.tsx      ← React Flow graph container
    ├── RelationNode.tsx       ← custom node component
    └── RelationEdge.tsx       ← custom edge component
```

`AnnotationThread.tsx` is used in both entity profiles and space profiles — place it in
`src/components/domain/` since it is shared across two features.

## View 1 — Timeline

The Timeline is an ordered event feed across all sessions. It uses **keyset pagination** (D-055).

**API:** `GET /api/v1/campaigns/{id}/timeline`

**Keyset pagination pattern:**

Keyset pagination uses a cursor rather than an offset. The backend returns a `nextCursor` in
`meta`. The client uses it to fetch the next page. There is no concept of "page 3" — only
"the page after this cursor."

```typescript
// src/types/timeline.ts
export interface TimelineEvent {
  id: string;
  description: string;
  sessionId: string;
  sequenceNumber: number;
  eventType: string;
  involvedActors: string[];
  space: string | null;
  createdAt: string;
}

export interface TimelinePage {
  events: TimelineEvent[];
  nextCursor: string | null;  // null means no more pages
}
```

```typescript
// src/api/timeline.ts
export const timelineKeys = {
  all: (campaignId: string) => ['campaigns', campaignId, 'timeline'] as const,
  page: (campaignId: string, cursor?: string) =>
    [...timelineKeys.all(campaignId), { cursor }] as const,
};

// TanStack Query infinite query for keyset pagination
export function useTimeline(campaignId: string) {
  return useInfiniteQuery({
    queryKey: timelineKeys.all(campaignId),
    queryFn: ({ pageParam: cursor }) =>
      fetchTimelinePage(campaignId, cursor),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
  });
}
```

Render with `useInfiniteQuery` and a "Load more" button (or intersection observer for infinite
scroll). Do not use `useQuery` with a page number — that is the offset pattern and will not work
with a cursor-based API.

## View 2 — Entities (Actors)

Actor list uses **offset pagination** (D-055).

**API:** `GET /api/v1/campaigns/{id}/actors?page=0&size=20`

```typescript
// src/api/actors.ts
export const actorKeys = {
  all: (campaignId: string) => ['campaigns', campaignId, 'actors'] as const,
  list: (campaignId: string, page: number) =>
    [...actorKeys.all(campaignId), 'list', page] as const,
  detail: (campaignId: string, actorId: string) =>
    [...actorKeys.all(campaignId), actorId] as const,
};

export function useActors(campaignId: string, page = 0) {
  return useQuery({
    queryKey: actorKeys.list(campaignId, page),
    queryFn: () => fetchActors(campaignId, page),
  });
}
```

**Entity profile page** shows:
- Current state (from `fullSnapshot` of the latest version)
- Version history: a collapsible timeline of changes across sessions, each referencing the session
  that produced the change
- Annotation thread (non-canonical comments, D-011)
- "Propose a change" affordance — visible but inactive in v1 (D-012, see below)

## View 3 — Spaces

Identical pattern to Entities but for space entities. Offset pagination.

**API:** `GET /api/v1/campaigns/{id}/spaces`

Spaces and Entities both render entity profiles with version history and annotation threads.
The `AnnotationThread` component and `EntityVersionHistory` component should be shared in
`src/components/domain/`.

## View 4 — Relations Graph (React Flow)

The Relations view renders all Actor↔Actor and Actor↔Space connections as an interactive graph
using React Flow v12.

**API:** `GET /api/v1/campaigns/{id}/relations` (all relations for the campaign)

This is the most complex single component in the frontend — keep its dependencies isolated within
`features/exploration/relations/`.

### React Flow setup

```typescript
// src/features/exploration/relations/RelationsPage.tsx
import ReactFlow, { Node, Edge, Background, Controls, MiniMap } from 'reactflow';
import 'reactflow/dist/style.css';

// Transform API relations into React Flow nodes and edges
function transformToGraph(actors: Actor[], relations: Relation[]): { nodes: Node[], edges: Edge[] } {
  const nodes: Node[] = actors.map(actor => ({
    id: actor.id,
    type: 'actorNode',
    position: { x: 0, y: 0 },  // use a layout algorithm — see note below
    data: { label: actor.name, status: actor.currentStatus, actorId: actor.id },
  }));

  const edges: Edge[] = relations.map(rel => ({
    id: rel.id,
    source: rel.sourceId,
    target: rel.targetId,
    type: 'default',
    label: rel.type,         // e.g., "alliance", "enmity"
    animated: rel.isActive,
  }));

  return { nodes, edges };
}
```

**Layout:** React Flow does not auto-layout nodes. Use a layout algorithm to position nodes
meaningfully. Options (pick one consistently):
- `dagre` (directed acyclic graph layout) — good for hierarchical relations
- `d3-force` — good for organic cluster layouts
- Manual positioning seeded from entity creation order

**Custom node component:**

```tsx
// src/features/exploration/relations/RelationNode.tsx
import { Handle, Position, type NodeProps } from 'reactflow';

export function ActorNode({ data }: NodeProps) {
  return (
    <div className="actor-node">
      <Handle type="target" position={Position.Left} />
      <span>{data.label}</span>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
```

Register custom node types in the `nodeTypes` prop of `ReactFlow`:

```tsx
const nodeTypes = useMemo(() => ({ actorNode: ActorNode }), []);
<ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} ... />
```

**Important:** `nodeTypes` must be defined outside the component or memoised with `useMemo`.
A new object reference on every render causes React Flow to unmount and remount all nodes.

**No world state edits from the graph.** Clicking a node navigates to the entity profile page.
Double-click, drag-to-connect, and other editing interactions must be disabled.

## Annotations (D-011)

Any campaign member can post an annotation on any entity, space, relation, or event. Annotations
are non-canonical commentary, clearly visually distinguished from world state content.

```tsx
// src/components/domain/AnnotationThread.tsx
function AnnotationThread({ entityType, entityId, campaignId }: Props) {
  const { data: annotations } = useAnnotations(campaignId, entityType, entityId);
  const { mutate: postAnnotation } = usePostAnnotation(campaignId);

  return (
    <section aria-label="Annotations">
      <h3>Notes (non-canonical)</h3>
      {annotations?.map(a => (
        <AnnotationCard key={a.id} annotation={a} />
      ))}
      <AnnotationInput onSubmit={(text) => postAnnotation({ entityType, entityId, content: text })} />
    </section>
  );
}
```

**Annotation rules (D-011):**
- Annotations are immutable after creation — no edit button
- Any member can delete their own annotation; GM can delete any annotation
- No `updated_at` on annotation cards
- Render a visual separator (colour, label, indentation) to clearly distinguish annotations from
  canonical entity data

## "Propose a Change" Affordance (D-012)

Every entity, space, and relation profile in v1 must render a "Propose a change" button. In v1,
the approval pipeline is not active — the affordance is a placeholder.

```tsx
function ProposeChangeButton() {
  return (
    <Tooltip content="Proposal system coming in a future update">
      <Button variant="ghost" disabled aria-disabled="true">
        Propose a change
      </Button>
    </Tooltip>
  );
}
```

Do not wire the button to any endpoint. Do not implement any approval form, modal, or workflow.
The data model exists server-side (D-016), but the UI affordance is purely cosmetic in v1.

## Role-Gating in Exploration Mode

Campaign role is resolved from the campaign membership API response — not from the JWT (D-043).

```typescript
// src/store/campaignStore.ts
interface CampaignContext {
  campaignId: string;
  currentUserRole: 'gm' | 'editor' | 'player' | null;
}
```

In Exploration Mode:
- All roles can view Timeline, Entities, Spaces, Relations, and read annotations.
- All roles can post annotations (D-011).
- The "Propose a change" affordance renders for all roles (stub in v1).
- The `admin` role has full platform access but is not a campaign role — admin can view all
  campaigns.

## Accessibility Requirements

All Exploration Mode views require axe-core checks in tests:

- `<section>` elements with `aria-labelledby` for each view
- Entity profile headings: `<h1>` for entity name, `<h2>` for subsections
- Annotation thread: `<section aria-label="Annotations">`
- React Flow graph: include a text-based alternative (e.g., an accessible list of relations) for
  screen readers — the SVG graph is not inherently accessible

## Common Pitfalls

- **Using offset pagination for the Timeline.** Timeline uses keyset (cursor) pagination. Using
  `page=0&size=20` with offset will not work with the backend's keyset-cursor API (D-055).

- **Editing `src/components/ui/` to customise the graph or entity cards.** `components/ui/` is
  owned by shadcn/ui. Wrap or compose in `components/domain/` instead.

- **Mutating world state from Exploration Mode.** No form, input, or button in Exploration Mode
  should call a world-state-modifying endpoint. The only write operations are annotations and
  (v1 stub) proposals. If you find an entity edit endpoint being called from an exploration view,
  that is a bug (D-010).

- **Deriving campaign role from the JWT.** The JWT carries only `user_id` and `is_admin`. Campaign
  role is in the campaign membership API response and must be stored in the campaign Zustand store
  (D-043).

- **Recreating `nodeTypes` on every render in React Flow.** This unmounts and remounts all graph
  nodes, causing flicker and lost positions. Define `nodeTypes` outside the component or use
  `useMemo`.

- **Rendering an edit button on annotations.** Annotations are immutable after creation. A delete
  button is valid (with the correct role check); an edit button is not.

## References

- `apps/web/CLAUDE.md` §6 (exploration concepts), §7 (read-only rule, annotation immutability), §10 (gotchas)
- `ARCHITECTURE.md` §7.9 (exploration mode endpoint catalogue)
- `DECISIONS.md` D-009, D-010, D-011, D-012, D-055
- `frontend-api-resource` skill (for TanStack Query infinite query patterns)
