# Module Name: Exploration Mode

## 1. Overview

Exploration Mode is the visual, browsable face of the campaign's accumulated knowledge. Four interconnected views — **Timeline**, **Entities** (actors), **Spaces** (locations), and **Relations** (graph) — let any campaign member navigate everything that has been committed through Input Mode. World state here is strictly **read-only** (D-010): every mutation flows through session ingestion, so every fact on screen is traceable to the session that produced it, and full per-entity version history allows seeing how anything evolved session by session.

The single write capability in this module is **annotations**: free-text, explicitly non-canonical commentary any member can attach to an entity, space, event, or relation.

## 2. Capabilities & Use Cases

- **Use Case / Action:** Browse the campaign Timeline — ✅ Implemented
- **Actor:** Any campaign member
- **Functional Description:** A chronological feed of events across all committed sessions, loaded 20 at a time with cursor-based (keyset) pagination via a "Load more" button (D-055). Filters by actor, space, and event type apply on submit and reset pagination. Each event card shows name, type badge, involved actors, space, and session number, and links to the event detail page.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/timeline` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/exploration/TimelineController.java`, `apps/api/src/main/java/com/bluesteel/application/service/worldstate/TimelineService.java`; UI: `apps/web/src/features/exploration/timeline/TimelinePage.tsx`, `EventCard.tsx`, `EventDetailPage.tsx`

---

- **Use Case / Action:** Browse and inspect Entities (actors) — ✅ Implemented
- **Actor:** Any campaign member
- **Functional Description:** An offset-paginated list of all actors (PCs, NPCs, creatures) with version badges. The profile page shows the current state (latest full snapshot), the append-only version history with per-session field deltas, the actor's relations, related entities, linked events, and session appearances — each a navigable cross-link.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/actors`, `GET .../actors/{aid}`, `GET .../actors/{aid}/links` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/exploration/WorldStateExplorationController.java`, `apps/api/src/main/java/com/bluesteel/application/service/worldstate/WorldStateExplorationService.java`; UI: `apps/web/src/features/exploration/entities/EntitiesPage.tsx`, `EntityProfilePage.tsx`, shared `components/EntityListView.tsx`, `EntityProfileView.tsx`, `EntityLinks.tsx`, and `apps/web/src/components/domain/EntityVersionHistory.tsx`

---

- **Use Case / Action:** Browse and inspect Spaces (locations) — ✅ Implemented
- **Actor:** Any campaign member
- **Functional Description:** Identical pattern to Entities, for locations: paginated list plus profile with current snapshot, version history, relations, and the events that took place there.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/spaces`, `GET .../spaces/{sid}`, `GET .../spaces/{sid}/links` — `WorldStateExplorationController.java`; UI: `apps/web/src/features/exploration/spaces/SpacesPage.tsx`, `SpaceProfilePage.tsx` (reusing the shared list/profile components)

---

- **Use Case / Action:** Inspect an Event — ✅ Implemented
- **Actor:** Any campaign member
- **Functional Description:** Event detail shows the latest snapshot, version history, involved actors and space (linked), and its annotation thread. Events are reached from the Timeline or from entity/space profiles.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/events`, `GET .../events/{eid}` — `WorldStateExplorationController.java`; UI: `apps/web/src/features/exploration/timeline/EventDetailPage.tsx`

---

- **Use Case / Action:** Explore the Relations graph — ✅ Implemented
- **Actor:** Any campaign member
- **Functional Description:** An interactive directed graph (React Flow) of actors and spaces as nodes with relations as edges. Nodes are draggable for readability (view-only — positions are not persisted); clicking an edge highlights its endpoints and opens a detail panel. A collapsible, keyboard-accessible relations list mirrors the graph for accessibility; selecting a row highlights it on the canvas. Relation detail includes its version history and annotations.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/relations`, `GET .../relations/{rid}` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/exploration/RelationsController.java`, `apps/api/src/main/java/com/bluesteel/application/service/worldstate/RelationExplorationService.java`; UI: `apps/web/src/features/exploration/relations/RelationsPage.tsx`, `RelationNode.tsx`, `RelationEdge.tsx`, `RelationsList.tsx`, `RelationDetailPage.tsx`, layout util `graphTransform.ts`

---

- **Use Case / Action:** Annotate world-state items — ✅ Implemented
- **Actor:** Any campaign member (create); annotation author or GM (delete)
- **Functional Description:** Members attach free-text notes to any actor, space, event, or relation — table commentary clearly marked as non-canonical (it never feeds the LLM pipeline). Annotations are immutable after posting; the author can delete their own, and the GM can delete any. Threads render inside the detail/profile pages.
- **Technical Reference / Source Files:** `POST /api/v1/campaigns/{id}/annotations`, `GET .../annotations?entityType=&entityId=`, `DELETE .../annotations/{aid}` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/annotation/AnnotationController.java`, `apps/api/src/main/java/com/bluesteel/application/service/annotation/AnnotationService.java`; UI: `apps/web/src/components/domain/AnnotationThread.tsx`

---

- **Use Case / Action:** Propose a change to a world-state item — 🚧 Planned (v2, D-012/D-016)
- **Actor:** Any campaign member
- **Functional Description:** Every profile shows a "Propose a change" button, but it is a disabled stub with a "Coming in a future update" tooltip. The proposal/voting data model already exists in the database; the approval workflow ships in v2. See [deferred_and_planned.md](deferred_and_planned.md).
- **Technical Reference / Source Files:** `apps/web/src/components/domain/ProposeChangeButton.tsx` (disabled stub); schema `apps/api/src/main/resources/db/changelog/0018_create_proposals.xml`, `0019_create_proposal_votes.xml`

## 3. Core User Journeys (Workflows)

**Journey: "What happened to this character?" (cross-link navigation)**
1. Member opens Exploration (`/campaigns/:id/explore`, defaulting to Timeline) and switches to the Entities tab.
2. Pages to the actor and opens their profile: current snapshot on top, version history showing exactly which session changed which fields.
3. Follows a relation link to see who the actor is connected to, or a session-appearance link to read the original session, or a linked event to its detail page — every fact is one click from its source.
4. Posts an annotation ("Remember: he still owes us 50 gold") visible to the whole table as commentary.

**Journey: Untangling the web (Relations graph)**
1. Member opens the Relations tab; the graph lays out all actors/spaces and their connections.
2. Drags crowded nodes apart, clicks the suspicious edge between two NPCs → the endpoints highlight and the detail panel shows the relation's nature and history.
3. Uses the accessible list view to scan all relations textually, selecting rows to locate them on the canvas.

**Journey: Catching up after a missed session**
1. A player who missed game night opens Timeline and filters by their character.
2. Reads the new events in order, clicking through to event details for the full picture.
3. Switches to Query Mode for anything still unclear ("Why did we burn the tavern down?").
