# Blue Steel — Functionality Inventory & Use Case Manual

> Generated from a full repository audit (backend controllers/services, frontend routes/pages, `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/roadmap/ROADMAP_V1.md`, `docs/DECISIONS.md`). Every capability listed here cites the source files that implement it so the technical team can validate the inventory against the code.

---

## 1. System Overview

**Blue Steel** is an AI-assisted narrative memory system for tabletop RPG campaigns. Game Masters paste raw session summaries; an LLM pipeline extracts structured knowledge (actors, spaces, events, relations) into a versioned world state. Campaign members then query that knowledge in natural language or browse it through visual exploration views.

The application exposes **three interaction modes**, all shipped in v1:

| Mode | Purpose | Who uses it |
|---|---|---|
| **Input Mode** | Paste a session summary → review the AI-extracted diff → commit to world state | GM, Editor |
| **Query Mode** | Ask free-form questions → receive grounded answers with session citations | All campaign members |
| **Exploration Mode** | Browse world state through four visual views (Timeline, Entities, Spaces, Relations) | All campaign members |

Roadmap status: Phases 0–4 of the v1 roadmap (infrastructure, auth, campaigns, session ingestion, Query Mode, Exploration Mode) are **complete**, and v2 **Phase 5 — Proposals & Approval Pipeline** has shipped (see [proposals.md](proposals.md)). Remaining gaps are explicitly deferred v2 features (Phases 6–9) — see [Deferred & Planned Features](deferred_and_planned.md).

---

## 2. High-Level Architecture Summary

```
blue-steel/
├── apps/api/   Java 25 / Spring Boot 4 (Maven) — hexagonal architecture (ports & adapters)
├── apps/web/   React 19 / Vite / TypeScript — TanStack Query (server state) + Zustand (client state)
├── docs/       PRD, ARCHITECTURE, DECISIONS, UX_CONSTITUTION, roadmap/
└── docker-compose.yml   Local PostgreSQL 16 + pgvector
```

- **Contract:** Backend and frontend share no code; they communicate exclusively over a versioned REST API (`/api/v1/`). All IDs are UUIDs, all timestamps ISO 8601 UTC. Response envelope: `{ "data": {}, "meta": {}, "errors": [] }`.
- **Backend layering:** `domain` (pure Java, framework-free) → `application` (use-case services + ports) → `adapters` (web controllers in, persistence/AI/email out). Boundaries are enforced by ArchUnit tests.
- **Persistence:** PostgreSQL + pgvector. World-state entities use a two-table append-only versioning pattern (head table + `*_versions` history table), enabling point-in-time reconstruction. Liquibase migrations are append-only.
- **LLM integration:** Provider-agnostic ports with three Spring profiles — mock adapters (default, zero cost), `llm-real` (Google Gemini: `gemini-2.5-flash` chat + `gemini-embedding-001` 1536-dim embeddings), `llm-ollama` (local offline models, 1024-dim embeddings).
- **Email:** Mock adapter by default; Brevo transactional email under the `email-real`/`prod` profile.
- **Auth:** Stateless JWT (HS256, 15-min TTL) + rotating refresh token (30-day httpOnly cookie). The JWT carries only `user_id` and `is_admin`; campaign roles are always resolved from the database per request.

---

## 3. Actors & Roles

| Actor | Scope | Summary of permissions |
|---|---|---|
| **Anonymous User** | Platform | Login, view public system status page |
| **Admin** | Platform (singleton, DB-enforced) | Create/delete campaigns, invite platform users, search users; everything an authenticated user can do |
| **GM (Game Master)** | Campaign (exactly one per campaign, DB-enforced) | Full campaign write access: submit sessions, review/commit/discard diffs, manage members |
| **Editor** | Campaign | Submit sessions and review/commit diffs; cannot discard drafts or manage members |
| **Player** | Campaign | Read-only: Query Mode, Exploration Mode, annotations |
| **System** | Background | Async ingestion pipeline, async embedding generation, scheduled session-timeout recovery |

Onboarding is **invitation-only** (no self-registration): Admin creates a campaign and assigns its GM; the GM invites editors and players. Invited users receive a temporary password by email and must change it on first login.

---

## 4. Table of Contents — Module Inventory

| Module | File | Core capability |
|---|---|---|
| Authentication | [authentication.md](authentication.md) | Login, JWT + refresh rotation, logout, forced password change, security hardening |
| User Management | [user_management.md](user_management.md) | Admin bootstrap, platform invitations, user search, profile, password change |
| Campaign Management | [campaign_management.md](campaign_management.md) | Campaign CRUD, membership & roles, member invitations |
| Session Ingestion (Input Mode) | [session_ingestion.md](session_ingestion.md) | Summary submission, 4-stage LLM pipeline, diff review, commit, discard |
| Query Mode | [query_mode.md](query_mode.md) | Natural-language Q&A with citations, rate limiting, cost governance |
| Exploration Mode | [exploration_mode.md](exploration_mode.md) | Timeline, Entities, Spaces, Relations graph, annotations |
| Proposals & Approval Pipeline | [proposals.md](proposals.md) | Member-proposed actor/space edits → co-sign → GM approve/veto → new version (Phase 5) |
| System & Platform Services | [system_platform.md](system_platform.md) | Health, LLM/email providers, cost accounting, error contract, versioning & embeddings |
| Deferred & Planned Features | [deferred_and_planned.md](deferred_and_planned.md) | v2 register — features that exist only as schema, stubs, or decisions |

**Status legend used throughout:** ✅ Implemented · 🚧 In Progress / Planned (stub, schema-only, or explicitly deferred).

---

## 5. End-to-End System Flow (the "golden path")

1. **Admin** creates a campaign and assigns a GM ([campaign_management.md](campaign_management.md)).
2. **GM** invites editors/players; invitees log in with a temporary password and are forced to change it ([user_management.md](user_management.md), [authentication.md](authentication.md)).
3. **GM/Editor** pastes a session summary. The async pipeline extracts entities, resolves them against existing world state via vector similarity, detects narrative conflicts, and produces a reviewable diff ([session_ingestion.md](session_ingestion.md)).
4. **GM/Editor** reviews the diff (accept/edit/delete per card, resolve UNCERTAIN matches, acknowledge conflicts) and commits. World state is versioned; embeddings are generated asynchronously.
5. **Any member** asks questions in Query Mode and gets answers grounded in committed sessions, with clickable citations ([query_mode.md](query_mode.md)).
6. **Any member** browses the Timeline, entity/space profiles, and the relations graph, optionally attaching annotations ([exploration_mode.md](exploration_mode.md)).
7. **Any member** proposes a correction to an actor/space; another member co-signs; the **GM** approves (optionally editing) or vetoes, and approved changes become a new entity version ([proposals.md](proposals.md)).

---

## 6. Out of Scope (v1)

The following are intentionally **not** implemented in v1 (decision IDs from `docs/DECISIONS.md`): `add` action in the commit payload (D-053), Q&A history log (D-058), SSE streaming for queries (D-052), self-registration (D-051), self-service password reset (D-070), E2E tests (D-056), staging environment (D-044), real-time collaboration, audio/image ingestion, and a mobile app. The proposal approval pipeline (D-016) shipped in v2 Phase 5 — see [proposals.md](proposals.md). Details in [deferred_and_planned.md](deferred_and_planned.md).
