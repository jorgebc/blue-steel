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
| D-027 | Backend stack: Java 25 + Spring Boot 4.0.3 | ✅ Active | Definition |
| D-028 | Build tool: Maven | ✅ Active | Definition |
| D-029 | Database migration tool: Liquibase | ✅ Active | Definition |
| D-030 | Frontend stack: React + Vite + TypeScript | ✅ Active | Definition |
| D-031 | Database: PostgreSQL + pgvector (single instance) | ✅ Active | Definition |
| D-032 | LLM provider: Anthropic via Spring AI, provider-agnostic boundary | ✅ Active | Definition |
| D-033 | OQ-2 resolved — Conflict handling: non-blocking surface (Option C) | ✅ Active | Definition |
| D-034 | LLM cost governance: bounded pipeline + provider-level spend cap | ✅ Active | Definition |
| D-035 | OQ-3 resolved — World state versioning: per-entity version history | ✅ Active | Definition |
| D-036 | Mutation testing: PITest via Maven plugin | ✅ Active | Definition |
| D-037 | Architecture boundary tests: ArchUnit | ✅ Active | Definition |
| D-038 | Package structure: domain / application / adapters / config | ✅ Active | Definition |
| D-039 | Configuration classes co-located with their adapter | ✅ Active | Definition |
| D-040 | OQ-C resolved — Embedding model: OpenAI text-embedding-3-small | ✅ Active | Definition |
| D-041 | OQ-A resolved — Entity resolution: two-stage pgvector + LLM | ✅ Active | Definition |
| D-042 | OQ-E resolved — Uncertain entity card: resolution required before commit | ✅ Active | Definition |

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
└── README.md
```

**Reason:**  
Solo development with tightly coupled frontend and backend. A monorepo eliminates cross-repo coordination overhead, keeps docs co-located with the code they describe, and presents a cleaner portfolio artifact — one repo tells the full story. The `apps/` boundary keeps frontend and backend clearly separated without the overhead of separate repositories. There is no shared code layer between `apps/web` and `apps/api` — the contract between them is the HTTP API.

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

### D-027 — Backend stack: Java 25 + Spring Boot 4.0.3

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
The backend is built with Java 25 (LTS) and Spring Boot 4.0.3 (GA, February 2026).

**Reason:**  
Developer has 10+ years of professional Java and Spring ecosystem experience. Fluency with the stack eliminates unnecessary learning overhead on a solo project. Java 25 provides stable virtual threads, mature records, and complete pattern matching. Spring Boot 4.x aligns with Spring Framework 7 and Spring AI. Spring Boot's production-grade ecosystem (security, data, transaction management) maps directly to project requirements. Hexagonal architecture maps naturally onto Spring idioms.

**Alternatives considered:**  
- TypeScript / Node.js — rejected; developer fluency with Java is the decisive factor
- Python — rejected; stronger AI ecosystem but introduces split-language monorepo with separate toolchains and no offsetting benefit given Spring AI availability

---

### D-028 — Build tool: Maven

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Maven (pom.xml) is the build tool for `apps/api`.

**Reason:**  
Developer knows Maven and does not know Gradle. For a solo project, fluency with the build tool is not optional — dependency resolution and plugin configuration issues will arise and must be debugged without friction. Gradle offers no technical advantage for a project of this size that justifies the learning cost.

**Alternatives considered:**  
- Gradle (Kotlin DSL) — rejected; no justification for learning cost on a solo project where developer knows Maven. Incremental build performance advantages apply to large multi-module monorepos, not this project.

---

### D-029 — Database migration tool: Liquibase

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Liquibase is the database migration tool.

**Reason:**  
Developer knows Liquibase. Built-in rollback support is a genuine advantage during active v1 schema development. Spring Boot integration is first-class and equivalent to the alternative.

**Alternatives considered:**  
- Flyway — rejected; developer knows Liquibase, Flyway's simpler mental model ("just SQL files") offers no benefit to someone already fluent with Liquibase. Flyway rollback requires manually written down scripts; Liquibase rollback is built in.

---

### D-030 — Frontend stack: React + Vite + TypeScript

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
The frontend is built with React 18, Vite, TypeScript, shadcn/ui, TanStack Query (v5), Zustand (v5), React Router (v6), and React Flow (v12).

**Reason:**  
Blue Steel is an auth-gated SPA with no SEO or SSR requirements. A pure SPA is the right architectural fit. The hard frontend problems (diff review UI, graph visualization, timeline view) are component-level — they are not solved by a more opinionated framework. React Flow is named explicitly because the Relations view (D-009) is a graph visualization problem that requires a dedicated library. Zustand handles client state with minimal boilerplate. TanStack Query handles server state without Redux complexity.

**Alternatives considered:**  
- Next.js — rejected; SSR and RSC add framework-level complexity that solves no stated requirement. Hard problems live at the component level, not the framework level.
- Vue 3 + Vite — rejected; smaller ecosystem, less portfolio visibility, no technical advantage for this use case.

---

### D-031 — Database: PostgreSQL + pgvector

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
A single PostgreSQL instance with the pgvector extension handles both the relational world state and the vector/semantic layer for Query Mode.

**Reason:**  
Polyglot persistence (dedicated vector database + relational database) introduces synchronisation complexity, a second operational concern, and a second backup strategy — none of which is justified at this scale. pgvector is production-grade and Spring AI integrates with it natively. One database, one connection pool, one failure domain.

**Alternatives considered:**  
- PostgreSQL + dedicated vector DB (Pinecone, Qdrant, Weaviate) — rejected; synchronisation complexity between two stores is real and ongoing. Dedicated vector DB would be justified at scale or with advanced retrieval requirements not present in v1. Follows Fowler's principle: don't introduce architectural complexity without a concrete, present justification.

---

### D-032 — LLM provider: Anthropic (Claude), provider-agnostic by design

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Anthropic (Claude) is the default LLM provider. Integration is provider-agnostic via Spring AI's abstraction layer. The LLM is an external actor behind a driven port. Additional providers (OpenAI, Google Gemini) are not implemented in v1.

**Reason:**  
Developer holds an active Anthropic subscription. Claude is well-suited to long-context narrative understanding and structured extraction tasks. Spring AI provides a model-agnostic abstraction (`ChatClient`, `EmbeddingModel`, `VectorStore`) — swapping providers means replacing the adapter, not touching domain or application code. This is Cockburn's hexagonal pattern applied directly: the LLM is an external actor behind a port.

**Alternatives considered:**  
- OpenAI as default — rejected in favour of existing Anthropic subscription.
- Multi-provider free-tier rotation (Anthropic + OpenAI + Google) — rejected. Free tier rate limits are aggressive and unsuitable for production use. Rotation logic (detect exhaustion, switch provider, retry, handle partial failures) is non-trivial infrastructure that delivers zero user value. Different providers produce inconsistent structured outputs requiring per-provider prompt tuning. The real cost of running Blue Steel for a small group on Claude is negligible; the development cost of the rotation strategy is not. Follows Fowler's Speculative Generality principle — don't build for problems you don't have.
- Additional providers in v1 — rejected; deferred to v2 on concrete justification only.

---

### D-033 — OQ-2 resolved: Conflict handling — non-blocking surface (Option C)

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
When a new session extraction contradicts existing world state, the system detects the conflict and surfaces it as a warning card inside the diff review screen. The commit is not blocked. The user decides whether to accept, edit, or delete the conflicting item.

Three conflict types are distinguished:
- **Hard contradiction** — directly negates an established fact (triggers a warning)
- **Soft evolution** — updates a fact narratively (treated as a normal delta, no warning)
- **Ambiguity** — possible entity resolution issue (handled by the entity resolution step, not conflict detection)

**Reason:**  
Tabletop RPGs contain retcons, resurrections, and deliberate continuity breaks. Blocking commit (Option B) is paternalistic — the GM may intentionally contradict previous state. Passive detection (Option A) abandons the system's core value proposition. Non-blocking warnings (Option C) inform without obstructing and respect user authority, consistent with D-002.

**Alternatives considered:**  
- Option A (no conflict detection) — rejected; users reviewing 20 diff cards will not reliably cross-reference against accumulated world state. Silent contradictions degrade query quality over time.
- Option B (blocking resolution) — rejected; creates friction on legitimate retcons, high false positive risk on soft evolutions, undermines GM authority.

---

### D-034 — LLM cost governance: bounded pipeline + provider-level spend cap

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
LLM cost is controlled at four levels: (1) a hard monthly spend cap in the Anthropic console, set before the first production call; (2) token estimation before every LLM call, rejecting calls that exceed a configurable envelope; (3) pgvector similarity search scoping LLM context to relevant chunks only, preventing context growth with campaign size; (4) usage logging of every LLM call with tokens in, tokens out, estimated cost, session, user, and pipeline stage.

Each session ingestion makes exactly two bounded LLM calls: knowledge extraction and conflict detection. Conflict detection context is bounded by the pgvector retrieval step.

**Reason:**  
Cost per session is predictable and small (estimated 3–5k tokens combined for a typical summary). The provider-level spend cap is the safety net that catches anything the application-level controls miss. Usage logging makes cost observable per campaign, per session, and per pipeline stage from day one — cost surprises are detectable before they compound.

**Alternatives considered:**  
- No cost controls — rejected; LLM API costs can compound unexpectedly without visibility or limits.
- Heuristic-only conflict detection (no LLM) — considered; rejected because semantic comparison against world state facts cannot be done reliably without LLM reasoning. The pgvector bounding makes the LLM call affordable.

---

### D-035 — OQ-3 resolved: World state versioning — per-entity version history

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
World state is versioned at the entity level. Each domain entity (Actor, Space, Event, Relation) carries an append-only version history table. Every change references the session that produced it. Current state is the latest version per entity. Point-in-time state is the latest version at or before a given session.

Each version row stores both `changed_fields` (delta only, satisfying D-006) and `full_snapshot` (complete state at that version, enabling efficient point-in-time reads).

**Reason:**  
The versioning model mirrors the diff model exactly — a session commit is a set of entity version increments. History is structurally preserved at the entity level, satisfying D-001 and D-003. Current state is a straightforward read. The approach is storage-efficient (only changed entities accumulate history) and schema-simple (no event sourcing infrastructure required).

**Alternatives considered:**  
- Snapshot-per-session (full world state copy on every commit) — rejected; storage grows quadratically, snapshots are largely redundant, gives nothing that entity versioning doesn't.
- Event sourcing (append-only delta log, state derived by replay) — rejected; intellectually sound but architecturally heavy for this domain. The use case does not require audit logs at event granularity or CQRS. Fowler cautions against event sourcing unless the use case genuinely requires it.

---

### D-036 — Mutation testing: PITest via Maven plugin

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Mutation testing is performed with PITest (PIT) via the Maven plugin. A minimum mutation score threshold is configured from day one and fails the build if not met. The threshold is set at an achievable baseline and raised deliberately — never aspirationally.

Mutation testing is scoped by build phase: domain core on every build, application layer pre-merge, full codebase nightly or on-demand.

**Reason:**  
Line coverage measures which lines were executed, not whether tests verify anything meaningful. PITest modifies bytecode and runs the test suite against each mutant — a surviving mutant is a real gap. Domain core is the highest-value target: business logic and invariants must be verified, not merely executed. Scoping by build phase keeps CI feedback fast.

**Alternatives considered:**  
- Line/branch coverage only — rejected; insufficient signal. A test that calls a method without asserting its output passes coverage without catching mutations.
- Full codebase on every build — rejected; PITest is slow at scale. Adapter and config code is lower-value for mutation testing.

---

### D-037 — Architecture boundary tests: ArchUnit

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Hexagonal architecture layer boundaries are enforced as executable tests using ArchUnit. Rules are defined once in a dedicated `ArchitectureTest` class and run on every build. The following rules are enforced: domain has no Spring or JPA imports; driving adapters only call application ports; driven adapters are never imported by domain or application; all ports are interfaces; no Spring annotations appear on domain classes.

**Reason:**  
The most common way hexagonal architecture degrades is through small, invisible violations — a JPA annotation leaking into a domain class, a controller calling a repository directly. Code review does not catch these reliably at scale. ArchUnit makes violations impossible to merge. It is fast (bytecode analysis, no Spring context), co-located with the test suite, and runs on every build.

**Alternatives considered:**  
- Convention-only enforcement (code review) — rejected; conventions drift under deadline pressure. The boundary is too important to depend on human vigilance alone.

---

### D-038 — Package structure: domain / application / adapters / config

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
The backend uses four top-level packages: `domain`, `application`, `adapters`, and `config`. Adapters are subdivided into `adapters.in` (driving) and `adapters.out` (driven), mirroring the port naming in `application.port.in` and `application.port.out`. The previous `infrastructure` and `api` split is replaced by this structure.

**Reason:**  
`adapters.in` and `adapters.out` are hexagonal architecture terms — they name what things are, not what framework they use. REST controllers and JPA repositories are both adapters; separating them into `api` and `infrastructure` leaks Spring Boot convention into the package structure. The `in`/`out` naming is consistent end to end: ports and adapters share the same directional vocabulary. Domain stays top-level (not nested inside application) to signal its primacy and to keep ArchUnit rules clean and unambiguous.

**Alternatives considered:**  
- `infrastructure` + `api` split (Spring Boot convention) — rejected; framework naming leaking into package structure, inconsistent with hexagonal vocabulary.
- `domain` nested inside `application` — rejected; domain is the most important and most isolated layer. Top-level placement signals this. Nesting complicates ArchUnit rule expression.

---

### D-039 — Configuration classes co-located with their adapter

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Spring `@Configuration` classes live in the package of the adapter they configure. A top-level `config/` package exists exclusively for cross-cutting configuration with no adapter home. All configuration classes are suffixed `Config`.

| Config class | Package |
|---|---|
| `WebConfig` | `adapters.in.web` |
| `PersistenceConfig` | `adapters.out.persistence` |
| `AiConfig` | `adapters.out.ai` |
| `SecurityConfig` | `adapters.out.security` |
| `ApplicationConfig` | `config` |

**Reason:**  
Configuration that belongs to an adapter should travel with that adapter. A shared `config/` package implies cohesion between unrelated concerns and obscures which config belongs to which part of the system. Co-location makes it clear: removing the JPA adapter takes `PersistenceConfig` with it. The `Config` suffix makes all configuration classes findable by name search regardless of their package location.

**Alternatives considered:**  
- Flat `config/` package for all configuration — rejected; mixes unrelated concerns, implies false cohesion, works against the hexagonal structure by obscuring adapter ownership.

---

### D-040 — OQ-C resolved: Embedding model — OpenAI text-embedding-3-small

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
The embedding model is OpenAI `text-embedding-3-small` at 1536 dimensions. Embedding calls are routed through the `EmbeddingPort` abstraction via Spring AI — the application layer is unaware of the provider.

**Reason:**  
Anthropic does not provide an embedding API; Claude models are generative only. A separate embedding provider is not a compromise — it is a structural requirement. OpenAI `text-embedding-3-small` is the industry standard for semantic retrieval: excellent quality for narrative text, negligible cost (fractions of a cent per campaign), first-class Spring AI support, and no local service dependency. The 1536-dimension schema entry in `entity_embeddings` is confirmed correct for this model.

**Alternatives considered:**  
- OpenAI `text-embedding-3-large` (3072 dimensions) — rejected; marginally better quality, 6x more expensive, 2x larger vectors with slower similarity search. Quality delta does not justify cost and storage overhead for this domain.
- Ollama local models (`nomic-embed-text`, `mxbai-embed-large`) — rejected; requires a running local service as an operational dependency, complicating deployment and making the project harder to run for others. Appropriate for experimentation, not for a portfolio project.

---

### D-041 — OQ-A resolved: Entity resolution — two-stage pgvector + LLM

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
Entity resolution uses a two-stage pipeline. Stage 1: pgvector similarity search per extracted mention — mentions scoring below a minimum similarity floor are classified as NEW immediately with no LLM call. Stage 2: mentions scoring above the floor are passed to the LLM alongside their top candidates for final classification. The LLM returns one of three outcomes per mention: MATCH (attach to existing entity), NEW (create new entity record), or UNCERTAIN (surface as a dedicated diff card for mandatory user resolution).

**Reason:**  
Entity resolution spans three problem layers: name variants (string-level), semantic identity (`"the old wizard"` = Aldric), and genuine ambiguity. Embedding similarity handles the first layer efficiently and eliminates the clear-NEW cases before the LLM is involved. The LLM handles the second layer where semantic reasoning is required. Genuine ambiguity (third layer) is surfaced as UNCERTAIN rather than forced to a confident wrong answer — the user is the trust boundary (D-002). The two-stage approach bounds LLM cost: a session where most entities are clearly new makes at most a handful of LLM resolution calls, not one per extracted mention.

**Alternatives considered:**  
- Embedding similarity only (no LLM) — rejected; fails on semantic identity. `"the old wizard"` does not embed close enough to `"Aldric"` without contextual reasoning.
- LLM-only resolution — rejected; one LLM call per extracted mention is unbounded and expensive at scale.
- Forced binary match/new (no UNCERTAIN) — rejected; forces a confident wrong answer on genuinely ambiguous cases. Wrong decisions on entity identity are hard to correct once history is committed against them.

---

### D-042 — OQ-E resolved: Uncertain entity card — resolution required before commit

**Date:** 2026-04-06  
**Status:** Active

**Decision:**  
All UNCERTAIN entity resolution cards must be resolved to MATCH or NEW before the session commit is permitted. There is no defer option. The Commit button is disabled until zero UNCERTAIN cards remain. A progress indicator surfaces the count of unresolved items. The backend validates the commit payload and rejects with `422` if any UNCERTAIN entities are present.

When a user is genuinely uncertain, the correct choice is **different entity** (NEW). An incorrect split can be corrected through the proposal system in v2 (D-016).

**Reason:**  
An unresolved entity left out of a session commit creates an orphaned, unanchored record. Reconciling it later against session history that was committed without it introduces data model complexity that outweighs the UX cost of forcing a decision. A wrong but committed decision is recoverable — the proposal system exists for exactly this correction. An orphaned entity is structurally problematic. The backend 422 validation provides defence in depth against UI bypass.

**Alternatives considered:**  
- Allow defer (exclude uncertain entity from commit, resolve later) — rejected; creates orphaned records with no clean reconciliation path against already-committed session history. The complexity cost is higher than forcing a resolvable wrong decision.
- Blocking commit on all uncertain items without guidance — rejected; users need to understand why commit is blocked. Progress indicator and explicit guidance ("choose different entity if unsure") are required.

---

*Entries are added as decisions are made. See PRD.md and ARCHITECTURE.md for context.*
