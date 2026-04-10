# ROADMAP — Blue Steel

**Status:** Draft v0.1 — skeleton, to be completed before development starts
**Phase:** Definition & Analysis
**Last updated:** 2026-04-10

> ⚠️ This document must be completed before any Phase 1 work begins. The functional blocks and their sequencing are the primary output still required to close the Definition & Analysis phase.

---

## Phases

### Phase 0 — Pre-Development Validation (Gate)

**Purpose:** Eliminate the highest-risk unknown before writing production code.

**Required before Phase 1 can begin:**

- [ ] Verify Spring Boot 4.0.3 compatibility for: Spring AI (`ChatClient`, `EmbeddingModel`, `VectorStore`), Testcontainers Spring Boot integration, Liquibase Spring Boot starter, Spring Security 7 (D-057)
- [ ] Log compatibility verification result in DECISIONS.md
- [ ] Resolve OQ-B (JWT algorithm, token expiry, refresh token strategy) — add DECISIONS.md entry
- [ ] Resolve OQ-6 (Q&A log — v1 or v2?) — add to PRD.md scope and DECISIONS.md if v1
- [ ] Create CLAUDE.md operational section (build commands, paths, test commands)

**Gate:** All items above must be checked before any functional block in Phase 1 is started.

---

### Phase 1 — Core Infrastructure

> To be defined. Suggested blocks (not final, not ordered):
>
> - Repository scaffold (monorepo, CI/CD pipelines, Docker Compose local dev)
> - Database schema baseline (Liquibase migrations for core tables)
> - Auth: user invitation model, login, JWT (D-051, D-043)
> - Campaign + membership management
> - Role enforcement at application layer

---

### Phase 2 — Session Ingestion Pipeline

> To be defined. Suggested blocks (not final, not ordered):
>
> - Session submission and status model
> - Knowledge extraction (LLM call 1)
> - Entity resolution (two-stage pgvector + LLM, D-041)
> - Conflict detection (D-033)
> - Diff generation and structured diff API
> - Diff review + commit (UI + backend)
> - World state versioning (D-035)
> - Embedding generation on commit (D-040)

---

### Phase 3 — Query Mode

> To be defined. Suggested blocks (not final, not ordered):
>
> - Query endpoint (synchronous, D-052)
> - pgvector similarity retrieval for query context
> - QueryAnsweringPort + LLM call
> - Citation grounding in response
> - Query Mode UI

---

### Phase 4 — Exploration Mode

> To be defined. Suggested blocks (not final, not ordered):
>
> - Timeline view + API
> - Entities view + API
> - Spaces view + API
> - Relations graph (React Flow, D-030)
> - Annotations (D-011)
> - "Propose a change" affordance (visible, pipeline inactive — D-012)

---

### v2 — Proposal & Approval Pipeline

> To be defined after v1 ships. Key items:
>
> - Player proposal submission UI
> - Co-sign flow (D-017)
> - GM approval / veto (D-018)
> - Proposal expiry TTL enforcement (D-019)
> - Commit payload "add" action (D-053)
> - Query streaming / SSE (D-052)

---

## Status Legend

| Symbol | Meaning |
|---|---|
| 🔲 | Not started |
| 🔄 | In progress |
| ✅ | Done |
| ⛔ | Blocked |

---

*This document is the primary output still required to close the Definition & Analysis phase. Functional blocks, sequencing, and status tracking are to be filled in before Phase 1 begins.*
