# PRD — Narrative Memory System for Tabletop RPG Campaigns

---

## 1. Problem Statement

Tabletop RPG campaigns are long-running, complex narratives. Information accumulates across sessions: characters evolve, alliances shift, locations are revealed, secrets come to light. No existing tool is purpose-built to capture and make this knowledge queryable in a structured, intelligent way.

**The result:** players forget key events, GMs struggle to maintain consistency, and narrative continuity degrades over time — especially in campaigns spanning months or years.

---

## 2. Vision

An **AI-assisted external brain** for tabletop campaigns. It remembers everything, organizes the story automatically, and answers questions as if it lived through every session alongside the group.

The system transforms raw, unstructured session summaries into structured world knowledge — and makes that knowledge explorable through natural language and visual interfaces.

---

## 3. Target Users

The role model has two distinct dimensions: platform-level and campaign-level.

**Platform level:**

| Role | Scope | Capabilities |
|---|---|---|
| **Admin** | Singleton super-user | Creates campaigns, assigns GMs, full platform access |

**Campaign level:**

| Role | Permissions | Primary Need |
|---|---|---|
| **Game Master (GM)** | Full write access within campaign | Maintain narrative consistency; track complex NPC webs and world state |
| **Editor** | Session upload + diff review | Trusted player delegated session management by the GM |
| **Player** | Read-only (query + exploration) | Recall past events; understand relationships and what happened in missed sessions |

The platform assumes a minimum group size of 3 (1 GM + 2 players). Duo campaigns are out of scope.

**Onboarding flow:**
```
Admin creates campaign
  → Admin assigns a user as GM
    → GM invites players and promotes editors
```

Admin hands off at the GM assignment step. Campaign membership below that is the GM's responsibility.

Role permissions are enforced from v1. The proposal/approval workflow (players requesting world state changes) is designed into the data model in v1 but ships in v2.

---

## 4. Core Concepts (Domain Model)

| Term | Definition |
|---|---|
| **Campaign** | The overarching story spanning multiple sessions |
| **Session** | A single play event; produces a summary that feeds the system |
| **Session Summary** | Raw narrative text written by GM or players after a session |
| **World State** | Accumulated, structured knowledge of the campaign at any point in time |
| **Actor** | Any entity with agency: Player Characters (PCs), NPCs, creatures |
| **Space** | A location in the world: city, dungeon, region, etc. |
| **Event** | A significant narrative occurrence: decision, combat, revelation |
| **Relation** | A connection between actors or between actors and spaces: alliance, enmity, hierarchy |
| **Timeline** | Ordered sequence of events across sessions |
| **Narrative Block** | A single session's input before it's processed into world state |
| **Knowledge Extraction** | The AI process of identifying actors, events, relations, and changes from a summary |
| **Query** | A natural language question asked against the current world state |

---

## 5. How the System Thinks

Each session is processed as a pipeline:

```
Session Summary (raw text)
  → Narrative Block
  → Knowledge Extraction (AI)
      ↳ Actors identified / updated
      ↳ Events logged
      ↳ Relations created / modified
      ↳ Spaces discovered / changed
  → World State updated
  → Available for Query and Exploration
```

The World State is cumulative. Each session builds on the previous, never replacing it — only extending or modifying it.

---

## 6. Interaction Modes

The system exposes three distinct interaction modes:

### 6.1 Input Mode
**Purpose:** Feed the system new session information.

- User uploads or pastes a session summary (free text)
- System processes it through Knowledge Extraction
- User reviews extracted knowledge via a **structured diff** before any commit
- User confirms, corrects, or annotates before committing to world state
- Designed to be fast and low-friction — not a data entry form

**Review UX — Structured Diff:**

The review screen is organized by extraction category:

```
🧑 Actors       [ 2 new · 3 updated ]
📍 Spaces       [ 1 new · 0 updated ]
⚡ Events        [ 5 new ]
🔗 Relations    [ 1 new · 2 updated ]
```

Each item is an editable card. Per item, the user can:
- **Accept** as-is (one click)
- **Edit inline** — rename, reclassify, adjust description
- **Delete** — remove false positives

A single **Commit** action finalizes all changes. Nothing enters world state before it.

> **v1 scope note:** The ability to manually introduce entities the AI missed ("Add" action) is deferred to v2 (D-053). In v1, users who need to add a missed entity should submit a corrected session summary or use the proposal system once it ships in v2.

**Narrative summary header:**

Before the diff, the system displays a 1–3 sentence AI-generated summary of the session's narrative meaning (*"Session 7 introduced a new faction, the Conclave, and shifted Mira's allegiance away from the party. Two new locations were discovered."*). This gives users the gestalt before they engage with individual items, and makes fundamental misinterpretations immediately visible.

**Delta-only display for existing entities:**

Entities already present in the world state show **only what changed this session** — new fields, modified fields, new relations. Unchanged recognized entities do not appear in the diff. New entities show their full extracted profile.

**Entity resolution:**

Before generating the diff, the system must reliably determine whether an extracted entity maps to an existing world state entity or is genuinely new. This is a non-trivial AI step (alias matching, name variation, contextual identity) — architectural implications deferred to ARCHITECTURE.md.

**Oversized input:** If a submitted summary exceeds the system token limit, the submission is rejected before any processing occurs. The user is shown the configured limit and a suggestion to split the summary across multiple session records.

### 6.2 Query Mode
**Purpose:** Ask natural language questions about the campaign.

- User types a free-form question: *"What does Seraphine know about the cursed artifact?"*, *"Who was present at the battle of Thornwall?"*
- System answers based on current world state, citing sessions as sources
- Responses are grounded — the system does not invent; it retrieves and synthesizes
- Citations link back to specific sessions for traceability

### 6.3 Exploration Mode
**Purpose:** Browse and navigate the structured world state visually.

Exploration mode is a set of interconnected views, not a single screen:

| View | What it shows |
|---|---|
| **Timeline** | Ordered sequence of events across all sessions; filterable by actor, space, or event type |
| **Entities** | All known actors with profiles: role, status, affiliations, session appearances |
| **Spaces** | All known locations with descriptions, connected actors, and events that occurred there |
| **Relations** | The web of connections between actors and between actors and spaces |

**Read-only for world state.** No direct edits to entities, spaces, or relations from this mode. All world state mutation flows through Input Mode.

**Annotations:**
- Any campaign member can attach a free-text annotation to any actor, space, relation, or event
- Annotations work as a comment section — non-canonical and clearly marked as player commentary, not world state
- Visible to all campaign members

**Propose a change:**
- A *"Propose a change"* affordance is present on every entity, space, and relation in v1
- The affordance is visible but the approval pipeline is inactive until v2

---

## 7. Functional Scope (MVP)

### In scope (v1)
- Multiple campaigns, created and managed by admin
- Admin assigns GM per campaign
- User authentication and role management (`admin`, `gm`, `editor`, `player`)
- Session summary ingestion — GM and editor roles only
- AI-driven knowledge extraction (actors, events, relations, spaces)
- Structured diff review and commit — GM and editor roles
- World state storage and versioning per session
- Natural language query against world state — all roles
- Exploration views: timeline, entities, spaces, relations — all roles
- Proposal system **data model** (entities, relations, approval state) — built, not exposed in UI

### Out of scope (v1) → v2
- Player proposal and approval workflow (UI + approval logic)
- Proposal expiry / TTL enforcement
- Conflict resolution between concurrent proposals
- Q&A log — campaign history of queries and answers, with a history panel inside Query Mode (D-058)

### Out of scope entirely (post-v2)
- Real-time collaborative editing
- Audio/image ingestion
- Public sharing or export
- Mobile-native application

---

## 8. Non-Functional Requirements

| Attribute | Requirement |
|---|---|
| **Latency** | Knowledge extraction: target < 10s for summaries up to 2,000 tokens (~1,500 words). Query responses: target < 5s. Summaries approaching the token limit may exceed the 10s target; this is acceptable. |
| **Accuracy** | Extractions must be reviewable and correctable — the system never commits without user confirmation |
| **Traceability** | Every piece of world state must be traceable to the session that produced it |
| **Consistency** | When a session introduces facts that contradict existing world state, the system must surface the contradiction to the user before commit. The user retains authority to accept the contradiction (e.g., narrative retcon). The system never silently admits contradictions. |
| **Simplicity** | The interface must be usable by non-technical users with no onboarding |

---

## 9. Open Questions

| # | Question | Priority |
|---|---|---|
| ~~OQ-1~~ | ~~What is the correction/annotation UX in Input Mode?~~ | ✅ Resolved — see §6.1 |
| ~~OQ-2~~ | ~~How are conflicts handled when a new session contradicts existing world state?~~ | ✅ Resolved — see DECISIONS.md D-033 |
| ~~OQ-3~~ | ~~What is the granularity of world state versioning — per session commit, or finer?~~ | ✅ Resolved — see DECISIONS.md D-035 |
| ~~OQ-4~~ | ~~Single-user vs multi-user per campaign in v1?~~ | ✅ Resolved — see §3 and §7 |
| ~~OQ-5~~ | ~~Is Exploration Mode read-only, or can users annotate / manually add information?~~ | ✅ Resolved — see §6.3 |
| ~~OQ-6~~ | ~~Should submitted queries and their answers be persisted and viewable as a Q&A log?~~ | ✅ Resolved — deferred to v2, see DECISIONS.md D-058 |

---

## 10. Success Criteria

The system is successful if:

1. A GM can upload a session summary and, within one review cycle, have it correctly integrated into the world state
2. A player can ask *"what happened to [character] in the last three sessions?"* and receive an accurate, sourced answer
3. The exploration views feel like a living record of the campaign — not a data dump
4. The system never silently invents information; every answer is grounded in what was submitted

---

