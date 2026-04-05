# DECISIONS — Blue Steel

Decision log for Blue Steel, an AI-assisted narrative memory system for tabletop RPG campaigns.

**Format:** Each entry records what was decided, why, and what alternatives were considered.  
**Convention:** Entries are append-only. Superseded decisions are marked, not deleted.

---

## Decision Index

| ID | Title | Status | Phase |
|---|---|---|---|
| D-001 | World state is cumulative | ✅ Active | Definition |
| D-002 | User confirmation required before world state commit | ✅ Active | Definition |
| D-003 | Query responses are grounded with session citations | ✅ Active | Definition |
| D-004 | Input Mode review model: structured diff | ✅ Active | Definition |
| D-005 | Narrative summary header in diff review | ✅ Active | Definition |
| D-006 | Delta-only display for existing entities in diff | ✅ Active | Definition |
| D-007 | New entities show full extracted profile in diff | ✅ Active | Definition |
| D-008 | Three interaction modes: Input, Query, Exploration | ✅ Active | Definition |
| D-009 | Exploration Mode views: timeline, entities, spaces, relations | ✅ Active | Definition |
| D-010 | Exploration Mode is read-only for world state | ✅ Active | Definition |
| D-011 | Annotations are a first-class non-canonical feature | ✅ Active | Definition |
| D-012 | "Propose a change" affordance present in v1, pipeline in v2 | ✅ Active | Definition |
| D-013 | v1 ships auth + roles (updated by D-025) | ⚠️ Superseded | Definition |
| D-014 | Session upload and diff review are GM-only by default | ⚠️ Superseded | Definition |
| D-015 | Editor role: player promoted to session upload rights by GM | ✅ Active | Definition |
| D-016 | Proposal/approval system designed in data model v1, ships v2 | ✅ Active | Definition |
| D-017 | Proposal approval rule: ≥1 player co-sign → GM decides | ✅ Active | Definition |
| D-018 | GM veto is unilateral | ✅ Active | Definition |
| D-019 | Abandoned proposals expire (TTL defined in v2) | ✅ Active | Definition |
| D-020 | Platform minimum group size: 3 | ✅ Active | Definition |
| D-021 | All domain entities carry owner_id from day one | ✅ Active | Definition |
| D-022 | Monorepo structure: apps/web + apps/api | ✅ Active | Definition |
| D-023 | License: MIT | ✅ Active | Definition |
| D-024 | Multiple campaigns, admin-only creation | ✅ Active | Definition |
| D-025 | Admin is a singleton platform-level super-user | ✅ Active | Definition |
| D-026 | Onboarding flow: admin creates campaign and assigns GM | ✅ Active | Definition |

---

## Entries

---

### D-001 — World state is cumulative

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The World State is never replaced by a new session. Each session exclusively extends or modifies the existing state. Historical state is preserved.

**Reason:**  
Narrative continuity requires that the past remain intact. Users must be able to query what was true at any point in the campaign, not just the current state. Replacing state would destroy traceability.

**Alternatives considered:**  
- Snapshot-per-session (full copy each time) — rejected as storage-inefficient and query-complex
- Mutable overwrite — rejected as it destroys history

---

### D-002 — User confirmation required before world state commit

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
No extracted knowledge is committed to world state without explicit user confirmation. The system never auto-commits.

**Reason:**  
AI extraction is fallible. Incorrect data silently entering the world state would degrade the system's reliability and erode user trust. The review step is the trust boundary.

**Alternatives considered:**  
- Auto-commit with undo — rejected because undo after-the-fact is worse UX than review before-the-fact, especially when entities have downstream relations

---

### D-003 — Query responses are grounded with session citations

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Every query response cites the session(s) that support the answer. The system does not synthesize or infer beyond what was submitted.

**Reason:**  
Prevents hallucination from poisoning the world state. Users need to trust that answers reflect what actually happened in their campaign, not what the AI considers plausible.

**Alternatives considered:**  
- Uncited synthesis — rejected; indistinguishable from invention
- RAG with fuzzy synthesis — may be used internally, but the output layer always grounds to source

---

### D-004 — Input Mode review model: structured diff

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The review screen presents extraction results as a structured diff organized by category (Actors, Spaces, Events, Relations). Each item is an editable card. Users can accept, edit inline, delete, or manually add items. A single Commit action finalizes all changes.

**Reason:**  
Gives users full scope of what is about to change before committing. Familiar mental model (PR review / diff merge). Keeps the interaction structured without being a data entry form.

**Alternatives considered:**  
- **Annotated narrative (Model B):** extraction highlighted inline in the original text. Rejected as primary model — hard to surface missed items, relations are difficult to represent inline, easy to miss the full scope of changes.
- **Step-by-step confirmation (Model C):** one card at a time. Rejected — becomes exhausting for sessions with many extractions; loses gestalt; power users would abandon it. May be revisited as an optional onboarding mode.

---

### D-005 — Narrative summary header in diff review

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The diff review screen opens with a 1–3 sentence AI-generated narrative summary of the session before the structured diff. Example: *"Session 7 introduced a new faction, the Conclave, and shifted Mira's allegiance away from the party."*

**Reason:**  
Gives users the gestalt of the session before they engage with individual items. Makes fundamental misinterpretations immediately visible at a glance, allowing users to calibrate their review depth accordingly.

**Alternatives considered:**  
- No summary, jump straight to diff — rejected; users lack context to evaluate individual items efficiently

---

### D-006 — Delta-only display for existing entities in diff

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Entities already present in the world state show only what changed in the current session: new fields, modified fields, new relations. Entities recognized but unchanged do not appear in the diff at all.

**Reason:**  
Showing the full profile of every recognized entity on every session review buries the signal in noise. The diff communicates one thing per existing entity: what is new or different.

**Alternatives considered:**  
- Full profile with changes highlighted — rejected; too much visual noise per entity, especially in long campaigns with many established actors

---

### D-007 — New entities show full extracted profile in diff

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Entities appearing for the first time in the world state show their complete extracted profile in the diff for user confirmation.

**Reason:**  
Complement to D-006. New entities have no existing baseline — the user needs to review and confirm the full initial record before it enters world state.

---

### D-008 — Three interaction modes: Input, Query, Exploration

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The system exposes exactly three interaction modes: Input (feed new sessions), Query (natural language questions), and Exploration (browse world state visually).

**Reason:**  
Each mode serves a distinct mental context: contributing to the world, interrogating it, and navigating it. Keeping them separate prevents the UI from becoming a hybrid that does each thing poorly.

---

### D-009 — Exploration Mode views: timeline, entities, spaces, relations

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Exploration Mode consists of four interconnected views: Timeline, Entities, Spaces, and Relations.

**Reason:**  
These four views map directly to the four primary domain objects users care about navigating. Together they cover the full surface of the world state without redundancy.

---

### D-010 — Exploration Mode is read-only for world state

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
No direct edits to entities, spaces, or relations are permitted from Exploration Mode. All world state mutation flows exclusively through Input Mode (session commit pipeline).

**Reason:**  
Keeping mutation in one place eliminates a second surface to maintain, a second permission model to enforce, and ambiguity about what is canonical vs. editorially added. Clean separation of read and write paths.

**Alternatives considered:**  
- Direct inline editing in Exploration — rejected; blurs the line between canonical world state and user annotations, complicates permission model

---

### D-011 — Annotations are a first-class non-canonical feature

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Any campaign member can attach a free-text annotation to any entity, space, or relation. Annotations are explicitly non-canonical (player commentary, not world state), visible to all campaign members. GMs can pin or dismiss annotations.

**Reason:**  
Players need a way to voice observations, hypotheses, and reminders without those notes being mistaken for established world facts. Annotations provide this without touching the world state model.

**Alternatives considered:**  
- GM-only annotations — rejected; players are witnesses to the story and their observations have value
- No annotations — rejected; without them Exploration Mode is purely passive and offers no collaborative layer

---

### D-012 — "Propose a change" affordance present in v1, pipeline in v2

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Every entity, space, and relation in Exploration Mode shows a "Propose a change" affordance in v1. The affordance is visible but the approval pipeline behind it is not active until v2.

**Reason:**  
Plants the UX pattern early without requiring the infrastructure. Avoids a jarring feature addition in v2 by making the entry point familiar from day one.

---

### D-013 — v1 ships auth and role model

**Date:** 2026-04-05  
**Status:** ⚠️ Superseded by D-025 — role model expanded to include platform-level `admin`

**Decision (original):**  
v1 includes user authentication and a campaign-level role model with three roles: `gm`, `editor`, `player`.

**Reason:**  
The proposal/approval system (v2) requires identity. Building auth in v2 would mean retrofitting ownership onto every domain entity. The cost of building it in v1 is manageable; the cost of adding it later is not.

**Alternatives considered:**  
- Defer auth to v2 entirely — rejected; would require painful data migration and bake in assumptions that break with multi-user access

---

### D-014 — Session upload and diff review are GM-only by default

**Date:** 2026-04-05  
**Status:** ⚠️ Superseded by D-015 — editor role granted diff review rights

**Decision (original):**  
Only the GM role can upload session summaries and perform diff review and commit by default.

**Reason:**  
The GM is the canonical authority on the world. Uncontrolled session uploads from players could introduce conflicting or erroneous state.

---

### D-015 — Editor role: player with delegated session upload rights

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The `editor` role is a player promoted by the GM. Editors can upload session summaries and perform diff review. Promotion is per-campaign and at the GM's discretion.

**Reason:**  
Some groups have players who write session recaps. Forcing everything through the GM creates friction for those groups. The editor role accommodates this without opening write access to all players.

---

### D-016 — Proposal/approval system: data model in v1, feature in v2

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The data model accommodates proposals (target entity, proposed delta, author, status, votes) from day one. The UI and approval logic ship in v2.

**Reason:**  
Middle path between ignoring collaboration and over-engineering v1. Protects future v2 implementation from requiring schema migrations. Follows Martin Fowler's principle: don't let current simplicity bake in future constraints.

---

### D-017 — Proposal approval rule: ≥1 player co-sign, then GM decides

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
A player proposal requires at least one other player to co-sign before it surfaces to the GM. The GM then approves or vetoes unilaterally.

**Reason:**  
Prevents the GM from being flooded with unvetted proposals. Player co-signing acts as a lightweight quality filter. GM retains final authority over canonical world state.

---

### D-018 — GM veto is unilateral

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The GM can reject any proposal regardless of player support, with no override mechanism.

**Reason:**  
The GM is the canonical authority on the narrative. The world state reflects their story. Player proposals are contributions, not votes.

---

### D-019 — Abandoned proposals expire

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Proposals that receive no action within a defined TTL are automatically expired. TTL value to be defined in v2 design.

**Reason:**  
Prevents proposal backlog from accumulating indefinitely and polluting the review queue. Keeps the system clean without requiring manual GM housekeeping.

---

### D-020 — Platform minimum group size: 3

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The platform is designed for groups of at least 3 (1 GM + 2 players). Duo campaigns (1 GM + 1 player) are out of scope.

**Reason:**  
The proposal co-signing rule (D-017) requires at least two players to function meaningfully. A duo campaign collapses the approval quorum to a single person, which undermines the social contract the system is built on.

---

### D-021 — All domain entities carry owner_id from day one

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Every domain entity (Campaign, Session, Actor, Space, Event, Relation, Proposal) carries an `owner_id` field in the data model from initial implementation, even in v1 where a single GM owns the campaign.

**Reason:**  
Avoids a costly future migration when multi-user and multi-campaign features are introduced. Follows the principle of designing for tomorrow's constraints today without over-engineering the current feature set.

---

### D-022 — Monorepo structure: apps/web + apps/api

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Blue Steel is maintained as a single monorepo with the following top-level structure:

```
blue-steel/
├── docs/
├── apps/
│   ├── web/     ← frontend
│   └── api/     ← backend
├── packages/    ← shared types and utilities (if needed)
└── README.md
```

**Reason:**  
Solo development with tightly coupled frontend and backend. A monorepo eliminates cross-repo coordination overhead, keeps docs co-located with the code they describe, and presents a cleaner portfolio artifact — one repo tells the full story. The  boundary keeps frontend and backend clearly separated without the overhead of separate repositories.

**Alternatives considered:**  
- Two repos (frontend + backend) — rejected; no independent deployment or team boundary justifies the split at this stage
- Three repos (docs + frontend + backend) — rejected; standalone docs repos drift and go stale; docs belong next to the code they describe

---

### D-023 — License: MIT

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
Blue Steel is released under the MIT License.

**Reason:**  
Public portfolio project. MIT maximises visibility and removes friction for employers, collaborators, and anyone who wants to study or fork the code. No commercial sensitivity or ideological open-source goals that would warrant a more restrictive license.

**Alternatives considered:**  
- Apache 2.0 — rejected; patent clauses add complexity with no benefit at this stage
- GPL v3 — rejected; copyleft requirement creates friction, wrong signal for a portfolio project
- No license — rejected; legally prevents any use or forking; looks unintentional

---

### D-024 — Multiple campaigns, admin-only creation

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The platform supports multiple campaigns. Only admin can create campaigns. GMs manage campaigns once created and assigned to them.

**Reason:**  
Multiple campaigns is a legitimate use case. Admin-only creation is a deliberate gate to control platform usage and database growth. A GM managing a campaign is distinct from having the privilege to create one.

**Alternatives considered:**  
- Any GM can create campaigns — rejected; removes the usage gate, makes DB growth uncontrolled
- Single campaign only — rejected; unnecessary restriction

---

### D-025 — Admin is a singleton platform-level super-user

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The `admin` role is a singleton — one per platform instance. It is not an assignable role. Admin has full platform access: campaign creation, user management, GM assignment, and platform monitoring.

**Reason:**  
Blue Steel is a controlled-usage platform, not a self-serve SaaS. A singleton admin keeps the usage gate simple and explicit. Multiple admins would require admin management UI that adds complexity with no current benefit.

**Alternatives considered:**  
- Assignable admin role — rejected; unnecessary complexity for a controlled-usage platform
- No admin role, GM creates campaigns — rejected; removes deliberate usage gate

---

### D-026 — Onboarding flow: admin creates campaign and assigns GM

**Date:** 2026-04-05  
**Status:** Active

**Decision:**  
The platform onboarding flow is:

```
Admin creates campaign
  → Admin assigns a user as GM
    → GM invites players and promotes editors
```

Admin hands off fully after GM assignment. All campaign membership and role management below that level is the GM's responsibility.

**Reason:**  
Keeps admin responsibility minimal and well-defined. Admin is a gate, not a manager. Once a campaign is running, it should be self-sufficient under GM authority.

**Alternatives considered:**  
- Admin manages all memberships — rejected; creates a bottleneck and makes admin a permanent dependency for campaign operation

---

*Entries are added as decisions are made. See PRD.md and ARCHITECTURE.md for context.*
