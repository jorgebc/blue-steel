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
| D-043 | Authentication: stateless JWT (auth only); campaign role via DB (authz) | ✅ Active | Definition |
| D-044 | Environment model: local + prod only | ✅ Active | Definition |
| D-045 | Frontend hosting: Vercel free tier | ✅ Active | Definition |
| D-046 | Backend hosting: Oracle Cloud Always Free ARM VM | ✅ Active | Definition |
| D-047 | Database hosting: Neon free tier (PostgreSQL + pgvector) | ✅ Active | Definition |
| D-048 | CI/CD: GitHub Actions with path-filtered workflows | ✅ Active | Definition |
| D-049 | Local dev LLM strategy: mock ports by default, real APIs via profile flag | ✅ Active | Definition |
| D-050 | Secret management: .env file on Oracle VM, never committed | ✅ Active | Definition |
| D-051 | User onboarding: invitation-only, email with temporary password | ✅ Active | Definition |
| D-052 | Query execution model: synchronous, single LLM call, 504 on timeout | ✅ Active | Definition |
| D-053 | Commit payload: "add" action (manually introduce missed entities) deferred to v2 | ✅ Active | Definition |
| D-054 | Draft session policy: single active draft per campaign; GM-discardable (soft delete) | ✅ Active | Definition |
| D-055 | OQ-D resolved — Pagination: offset for entity lists, keyset for Timeline | ✅ Active | Definition |
| D-056 | No E2E tests; backend top level is integration with external services mocked | ✅ Active | Definition |
| D-057 | Pre-Phase 1 gate: Spring Boot 4.x compatibility must be verified before development | ✅ Active | Definition |
| D-058 | OQ-6 resolved — Q&A log deferred to v2; queries are stateless in v1 | ✅ Active | Definition |
| D-059 | OQ-B resolved — JWT: HS256, 15-min access token, rotating refresh tokens (30-day TTL) | ✅ Active | Definition |
| D-060 | Auth implementation: self-implemented on Spring Security; email delivery outsourced via EmailPort | ✅ Active | Definition |
| D-061 | Campaign creation atomically assigns the initial GM | ✅ Active | Definition |
| D-062 | pgvector retrieval uses native queries; Spring AI VectorStore not used | ✅ Active | Definition |
| D-063 | Embedding generation is async post-commit; commit endpoint returns immediately | ✅ Active | Definition |
| D-064 | Two invitation endpoints: platform-level (admin) and campaign-scoped (GM) | ✅ Active | Definition |
| D-065 | Commit message format: Conventional Commits | ✅ Active | Definition |
| D-066 | Branch naming: type/short-description (kebab-case) | ✅ Active | Definition |
| D-067 | Frontend form library: React Hook Form | ✅ Active | Definition |
| D-068 | Frontend package manager: npm | ✅ Active | Definition |
| D-069 | Session `sequence_number` assigned at commit, nullable until then | ✅ Active | Definition |
| D-070 | Self-service password reset not implemented in v1 | ✅ Active | Definition |
| D-071 | Java code style: Spotless + google-java-format | ✅ Active | Definition |
| D-072 | Structured logging: logstash-logback-encoder; MDC fields for LLM calls | ✅ Active | Definition |
| D-073 | Admin bootstrap: `ApplicationReadyEvent` startup listener seeded from env vars | ✅ Active | Phase 1 |
| D-074 | Stuck-processing recovery: startup transition + scheduled TTL check | ✅ Active | Phase 2 |
| D-075 | `EmailPort` activation: `email-real` Spring profile, independent of `llm-real` | ✅ Active | Phase 1 |
| D-076 | DiffPayload and CommitPayload are formalized JSON contracts in ARCHITECTURE.md §7.6 | ✅ Active | Phase 2 |
| D-077 | Invitation model: no invitations table; temporary password + `force_password_change` flag | ✅ Active | Phase 1 |

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
The review screen presents extraction results as a structured diff organized by category (Actors, Spaces, Events, Relations). Each item is an editable card. Users can accept, edit inline, or delete items. A single Commit action finalizes all changes.

**v1 scope note:** The ability to manually introduce entities the AI missed ("add" action) is deferred to v2 — see D-053. In v1, the three supported actions per item are `accept`, `edit`, and `delete`.

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
Any campaign member can attach a free-text annotation to any actor, space, relation, or event. Annotations work as a comment section — non-canonical and clearly marked as player commentary, not world state. Visible to all campaign members.

**Reason:**  
Players need a way to voice observations, hypotheses, and reminders without those notes being mistaken for established world facts. A comment section provides this without touching the world state model and without requiring any GM moderation role.

**Alternatives considered:**  
- GM-only annotations — rejected; players are witnesses to the story and their observations have value
- No annotations — rejected; without them Exploration Mode is purely passive and offers no collaborative layer
- Pin/dismiss moderation by GM — rejected; unnecessary friction. A simple comment section serves the use case without additional moderation mechanics.

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
**Amended:** 2026-04-13  
**Status:** Active

**Decision:**  
The platform is designed for groups of at least 3 (1 GM + 2 players). Duo campaigns (1 GM + 1 player) are out of scope.

**Enforcement:** This is a **design-scope statement, not a runtime invariant in v1.** No API endpoint or DB constraint enforces a minimum member count in v1. The constraint becomes a runtime concern in v2, when the proposal co-signing rule (D-017) ships — co-signing requires at least two players to form a quorum, so the v2 proposal workflow design must include a membership count check before a proposal can be submitted. In v1, all shipped features (session ingestion, query, exploration) function correctly regardless of campaign member count.

**Reason:**  
The proposal co-signing rule (D-017) requires at least two players to function meaningfully. A duo campaign collapses the approval quorum to a single person, which undermines the social contract the system is built on. The constraint is deferred to v1 runtime enforcement because the feature it protects (proposal co-signing) does not ship until v2.

---

### D-021 — All domain entities carry owner_id from day one

**Date:** 2026-04-05  
**Amended:** 2026-04-13  
**Status:** Active

**Decision:**  
Every domain entity (Session, Actor, Space, Event, Relation, Proposal) carries an `owner_id` field in the data model from initial implementation. `owner_id` is set at creation and does not transfer.

**Semantic definition:** `owner_id` refers to the `user_id` of the campaign member who *committed* the entity — the GM or editor who performed the commit action that introduced it to world state. It is not the submitter of the session summary. In the common case (Editor submits and commits their own summary) these are the same person. When a GM reviews and commits a draft submitted by an Editor, `owner_id` on the resulting entities is the GM's `user_id`. The commit transaction sets `owner_id` from the authenticated caller of `POST .../commit` — it is never threaded forward from the submission step. Sessions track the submitter separately via `sessions.owner_id`; together these two fields provide a complete audit trail: who submitted, and who committed.

**Campaign is the explicit exception.** The `campaigns` table does not carry an `owner_id` column. Campaign ownership (the GM relationship) is already a first-class structural relationship in `campaign_members`, enforced by a partial unique index (`WHERE role = 'gm'`). The `campaigns` table carries `created_by` for the admin audit trail. Adding a redundant `owner_id` column pointing to the GM would create two sources of truth for the same fact and introduce a synchronisation obligation with no query or authorization benefit — use cases that need the GM join through `campaign_members`. Do not add `owner_id` to `campaigns`.

**Reason:**  
Avoids a costly future migration when multi-user and multi-campaign features are introduced. Follows the principle of designing for tomorrow's constraints today without over-engineering the current feature set. The Campaign carve-out avoids redundancy that would violate normalization without any offsetting benefit.

**Amendment rationale (2026-04-13):**  
The original decision listed Campaign in the scope of the `owner_id` rule without accounting for the fact that GM ownership is already modelled structurally in `campaign_members`. The amendment removes Campaign from the scope and documents the structural reason explicitly to prevent a future developer from adding the column as an apparent fix.

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

**Risk note:**  
Spring Boot 4.x is a recent major release. Before Phase 1 development begins, verify compatibility for the following dependencies: Spring AI (`ChatClient`, `EmbeddingModel`), Testcontainers Spring Boot integration, Liquibase Spring Boot starter, and Spring Security 7. Any dependency lagging on Boot 4.x support should be flagged before the roadmap's Phase 1 start gate.

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
Developer holds an active Anthropic subscription. Claude is well-suited to long-context narrative understanding and structured extraction tasks. Spring AI provides model-agnostic abstractions (`ChatClient`, `EmbeddingModel`) — swapping providers means replacing the adapter, not touching domain or application code. This is Cockburn's hexagonal pattern applied directly: the LLM is an external actor behind a port. Note: Spring AI's `VectorStore` is not used — pgvector retrieval uses native queries (D-062).

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

Each session ingestion makes at most three bounded LLM calls: (1) knowledge extraction, (2) entity resolution Stage 2 (conditional — only when extracted mentions score above the similarity floor; see D-041), and (3) conflict detection. Conflict detection context is bounded by the pgvector retrieval step. Sessions where all extracted mentions score below the resolution floor require only two LLM calls.

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
| `SecurityConfig` | `adapters.in.security` |
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

### D-043 — Authentication mechanism: stateless JWT; authorization via DB

**Date:** 2026-04-07
**Amended:** 2026-04-12
**Status:** Active

**Decision:**
Authentication uses stateless JWTs. Tokens are issued on login, included in the `Authorization: Bearer` header on every request, and validated by Spring Security on the server without a session store. The access token carries only `user_id` and `is_admin`.

**Authentication is stateless.** Spring Security validates JWT signature and expiry with no DB call. This is the only thing "stateless" applies to.

**Authorization is not stateless.** Campaign-level role (`gm`, `editor`, `player`) is resolved at the use-case boundary via a DB read against `campaign_members` on every request that requires it. This means role changes (e.g., removing a player from a campaign) take effect on the next request — there is no stale-role window. A token blocklist is not implemented in v1; the 15-minute access token TTL limits exposure if a token is intercepted.

Token expiry and refresh token strategy: see D-059.

**Reason:**
Encoding campaign roles in the JWT (to avoid the DB read) would mean role changes don't take effect until the token expires — up to 15 minutes during which a removed player retains access. For a system where the GM controls sensitive narrative content, that window is unacceptable. The DB read at the use-case boundary is the correct authorization model. It also keeps the JWT minimal (`user_id` + `is_admin` only), which avoids token bloat as a user joins more campaigns.

The "stateless" claim in the original decision was imprecise — it conflated authentication (truly stateless) with authorization (necessarily stateful against live membership data). This amendment corrects that conflation explicitly.

**Alternatives considered:**
- JWT encodes all campaign roles — rejected; role changes don't take effect until token expiry. A removed player retains read access to sensitive campaign content for up to 15 minutes. Unacceptable.
- Server-side sessions — rejected; requires session store (Redis or DB), adds an operational dependency, complicates horizontal scaling if ever needed.
- OAuth/OIDC (external provider) — rejected; adds external dependency for a controlled-usage platform. May be revisited if the admin wants to delegate identity management.
- Opaque tokens with DB lookup on every request — rejected; the JWT + targeted DB authorization read gives the same result with less infrastructure.

---

### D-044 — Environment model: local + prod only

**Date:** 2026-04-09
**Status:** Active

**Decision:**
Two environments only: `local` (Docker Compose on developer machine) and `prod` (Oracle Cloud + Vercel + Neon). No staging environment.

**Reason:**
The project scope and team size do not justify consuming free-tier quota on a third environment. Local development is the pre-production validation layer. The testing strategy (unit, integration with Testcontainers, ArchUnit, PITest) provides sufficient confidence before pushing to main.

**Alternatives considered:**
- Local + Staging + Prod — rejected; doubles free-tier resource consumption for a solo/small-team project with no offsetting benefit.
- Local + Prod + branch previews — considered; Vercel provides PR preview URLs for the frontend natively at no cost. These are available but do not constitute a persistent staging environment.

---

### D-045 — Frontend hosting: Vercel free tier

**Date:** 2026-04-09
**Status:** Active

**Decision:**
The React / Vite / TypeScript frontend is deployed to Vercel on the free Hobby tier. Deployment is triggered automatically by Vercel's GitHub integration on push to `main`. PR pushes generate ephemeral preview URLs automatically.

**Reason:**
Vercel is the natural fit for Vite/React static builds. Zero configuration required. Global CDN, automatic HTTPS, branch preview URLs, and automatic production deploys are all included in the free tier. No CI deploy step is needed for the frontend — Vercel's GitHub integration handles it natively.

**Alternatives considered:**
- Netlify — rejected in favour of Vercel; both are equivalent for this use case. Vercel's DX for Vite is marginally better documented.
- GitHub Pages — rejected; no automatic deploy integration, no CDN, no PR preview URLs.

---

### D-046 — Backend hosting: Oracle Cloud Always Free ARM VM

**Date:** 2026-04-09
**Status:** Active

**Decision:**
The Spring Boot backend runs on an Oracle Cloud Always Free ARM VM (Ampere A1 Compute). The VM runs Docker. The backend container is managed via Docker Compose on the VM. Images are built for `linux/arm64` using `docker buildx` in GitHub Actions and pushed to GitHub Container Registry (ghcr.io). Deployment is: SSH into VM → `docker pull` → `docker compose up -d`.

**Reason:**
Oracle Cloud's Always Free tier is the only hosting option offering sufficient RAM (up to 24GB configurable on ARM) for a Spring Boot 4.x application at zero cost with no expiry. Spring Boot on ARM is fully supported. The self-managed Docker approach is simple and gives full control over the runtime environment.

A credit card is required at Oracle signup, but Always Free resources are never charged as long as no paid resources are provisioned. This risk is accepted.

**Alternatives considered:**
- Render free tier — rejected; 512MB RAM is marginal for Spring Boot, and the 15-minute inactivity sleep-down creates unacceptable cold-start latency for a weekly-use RPG app.
- Fly.io free tier — rejected; 256MB per VM is insufficient for Spring Boot without aggressive JVM tuning that would complicate the development setup.
- Railway — rejected; no longer has a genuine free tier (only monthly credit that expires).
- Paid tier (~$5/mo) — rejected in favour of Oracle free tier. The ops overhead of managing a VM is acceptable given the zero cost.

---

### D-047 — Database hosting: Neon free tier (PostgreSQL + pgvector)

**Date:** 2026-04-09
**Status:** Active

**Decision:**
The production PostgreSQL database runs on Neon's free tier. Neon supports the pgvector extension, which is required for the embedding and similarity search pipeline (D-041). The free tier provides 0.5GB storage and never pauses or auto-deletes.

Neon's database branching feature is available for migration testing: a branch of the prod database can be used to validate Liquibase changelogs before applying to main.

**Reason:**
Neon is the strongest free PostgreSQL option for this use case: pgvector supported, always-on (no inactivity pause), no data deletion, and database branching for safe migration testing. The 0.5GB storage limit is sufficient for v1 with a small number of campaigns and sessions; vector storage (1536-dimension embeddings) is the primary growth factor to monitor.

**Alternatives considered:**
- Supabase — rejected; free tier pauses the database after 7 days of inactivity. For a weekly RPG campaign this creates cold-start risk on session night.
- Render PostgreSQL (free) — rejected; auto-deletes the database after 90 days of inactivity. Not viable for production.
- Self-hosted PostgreSQL on Oracle VM — considered; eliminates the external dependency and storage limits. Rejected in favour of Neon for v1 because: (a) Neon separates compute and storage concerns, (b) branching enables safe migration testing, (c) the free tier is sufficient. May be revisited if storage limits are reached or Neon's free tier changes.

---

### D-048 — CI/CD: GitHub Actions with path-filtered workflows

**Date:** 2026-04-09
**Status:** Active

**Decision:**
CI/CD uses GitHub Actions with two independent workflow files, each filtered to its project path:
- `backend.yml` — triggers on `apps/api/**` changes: compile → unit + ArchUnit tests → integration tests (Testcontainers) → PIT mutation tests (domain core) → build JAR → build arm64 Docker image → push to ghcr.io → SSH deploy to Oracle VM. Steps 5–7 run on push to `main` only.
- `frontend.yml` — triggers on `apps/web/**` changes: type check → lint → Vitest unit tests → vite build. Deployment is handled natively by Vercel's GitHub integration.

Docker images are pushed to GitHub Container Registry (ghcr.io), which is included in the GitHub free plan.

**Reason:**
Path-filtered workflows avoid wasting CI minutes on unrelated changes. The two projects have independent toolchains and independent deployment targets — they should have independent pipelines. GitHub Actions free plan (2,000 min/month for private repos) is well within budget for this workload.

**Alternatives considered:**
- Single workflow for both projects — rejected; every push triggers both pipelines regardless of what changed. Wasteful and slower.
- Turborepo / Nx monorepo tooling — rejected; overkill for two independent projects with no shared code layer. Fowler's Speculative Generality — don't build for problems you don't have.

---

### D-049 — Local dev LLM strategy: mock ports by default, real APIs via profile flag

**Date:** 2026-04-09
**Status:** Active

**Decision:**
In local development, all LLM-backed ports (`NarrativeExtractionPort`, `EntityResolutionPort`, `ConflictDetectionPort`, `QueryAnsweringPort`, `EmbeddingPort`) are implemented by in-memory mock adapters that return canned responses. The `local` Spring profile activates mocks by default.

A separate `llm-real` Spring profile swaps in the real Anthropic and OpenAI adapters. Activated via `--spring.profiles.active=local,llm-real` when real pipeline testing is needed. Mock and real implementations coexist in `adapters.out.ai`.

**Reason:**
Day-to-day development work (UI, domain logic, API endpoints, test writing) does not require real LLM calls. Mock adapters provide deterministic, fast, zero-cost feedback. The hexagonal port design makes this swap transparent — no domain code changes needed. Real APIs are available when the extraction or query pipelines need to be tested end-to-end, but this is the exception rather than the default.

**Alternatives considered:**
- Always use real APIs in dev — rejected; introduces API cost for every local run and makes the feedback loop dependent on network and provider availability.
- Always mock in dev — rejected; there must be a path to test the real pipeline locally before it reaches production. The profile flag provides this escape hatch.

---

### D-050 — Secret management: .env file on Oracle VM, never committed

**Date:** 2026-04-09
**Status:** Active

**Decision:**
Production secrets (`DATABASE_URL`, `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `JWT_SECRET`) are stored in a `.env` file on the Oracle VM. The file is read by Docker Compose via the `env_file` directive. The `.env` file is never committed to the repository; it is listed in `.gitignore`. Rotating a secret requires SSH access to the VM and a container restart.

Vercel environment variables (API base URL, public config) are managed through the Vercel dashboard.

**Reason:**
A `.env` file on the VM is the simplest secret management approach for a self-hosted single-VM deployment. No external secret manager dependency is required at this scale. The security model is equivalent to the VM's SSH access control — the same person who can SSH into the server can read the secrets, which is acceptable for a personal/small-team project.

**Alternatives considered:**
- GitHub Actions secrets injected at deploy time — considered; more auditable (secrets never persist as files on disk), but adds complexity to the deploy script and requires the deploy workflow to reconstruct the full environment on each run. Overkill for a single-VM personal project.
- External secrets manager (HashiCorp Vault, AWS SSM) — rejected; introduces an external operational dependency and complexity that is not justified at this scale.

---

---

### D-051 — User onboarding: invitation-only model

**Date:** 2026-04-10
**Status:** Active

**Decision:**
There is no self-registration endpoint. User accounts are created exclusively via invitation. Both Admin and GM can send invitations. An invitation email is sent to the provided address containing a system-generated temporary password. The recipient logs in with that password and is required to change it on first login.

Two distinct invitation endpoints exist, one per caller context (D-064): `POST /api/v1/invitations` (admin only) creates a user account only. `POST /api/v1/campaigns/{id}/invitations` (GM only) creates a user account AND adds the user to the campaign as `player` in a single transaction.

**Reason:**
Blue Steel is a controlled-usage platform, not a public SaaS. Open registration would bypass the admin usage gate (D-024, D-025). The invitation model keeps the admin as the hard gate while giving GMs a practical path to bring players in without involving admin in every membership change.

**Alternatives considered:**
- Admin creates users manually (no email) — rejected; requires an out-of-band password delivery step for every new user, which is worse UX with no security benefit.
- Open registration with admin approval — rejected; adds an approval queue UI that over-engineers the use case. An invitation IS the approval.

---

### D-052 — Query execution model: synchronous

**Date:** 2026-04-10
**Status:** Active

**Decision:**
Query requests (`POST /api/v1/campaigns/{id}/queries`) are handled synchronously. The server holds the HTTP connection open while it runs the full pipeline (embed → pgvector retrieval → context assembly → LLM call) and returns the complete response. Target P95 latency is <5s. A configurable server-side timeout causes the server to return `504 QUERY_TIMEOUT` if the pipeline exceeds it. Streaming (SSE) is not implemented in v1.

**Reason:**
Synchronous handling is the simplest model that satisfies the stated NFR (<5s). The query pipeline makes exactly one LLM call on a bounded context — latency is predictable and unlikely to exceed the threshold for typical campaigns. Streaming adds frontend complexity (incremental rendering, partial state handling) that is not justified until the synchronous model demonstrably fails the latency target. A server-side timeout prevents connection starvation on pathological queries.

**Alternatives considered:**
- Async with polling (same model as ingestion) — rejected; adds unnecessary polling infrastructure and client-side polling logic for a fast, single-call operation.
- SSE streaming — considered and deferred to v2. Streaming delivers better perceived UX for borderline-latency responses but adds state management complexity on both ends. Start synchronous; move to streaming if needed.

---

### D-053 — Commit payload: "add" action deferred to v2

**Date:** 2026-04-10
**Status:** Active

**Decision:**
The commit payload in v1 supports three actions per extracted item: `accept`, `edit`, `delete`. The ability for users to manually introduce entities the AI missed ("add" action) is deferred to v2.

**Reason:**
The "add" action requires a distinct frontend flow (a creation form embedded in the diff review screen), a new payload shape (client-generated temporary ID, full entity body), and backend handling to distinguish user-created entities from AI-extracted ones in the same commit. The core value proposition of v1 is AI extraction + user review and correction of extracted items. Manual addition is an enhancement, not a blocker. Shipping v1 without it keeps the commit contract simple and the review UX focused.

**Alternatives considered:**
- Include "add" in v1 — considered; deprioritized after scoping review. Users who need to add missed entities in v1 can submit a corrected session summary or use the proposal system in v2.

---

### D-054 — Draft session policy: single active draft per campaign, GM-discardable

**Date:** 2026-04-10
**Amended:** 2026-04-12
**Status:** Active

**Decision:**
At most one session per campaign may be in `processing` or `draft` state at any time. A new session submission is rejected with `409 DRAFT_IN_PROGRESS` if another session is currently in one of those states. The GM may explicitly discard a draft session (`DELETE /api/v1/campaigns/{id}/sessions/{sid}`, GM role required, only valid when status is `draft`). Discarding sets `status = 'discarded'` and clears `diff_payload`. The session row and `narrative_blocks` record are both preserved — no physical deletion occurs. `discarded` is a terminal status; a discarded session cannot be reactivated.

**Reason:**
Allowing concurrent drafts would require conflict resolution between two uncommitted world state modifications touching the same entities. The session pipeline is designed around a linear, sequential ingestion model (D-001 — world state is cumulative). Preventing concurrent drafts enforces this linearity at the API boundary. GMs need a clean path to abandon a draft that was submitted in error without having to commit incorrect data.

**Amendment rationale (2026-04-12):**
Physical deletion of the session row was originally specified alongside preservation of the `narrative_blocks` record. This creates an FK violation: `narrative_blocks.session_id` is non-nullable and references `sessions.id`. Soft deletion (status = `discarded`) resolves the constraint without sacrificing the discard capability, and is consistent with the append-only principle of D-001 — even a discarded submission is part of the campaign's record.

**Amendment rationale (2026-04-13) — enforcement mechanism:**
The original decision specified the policy (reject with 409) but not the enforcement mechanism. Application-layer checks alone are subject to a TOCTOU race: two simultaneous submissions can both read "no active draft" before either inserts, and both succeed. The constraint is enforced at the DB level via a partial unique index:

```sql
CREATE UNIQUE INDEX sessions_one_active_per_campaign
ON sessions (campaign_id)
WHERE status IN ('processing', 'draft');
```

This makes it physically impossible for two rows with the same `campaign_id` to simultaneously hold a status in the active set. A concurrent duplicate insert fails with a unique constraint violation, which the application layer catches and converts to `409 DRAFT_IN_PROGRESS`. The index is defined in the Liquibase migration for the `sessions` table.

**Alternatives considered:**
- Allow multiple concurrent drafts — rejected; would require ordering guarantees, concurrent entity resolution conflicts, and a merge step that adds significant complexity for no clear benefit.
- No discard, force commit — rejected; forces GMs to commit a session they want to abandon, permanently polluting the world state.
- Physical row deletion with nullable FK (ON DELETE SET NULL) — rejected; orphaned narrative_blocks with a null session_id lose traceability and create a second class of records with no clean query path.
- Application-layer check only (no DB index) — rejected; TOCTOU race allows two simultaneous submissions to bypass the check. DB enforcement is the correct layer for invariants that must hold absolutely.

---

### D-055 — OQ-D resolved: Pagination strategy

**Date:** 2026-04-10
**Status:** Active

**Decision:**
Two pagination strategies are used, selected by the nature of the data:

- **Offset-based pagination** (`?page=N&size=N`) for entity list endpoints (actors, spaces, events, relations, sessions). These collections are filterable, randomly accessible, and not ordered by insertion sequence in user-facing queries.
- **Keyset/cursor pagination** (`?after=<sequence_number>`) for the Timeline endpoint. The Timeline is ordered by `sequence_number` (append-only), and keyset pagination avoids page-skip anomalies as new sessions are committed between page loads.

**Reason:**
Offset pagination is simple, frontend-friendly, and correct for filterable entity lists where users jump to pages by filter criteria, not by sequential scroll. Keyset pagination is the right model for the Timeline — a strictly ordered, append-only feed where offset instability would cause events to appear or disappear between page loads as sessions are committed.

**Alternatives considered:**
- Cursor-based for everything — rejected; cursor pagination is harder to implement for filterable, randomly-accessible entity lists and offers no benefit there.
- Offset for everything — rejected; the Timeline's append-only ordered nature makes keyset pagination clearly superior. Offset skips and duplicates are especially jarring on a chronological feed.

---

### D-056 — No E2E tests; backend test pyramid top level is integration tests

**Date:** 2026-04-10
**Status:** Active

**Decision:**
There is no end-to-end test layer. The backend's highest-confidence test tier is integration tests: Spring Boot Test + Testcontainers (real PostgreSQL), full Spring context, with all LLM-backed ports mocked at the port boundary. The frontend's highest-confidence tier is component-level tests with React Testing Library.

**Reason:**
E2E tests require a full-stack environment, a test data strategy, and a reliable automation layer (Playwright or equivalent). The infrastructure and maintenance cost is not justified for a solo/small-team project where the integration test layer already exercises the full request path (HTTP → use case → domain → JPA → real DB). LLM mocking at the port boundary is consistent with the local dev strategy (D-049) and cost governance (D-034).

**Alternatives considered:**
- Playwright E2E against a test environment — rejected; requires a persistent full-stack test environment (third environment, which D-044 already rejected). Maintenance burden of E2E suites on a solo project is high relative to the confidence gain over the existing integration test layer.

---

### D-057 — Pre-Phase 1 gate: Spring Boot 4.x compatibility verification

**Date:** 2026-04-10
**Status:** Active

**Decision:**
Before writing any production code, the following dependencies must be verified as compatible with Spring Boot 4.0.3:

1. **Spring AI** — `ChatClient`, `EmbeddingModel` (pgvector retrieval uses native queries per D-062, not `VectorStore`)
2. **Testcontainers** Spring Boot integration
3. **Liquibase** Spring Boot starter
4. **Spring Security 7**

Verification means: a working proof-of-concept dependency resolution (not just version check) confirming no classpath conflicts and functional API availability. The result must be logged as a DECISIONS.md update before Phase 1 begins. Any dependency that lags must either have a confirmed roadmap date or trigger a stack reconsideration.

**Reason:**
Spring Boot 4.x is a recent major release. Spring AI in particular is an actively developing project whose API surface has changed significantly across releases. A silent incompatibility discovered mid-Phase 1 would cause costly rework. Thirty minutes of upfront verification eliminates that risk entirely. This is Martin Fowler's "known unknowns" principle: surface risks before they become blockers.

**Alternatives considered:**
- Verify as-you-go during Phase 1 — rejected; discovering a Spring AI incompatibility after building the extraction pipeline requires either downgrading Boot (breaking other dependencies) or rewriting the adapter layer. Front-loaded verification costs almost nothing.

---

---

### D-058 — OQ-6 resolved: Q&A log deferred to v2

**Date:** 2026-04-10
**Status:** Active

**Decision:**
Queries are stateless in v1 — no query history is persisted. A campaign Q&A log (history of questions asked and answers received) is a v2 feature. When shipped in v2, it will live as a history panel inside Query Mode, not as a standalone Exploration view.

**Reason:**
The core value of Query Mode in v1 is the ability to ask questions and get grounded answers. Persisting that history adds a `queries` table, a list endpoint, and a UI history component — none of which is necessary to validate the core loop. Deferring keeps v1 scope tight. The UI placement decision (history panel inside Query Mode, not a 5th Exploration view) is recorded now so the v2 design starts with a resolved anchor.

**Alternatives considered:**
- Ship Q&A log in v1 — rejected; adds schema and UI scope without changing the core value proposition. The marginal UX benefit does not justify the added complexity in v1.
- 5th Exploration view — considered for v2 design; rejected in favour of a history panel inside Query Mode. Query history is contextually tied to the act of querying, not to browsing world state.

---

### D-059 — OQ-B resolved: JWT — HS256, 15-minute access token, rotating refresh tokens

**Date:** 2026-04-10
**Status:** Active

**Decision:**
- **Algorithm:** HS256 (HMAC-SHA256, symmetric). The signing secret is stored in `.env` alongside the other production secrets (D-050).
- **Access token TTL:** 15 minutes. Short-lived; limits blast radius if a token is intercepted.
- **Refresh token TTL:** 30 days. Aligns with the weekly play cadence — users should not be forced to re-login between sessions.
- **Rotation strategy:** Rotating refresh tokens with family-based reuse detection. Each refresh call issues a new token and invalidates the previous one. If an already-used token from a family is presented, the entire family is revoked (indicates token theft). Refresh tokens are stored server-side as a hash in a `refresh_tokens` table.

**Refresh token schema:**
```
refresh_tokens
  id UUID PK
  user_id UUID FK → users.id
  token_hash TEXT NOT NULL     ← SHA-256 of the raw token; raw token never stored
  family_id UUID NOT NULL      ← groups a login session's rotation chain
  expires_at TIMESTAMP NOT NULL
  used_at TIMESTAMP            ← nullable; set when this token is exchanged for a new one
  created_at TIMESTAMP
```

Reuse detection: if a token with `used_at IS NOT NULL` is presented, all tokens sharing the same `family_id` are immediately revoked and the user must re-authenticate.

Logout (`POST /api/v1/auth/logout`) marks the current refresh token's family as fully revoked.

**Reason:**
HS256 is the natural fit for a single-server deployment — no key infrastructure, no key rotation ceremony, secret lives in `.env` with everything else. 15-minute access tokens are the industry standard for short-lived credentials; they limit exposure without user-visible impact because the refresh flow is transparent. 30-day refresh TTL matches the use case: a group playing weekly should stay logged in between sessions without friction. Rotating refresh tokens with reuse detection provide meaningful theft detection at low implementation cost — a compromised token is detectable and self-healing on next legitimate use.

**Alternatives considered:**
- RS256 — rejected; asymmetric signing is justified when multiple independent services verify tokens. A single Spring Boot monolith has no such requirement.
- 1-hour or 24-hour access tokens — considered; rejected in favour of 15 minutes because the refresh rotation is transparent and the shorter TTL costs nothing in UX.
- Static (non-rotating) refresh tokens — rejected; a stolen refresh token would be valid for 30 days with no detection mechanism. Rotation adds minimal complexity and catches theft.
- No refresh tokens — rejected; a 15-minute TTL without refresh would force re-login mid-session, which is unacceptable UX during an active RPG session night.

---

### D-060 — Auth implementation: self-implemented on Spring Security; email outsourced

**Date:** 2026-04-10
**Status:** Active

**Decision:**
Authentication and user management are self-implemented on top of Spring Security and a JWT library (`nimbus-jose-jwt`, already a Spring Security transitive dependency). No external auth service (Clerk, Auth0) is used.

Email delivery (invitation emails, future password reset) is outsourced to an external transactional email provider (Resend or Brevo, both with generous free tiers) behind a dedicated `EmailPort` in the application layer. The provider is an adapter detail — the domain never references it.

**Reason:**
The "don't roll your own auth" principle applies to cryptographic primitives, not to wiring established components together. Spring Security handles the filter chain and JWT validation. The token signing uses `nimbus-jose-jwt` (same library Spring uses internally). Our code contributes the refresh token rotation logic (~100–150 lines) and the invitation flow — neither involves custom cryptography.

External auth services (Clerk, Auth0) were evaluated and rejected for this project:
- This is a controlled-usage platform with a handful of users, not a self-serve SaaS. The primary value these services deliver (scaling user management to thousands of signups) is irrelevant here.
- Self-implementing auth behind a port is a more meaningful portfolio demonstration than delegating to a third-party SDK.
- The `EmailPort` abstraction isolates the one genuinely painful self-hosting concern (SMTP delivery) without adding vendor lock-in at the auth layer.

Email delivery is the one concern deliberately outsourced — running a reliable SMTP server is operationally non-trivial and offers no learning or portfolio value. A transactional email API behind a port is the correct boundary.

**Alternatives considered:**
- Clerk — rejected; designed for self-serve SaaS, adds hard vendor dependency, obscures auth implementation in a portfolio context.
- Auth0 — rejected; same reasons. Free tier limits (7,500 MAU) are irrelevant at this scale but the integration complexity is not.
- Spring Authorization Server — rejected; OAuth2/OIDC infrastructure is designed for multi-service token delegation scenarios. A single Spring Boot monolith serving one SPA has no such requirement.
- Self-hosted SMTP — rejected; operational overhead with no offsetting benefit. `EmailPort` keeps the option open without forcing it.

---

### D-061 — Campaign creation atomically assigns the initial GM

**Date:** 2026-04-12
**Status:** Active

**Decision:**
`POST /api/v1/campaigns` requires a `gm_user_id` field in the request body. Campaign creation and GM assignment are a single atomic operation: the campaign row and the `campaign_members` row (`role = 'gm'`) are inserted in the same transaction. `gm_user_id` is required — the request is rejected with `422` if omitted or if the referenced user does not exist. A campaign can never exist in a GM-less state.

**Reason:**
`POST /api/v1/campaigns/{id}/members` requires `gm` role to execute. If campaign creation and GM assignment were separate steps, there would be no way for admin to assign the first GM — the campaign would exist in a GM-less state with no actor able to invoke the membership endpoint. Atomic assignment at creation time eliminates this chicken-and-egg problem and enforces the invariant that every campaign always has a GM.

This is the direct implementation of D-026 (onboarding flow: admin creates campaign and assigns GM) as a single API call, which is also the more natural UX — admin thinks of campaign creation and GM assignment as one act.

**Alternatives considered:**
- Separate admin-only `PATCH /api/v1/campaigns/{id}/gm` endpoint — rejected; allows a transient GM-less state between campaign creation and GM assignment, which the system has no defined behavior for. Adds an endpoint that exists solely to work around a sequencing problem rather than solving it structurally.

---

### D-064 — Two invitation endpoints: platform-level (admin) and campaign-scoped (GM)

**Date:** 2026-04-12
**Status:** Active

**Decision:**
Invitation functionality is split across two endpoints with distinct scopes and behavior:

- `POST /api/v1/invitations` — admin only. Creates a platform user account with a temporary password. No campaign assignment.
- `POST /api/v1/campaigns/{id}/invitations` — GM only. Creates a platform user account AND adds the user to the campaign as `player` in a single transaction.

**Reason:**
The original single `POST /api/v1/invitations` endpoint had context-dependent behavior: admin callers created a user only; GM callers created a user and added them to a campaign. This violates Bloch's principle of minimal surface and clear contracts — a caller cannot predict the side effect from the endpoint alone. Splitting by scope makes the behavior self-evident from the URL: `/invitations` is platform-level, `/campaigns/{id}/invitations` is campaign-scoped. Each endpoint does exactly one thing with no hidden branching.

**Alternatives considered:**
- Single endpoint with bifurcated behavior inferred from caller role — rejected; violates Bloch's "no surprises" principle. Implicit behavior differences in a shared endpoint are a maintenance and documentation burden.
- Single endpoint with an explicit `campaign_id` parameter — rejected; makes campaign assignment optional in the same endpoint, which still creates the same branching problem with a more visible seam.

---

### D-062 — pgvector retrieval uses native queries; Spring AI `VectorStore` not used

**Date:** 2026-04-12
**Status:** Active

**Decision:**
Spring AI is used exclusively for `ChatClient` (text generation) and `EmbeddingModel` (vector generation). Spring AI's `VectorStore` abstraction is not used. All pgvector similarity searches are implemented as native PostgreSQL queries via Spring Data JPA (`@Query` with native SQL) or `JdbcTemplate`.

**Reason:**
Spring AI's `VectorStore` interface provides a generic flat similarity search (`similaritySearch(query, topK)`). The retrieval queries in Blue Steel are domain-specific and cannot be expressed through this interface:

- Entity resolution (§6.3): similarity search scoped by `campaign_id` and `entity_type`, returning entity version IDs for LLM resolution.
- Query mode context retrieval (§6.4): similarity search joining through `entity_versions → sessions`, scoped by campaign, with version-aware point-in-time semantics.

These are relational queries that happen to involve a vector similarity operator (`<=>`). Writing them as native SQL gives full control over query shape, index usage, and join conditions. Using `VectorStore` would require bypassing the abstraction for every non-trivial query, creating an inconsistent mix of abstraction and raw SQL with no benefit from the abstraction layer.

The `EmbeddingPort` abstraction (application layer) still isolates embedding generation from the domain. Swapping the embedding provider (OpenAI → other) means replacing the `EmbeddingModel` adapter only — retrieval queries are unaffected because they operate on stored vectors, not on the provider.

**Alternatives considered:**
- Spring AI `VectorStore` for all retrieval — rejected; interface is too generic to express campaign-scoped, version-aware, entity-type-filtered similarity queries. Would require raw SQL fallback for every non-trivial case, defeating the purpose of the abstraction.
- Spring AI `VectorStore` for simple cases + raw SQL for complex — rejected; two inconsistent retrieval paths with no clear boundary rule. Harder to test, harder to reason about.

---

### D-063 — Embedding generation is asynchronous post-commit

**Date:** 2026-04-12
**Status:** Active

**Decision:**
The commit endpoint (`POST .../commit`) writes world state synchronously and returns `200` immediately. Embedding generation for committed entity versions is triggered asynchronously after the commit transaction completes, using Spring `@Async` or an `ApplicationEvent`. Embeddings are inserted into `entity_embeddings` as they complete. Entity versions without a corresponding `entity_embeddings` row are excluded from Query Mode context retrieval until their embeddings are present.

**Reason:**
Synchronous embedding generation during commit has two problems. First, latency: a session with many new entities requires one OpenAI API call per entity version, each taking ~100ms — the commit endpoint would block for several seconds proportional to session size. Second, failure surface: if embedding generation fails partway through, world state has already been written but `entity_embeddings` is partially populated. Rolling back a committed session to recover from an embedding API error is disproportionate. Async generation decouples the commit correctness boundary (world state write) from the eventual consistency concern (embeddings available for retrieval). The lag between commit and query availability is typically a few seconds — acceptable given that users are unlikely to query immediately after committing a session.

No external queue infrastructure is required. Spring's `@Async` mechanism (or an `ApplicationEvent` fired after transaction commit) is sufficient for the expected load.

**Alternatives considered:**
- Synchronous commit with blocking embedding generation — rejected; unacceptable latency for sessions with many entities, and partial failure creates a data consistency problem with no clean recovery path.
- Async with two-phase session status (`embeddings_ready` flag) — considered; adds UI state and a polling/notification concern with marginal benefit. The brief unavailability of newly committed entities in Query Mode does not require a visible user-facing status. Deferred as a potential v2 enhancement if the lag proves noticeable in practice.
- External queue (Redis, SQS) — rejected; introduces operational infrastructure with no benefit at this scale. Spring `@Async` is sufficient.

---

### D-065 — Commit message format: Conventional Commits

**Date:** 2026-04-12
**Status:** Active

**Decision:**
All commits follow the [Conventional Commits](https://www.conventionalcommits.org/) specification. Format: `type(scope): description`.

Allowed types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `ci`.

Allowed scopes: `api` (backend), `web` (frontend), `db` (Liquibase migrations), `ci` (GitHub Actions), `docs` (documentation files).

Breaking changes use a `BREAKING CHANGE:` footer. Commit body is optional and explains *why*, not *what*.

**Reason:**
Conventional Commits produces a machine-readable history that enables automated changelog generation (CHANGELOG.md will be generated from commits once Phase 1 begins). The type + scope prefix makes it immediately clear which layer a change touches without reading the diff. This is especially useful in a monorepo where `api` and `web` changes are interleaved in a single history.

**Alternatives considered:**
- Imperative sentence only (no prefix) — rejected; readable but not machine-parseable, loses the layer signal that scopes provide in a two-project monorepo.
- GitHub-style with issue reference — rejected; this project is driven by ROADMAP.md phases, not a GitHub issue queue. Issue refs add noise without adding value in this workflow.

---

### D-066 — Branch naming: type/short-description

**Date:** 2026-04-12
**Status:** Active

**Decision:**
Branch names follow the pattern `type/short-description` in kebab-case, using the same type prefixes as D-065 (`feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `ci`).

Examples: `feat/session-ingestion`, `fix/jwt-refresh-rotation`, `chore/liquibase-baseline`.

**Reason:**
Mirrors the Conventional Commits type vocabulary (D-065), so the branch and its commits are self-consistent. GitHub's branch list groups by prefix, making all `feat/` branches visually adjacent. Kebab-case is the most portable branch naming convention across Git tooling.

**Alternatives considered:**
- Flat short-description only — rejected; loses the type signal, making the branch list harder to scan in the GitHub UI.
- `username/short-description` — rejected; on a solo project, user prefixes add no useful signal and clutter every branch reference.

---

---

### D-067 — Frontend form library: React Hook Form

**Date:** 2026-04-12
**Status:** Active

**Decision:**
React Hook Form (v7) is the standard form library for all forms in `apps/web`. Forms are built using shadcn/ui's `Form`, `FormField`, `FormItem`, `FormLabel`, `FormControl`, and `FormMessage` primitives, which are designed to compose directly with React Hook Form. Client-side validation errors and API-returned validation errors (`400` responses with `field` in the error envelope) are both surfaced through `useForm`'s `setError` mechanism.

**Reason:**
shadcn/ui's `Form` component is built on top of React Hook Form — it is the path of least resistance and eliminates any impedance between the component library and the form state layer. React Hook Form's uncontrolled-by-default model minimises re-renders on large forms like the diff commit payload. Its TypeScript support is first-class.

**Alternatives considered:**
- Formik — rejected; more verbose, heavier, less idiomatic with shadcn/ui, and less popular in the current React ecosystem.
- Native controlled components only — rejected; fine for single-field forms but becomes unmanageable for multi-field forms with validation and server error mapping.

---

### D-068 — Frontend package manager: npm

**Date:** 2026-04-12
**Status:** Active

**Decision:**
`npm` is the package manager for `apps/web`. `package-lock.json` is committed. No pnpm or yarn workspaces.

**Reason:**
npm ships with Node and requires zero setup. For a single-frontend project with no shared package layer (D-022), pnpm's strict dependency isolation and workspace features offer no benefit that justifies a non-default toolchain for contributors.

**Alternatives considered:**
- pnpm — rejected; faster installs and stricter hoisting are genuine advantages in large monorepos with shared packages. This project has no shared packages (D-022), so the tradeoff is all cost and no benefit.

---

### D-069 — Session `sequence_number` assigned at commit, nullable until then

**Date:** 2026-04-13  
**Status:** Active

**Decision:**  
`sessions.sequence_number` is assigned at commit time, not at submission time. The column is nullable in the DB schema. It is populated — as the next integer in the campaign's committed session sequence — atomically within the commit transaction. Sessions in `pending`, `processing`, `draft`, `failed`, or `discarded` states carry a null `sequence_number`.

The assignment rule is: `sequence_number = MAX(sequence_number) + 1` across all `committed` sessions for the same `campaign_id`, evaluated inside the commit transaction.

`sequence_number` is never exposed to clients on non-committed sessions. The status polling endpoint (§7.6, Step 2) and the draft diff endpoint do not include it in their response payloads. It appears in responses only once status = `committed`.

**Reason:**  
Assigning at submission would permanently consume a number for every failed or discarded session, producing visible gaps in the timeline sequence (sessions 1, 2, 4 — session 3 was discarded). Users would have no explanation for these gaps, and queries against `sequence_number` ranges would silently miss nothing but look wrong. Assigning at commit means the timeline is always a contiguous, gap-free sequence of committed sessions — which is the only sequence that has narrative meaning. Failed and discarded sessions are record-keeping artifacts, not narrative events, and should not occupy positions in the story sequence.

**Constraints this imposes on the implementation:**
- `sequence_number` must be defined as `INTEGER NULL` in the Liquibase migration (not `NOT NULL`).
- The `UNIQUE (campaign_id, sequence_number)` constraint applies only to non-null values — which is PostgreSQL's default behavior for unique constraints (nulls are not considered duplicates).
- The commit use case is responsible for computing and writing `sequence_number` atomically. No other code path sets it.
- No query, sort, or filter on `sequence_number` is valid for non-committed sessions.

**Alternatives considered:**  
- Assign at submission — rejected; failed and discarded sessions permanently consume sequence positions, producing unexplained gaps in the timeline sequence visible to users.
- Assign a `display_number` separately from `sequence_number` — rejected; introduces two numbering concepts with no benefit over a single number assigned at the only point it is meaningful.

---

### D-070 — Self-service password reset not implemented in v1

**Date:** 2026-04-13  
**Status:** Active

**Decision:**  
There is no self-service "forgot password" flow in v1. No `POST /api/v1/auth/password-reset` endpoint is provided. The only password change path for an authenticated user is `PATCH /api/v1/users/me/password`.

If a user loses access to their account, the recovery path is: admin re-invites the user via `POST /api/v1/invitations`, which issues a new temporary password to their email address. The user's existing account and campaign memberships are preserved — the invitation endpoint handles the case where the email already has an account by reissuing credentials only.

**Reason:**  
Blue Steel is an invitation-only platform with a small, known user base. A self-service password reset flow requires a time-limited, single-use token, a dedicated email template, a reset endpoint, and first-login redirect logic. The recovery path via admin re-invitation already satisfies the need at this scale without additional infrastructure. D-060 identified email delivery as the one externally-managed concern — this decision keeps that contract minimal in v1.

**Alternatives considered:**  
- Self-service reset via `EmailPort` — considered; the `EmailPort` abstraction makes this straightforward to add. Deferred because it adds endpoint, token lifecycle, and email template scope that is not justified by the expected frequency of password loss on a small controlled-access platform. Can be added in v2 if the user base grows or admin-mediated recovery proves operationally burdensome.

---

### D-071 — Java code style: Spotless + google-java-format

**Date:** 2026-04-13  
**Status:** Active

**Decision:**  
The backend uses the [Spotless Maven plugin](https://github.com/diffplug/spotless) with `google-java-format` to enforce a canonical Java code style. Spotless runs as part of `mvn verify` and fails the build on any formatting violation. The formatter is applied via `mvn spotless:apply` before committing. No custom style file is maintained — `google-java-format` is the canonical source of truth.

**Reason:**  
A solo portfolio project benefits from zero-configuration style enforcement: no style file to write, no rule debates, no drift. `google-java-format` is opinionated and non-negotiable, which is a feature — formatting decisions never require thought. Spotless integrates cleanly with the Maven lifecycle and can be wired into the CI `backend.yml` workflow as a pre-test check. This approach aligns with Martin (clean code, remove subjective bikeshedding) by eliminating style as a decision surface entirely.

**Alternatives considered:**  
- Checkstyle + config file — more granular control, but requires maintaining a style configuration. Adds a file to own and decisions to make. Not justified for a solo build.
- No formatter — rejected; inconsistent style in a portfolio project signals lack of discipline. Automated enforcement costs nothing once configured.

---

### D-072 — Structured logging: logstash-logback-encoder; MDC fields for LLM calls

**Date:** 2026-04-13  
**Status:** Active

**Decision:**  
The backend uses `logstash-logback-encoder` for structured JSON logging in production. Configuration lives in `logback-spring.xml` with profile-conditional appenders:

- **`local` profile:** human-readable pattern — `%d{HH:mm:ss} [%-5level] %logger{36} - %msg%n`
- **All other environments (prod):** JSON appender via `LogstashEncoder`, one JSON object per log line

MDC (Mapped Diagnostic Context) carries `session_id` and `user_id` for the duration of each request. These fields appear automatically on every log line within that request's scope. LLM call log lines at INFO additionally emit the following structured fields: `stage` (pipeline stage: `intake`, `extraction`, `resolution`, `commit`, `query`), `tokens_in` (int), `tokens_out` (int), `cost_usd` (decimal string).

**Reason:**  
`logstash-logback-encoder` is the de facto standard for structured JSON logging in the Spring Boot ecosystem. It adds one dependency and zero Logback API changes — the application code uses SLF4J as normal. MDC-based propagation of `session_id` and `user_id` means every log line within a request is automatically correlated without passing these values through every method. The cost visibility requirement (D-041 context: usage logging) is satisfied by the `cost_usd` field on every LLM call log line, enabling per-session and per-stage cost analysis from raw logs.

**Alternatives considered:**  
- Spring Boot 3.x native structured logging (`logging.structured.format=logstash`) — available in Boot 3.4+; not yet verified for Boot 4.x compatibility. `logstash-logback-encoder` is the established path and is known to work. Revisit in v2 if Boot 4.x native structured logging reaches parity.
- OpenTelemetry logging — premature for a solo portfolio project. No observability stack is in scope (no staging environment, D-044).

---

### D-073 — Admin bootstrap: `ApplicationReadyEvent` startup listener seeded from env vars

**Date:** 2026-04-14  
**Status:** Active

**Decision:**  
The singleton admin user is seeded on application startup via a Spring `@EventListener(ApplicationReadyEvent.class)` bean. It reads `ADMIN_EMAIL` and `ADMIN_PASSWORD` from environment variables, hashes the password with BCrypt, and inserts the admin user if and only if no user with `is_admin = TRUE` already exists. The listener is idempotent: subsequent restarts are no-ops. Both `ADMIN_EMAIL` and `ADMIN_PASSWORD` are required env vars; missing values fail startup with a clear error message.

**Reason:**  
The admin user must exist before any invitation can be sent or any campaign created. An `ApplicationReadyEvent` listener is the standard Spring Boot mechanism for startup side effects: it runs after the full context — including Liquibase migrations — is ready, ensuring the schema exists before the insert. Idempotency is essential: the listener must never fail on a redeployment where the admin already exists. Seeding from env vars keeps secrets out of the codebase (D-050) and makes the bootstrap reproducible without a manual step.

**Constraints this imposes on the implementation:**  
- Both `ADMIN_EMAIL` and `ADMIN_PASSWORD` must be declared in `.env.example`.
- The application layer enforces the singleton invariant before inserting: if `users WHERE is_admin = TRUE` exists, the listener logs "Admin already exists — skipping bootstrap" and returns without error.
- The DB-level partial unique index on `users WHERE is_admin = TRUE` (§5.1) is the second enforcement layer.
- The admin user has `force_password_change = FALSE` (D-077): the bootstrap password is intentionally set by the operator, not a temporary value.

**Alternatives considered:**  
- CLI command / seed script — rejected; requires a separate manual step that is easy to forget and cannot be automated in CI. A startup listener requires zero operator action.
- First-request bootstrap (lazy) — rejected; the first request to create a campaign would fail if the admin doesn't exist. Eager bootstrap on startup gives a clear error at deploy time, not at runtime.

---

### D-074 — Stuck-processing session recovery: startup transition + scheduled TTL check

**Date:** 2026-04-14  
**Status:** Active

**Decision:**  
Sessions can become permanently stuck in `processing` state if the JVM is restarted while the async pipeline is in flight. Two mechanisms address this:

1. **Startup recovery:** the same `ApplicationReadyEvent` listener (D-073) queries for all sessions with `status = 'processing'` at startup and bulk-transitions them to `failed` with `failure_reason = 'PIPELINE_INTERRUPTED'`. This handles crash-recovery.

2. **Scheduled TTL check:** a `@Scheduled` task runs every 5 minutes and transitions any session in `processing` for longer than `blue-steel.ingestion.processing-timeout-minutes` (default: 10 minutes, evaluated against `updated_at`) to `failed` with `failure_reason = 'PIPELINE_TIMEOUT'`. This handles silent hangs where the JVM is alive but the async task is blocked.

Both transitions are non-destructive: the `narrative_blocks` record is preserved. The user can re-submit the summary.

**Reason:**  
Without recovery, a server restart during ingestion leaves the campaign permanently blocked (D-054: at most one active session per campaign). A user would have no recourse — the 409 block cannot be cleared without an admin intervention that is not exposed in the API. The startup recovery eliminates the most common failure mode (deploy during ingestion). The scheduled check handles less common but equally blocking scenarios (LLM provider timeout, network partition). Both mechanisms are cheap to implement and critical for operational reliability.

**Constraints this imposes on the implementation:**  
- `blue-steel.ingestion.processing-timeout-minutes` must be declared in `application.yml` with default `10` and in `.env.example`.
- The scheduled task must be in an `adapters.in` or `application.service` class — not domain code.
- Both recovery paths must update `sessions.updated_at` and log at WARN with `session_id`.
- The `@Scheduled` task must be activated in all profiles including `local` (stuck sessions are not test-environment-specific).

**Alternatives considered:**  
- Manual admin endpoint to un-stick sessions — considered as a complement; not included in v1 because the automatic recovery covers the primary failure modes. Can be added as an ops endpoint in v2 if needed.
- Retry stuck sessions instead of failing them — rejected; partial retry is complex, and the user's review of the diff would see results from a different pipeline run than the original. Failing cleanly and requiring re-submission is simpler and safer.

---

### D-075 — `EmailPort` activation: `email-real` Spring profile, independent of `llm-real`

**Date:** 2026-04-14  
**Status:** Active

**Decision:**  
The real `EmailPort` adapter is activated by a dedicated `email-real` Spring profile, separate from `llm-real`. The mock `EmailPort` adapter (which logs email content to the console) is active on the `local` profile by default. Activating `email-real` in addition to `local` and/or `llm-real` enables real email delivery without affecting LLM behavior.

Profile combinations:
- `local` — all adapters mocked; no external calls
- `local,llm-real` — real LLM calls; email still mocked
- `local,email-real` — real email; LLM still mocked
- `local,llm-real,email-real` — all real (closest to prod behavior)

**Reason:**  
A developer testing the extraction pipeline in local dev does not want to send real emails. Conversely, a developer testing the invitation flow does not need real LLM calls. Coupling both behind a single profile forces developers to choose between incurring LLM cost when testing email, or forgoing email testing when validating the pipeline. Separate profiles give fine-grained control. This follows D-049's principle of safe defaults (local profile = all mocked) extended to the email channel.

**Constraints this imposes on the implementation:**  
- `EMAIL_API_KEY` must be in `.env.example` with a comment indicating it is only required with `email-real` profile.
- The real `EmailAdapter` is a stub in Phase 1 — it implements `EmailPort` and throws `UnsupportedOperationException("Activate email-real profile and configure EMAIL_API_KEY")` unless a provider is wired. Provider selection (Resend recommended) is a Phase 1 delivery task.
- The `MockEmailAdapter` must log the full `EmailMessage` (recipient, subject, body) at INFO level so developers can verify invitation flows locally.

**Alternatives considered:**  
- `@ConditionalOnProperty(name = "blue-steel.email.mock")` — more granular but introduces a new config key. Profiles are already the established pattern in this codebase (D-049). Consistency wins.
- Include email in `llm-real` profile — rejected; conflates two independent concerns. See reasoning above.

---

### D-076 — DiffPayload and CommitPayload are formalized JSON contracts in ARCHITECTURE.md §7.6

**Date:** 2026-04-14  
**Status:** Active

**Decision:**  
`DiffPayload` (returned by `GET .../diff`, stored in `sessions.diff_payload` JSONB) and `CommitPayload` (submitted to `POST .../commit`) are formalized as canonical JSON schemas in ARCHITECTURE.md §7.6. These schemas are the authoritative source for both the backend Java records and the frontend TypeScript types. Any change to either schema requires a simultaneous update to both the Java record and the TypeScript type — enforced as a review convention (D-077).

The field names use `snake_case` for JSON keys, consistent with the existing API convention. Java records map these via Jackson `@JsonProperty` or a global `PropertyNamingStrategies.SNAKE_CASE` config.

**Reason:**  
`DiffPayload` is a triple-boundary contract: (1) the backend serializes it, (2) it is persisted as JSONB in PostgreSQL, and (3) the frontend deserializes it. Schema drift at any of these three points produces bugs that are hard to detect because the JSONB column accepts any valid JSON and JPA maps it as a String. Without a formal, canonical definition, the backend and frontend are likely to diverge silently. Formalizing the schema in ARCHITECTURE.md makes it a first-class architectural artifact, not an implicit assumption.

**Constraints this imposes on the implementation:**  
- Backend: `DiffPayload` and `CommitPayload` are Java Records in `com.bluesteel.application.model` (not in the adapter layer). They have zero framework imports. Jackson serialization is handled by the adapter.
- Frontend: `DiffPayload` and `CommitPayload` TypeScript types in `apps/web/src/types/sessions.ts` must mirror the schema field-for-field.
- JSONB round-trip: any change to the schema requires a Liquibase migration if the shape changes in a way that makes existing stored `diff_payload` values unreadable. For v1 (no committed sessions in production yet), this is not a practical concern but must be in the review checklist for v2.

**Alternatives considered:**  
- OpenAPI code generation — deferred to v2 (D-030). Not justified for v1 given the small number of shared types.
- Separate schema file (`.json` schema) — over-engineering for v1. A clearly formatted section in ARCHITECTURE.md with explicit JSON examples serves the same purpose.

---

### D-077 — Invitation model: no invitations table; temporary password + `force_password_change` flag

**Date:** 2026-04-14  
**Status:** Active

**Decision:**  
User accounts are created directly by invitation endpoints — there is no separate `invitations` table. When an invitation is sent:
1. A cryptographically random temporary password is generated (not stored; only its BCrypt hash is stored in `users.password_hash`).
2. The password is emailed to the recipient via `EmailPort`.
3. The `users.force_password_change` column is set to `TRUE`.

The recipient logs in with the temporary password, which succeeds and returns a JWT. The login response includes `"force_password_change": true`. The frontend redirects to the change-password screen. `PATCH /api/v1/users/me/password` clears `force_password_change` on success.

**For re-invitation** (D-070 recovery path): calling `POST /api/v1/invitations` with an existing email generates a new temporary password, hashes it, updates `users.password_hash`, sets `force_password_change = TRUE`, and sends a new email. The user's account, campaign memberships, and world state data are unchanged.

**Reason:**  
An `invitations` table requires token lifecycle management (expiry, reuse detection, claimed state) that duplicates the pattern already in place for refresh tokens — without the additional security benefit. The temporary password approach is simpler: the email IS the "token" delivery channel; logging in with the temp password IS the acceptance event; the forced password change IS the acknowledgment. This is the pattern described in ARCHITECTURE.md §7.7 and is consistent with how many small, controlled-access platforms handle onboarding.

**Constraints this imposes on the implementation:**  
- `users` table requires a `force_password_change BOOLEAN NOT NULL DEFAULT FALSE` column (Liquibase changeset `0002_create_users`).
- The temporary password is generated with `SecureRandom`, minimum 16 characters, alphanumeric + symbols. It is NEVER logged or stored in raw form.
- The login response (`POST /api/v1/auth/login`) must include `"force_password_change": true|false` in the `data` object so the frontend can gate the redirect.
- `PATCH /api/v1/users/me/password` requires the authenticated user's current password as confirmation before setting the new one. On success, sets `force_password_change = FALSE`.
- `force_password_change = FALSE` for the bootstrapped admin (D-073): the operator sets the admin password intentionally via env var.

**Alternatives considered:**  
- Token-based invitation accept flow (separate `invitations` table with `token_hash`, `expires_at`, `accepted_at`) — considered; provides better audit trail and token expiry. Rejected for v1 in favour of simplicity. The `users.force_password_change` flag provides the essential first-login enforcement. Token-based flow can be introduced in v2 if the audit trail becomes important.
- Send a magic link (one-time login URL) — rejected; requires token storage and expiry enforcement, which is the complexity we are avoiding.

---

*Entries are added as decisions are made. See PRD.md and ARCHITECTURE.md for context.*
