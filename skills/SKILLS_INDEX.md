# Blue Steel — Skills Index

> Read this file first when starting any non-trivial task. Each skill covers a distinct,
> recurring workflow. Pick the one that matches your task before touching any code.
>
> Skills are grounded in the actual project decisions (DECISIONS.md) and conventions
> (ARCHITECTURE.md, CLAUDE.md). When a skill contradicts something you think you know,
> the skill is likely more current — re-read the relevant doc.

---

## Skill Directory

| Skill | File | Use when... |
|---|---|---|
| **backend-endpoint** | `backend-endpoint/SKILL.md` | Adding or modifying any REST endpoint end-to-end (controller → port → service → adapter) |
| **backend-domain-model** | `backend-domain-model/SKILL.md` | Creating or modifying a domain entity, including the world state versioning pattern (actor/space/event/relation) |
| **backend-testing** | `backend-testing/SKILL.md` | Writing or running any backend test — domain unit, application unit, Testcontainers integration, ArchUnit, or PITest |
| **session-ingestion-pipeline** | `session-ingestion-pipeline/SKILL.md` | Working on the extraction → entity resolution → conflict detection → diff → commit pipeline |
| **query-pipeline** | `query-pipeline/SKILL.md` | Working on the Query Mode pipeline: embed → vector search → context assembly → LLM answer → citations |
| **frontend-api-resource** | `frontend-api-resource/SKILL.md` | Adding a typed API client file, TanStack Query hooks, DTO types, or connecting any component to a backend endpoint |
| **frontend-diff-review** | `frontend-diff-review/SKILL.md` | Building or modifying the diff review screen: card types, UNCERTAIN resolution, commit button, payload assembly |
| **frontend-exploration** | `frontend-exploration/SKILL.md` | Building any Exploration Mode view: Timeline (keyset), Entities/Spaces (offset), Relations graph (React Flow) |
| **database-migration** | `database-migration/SKILL.md` | Adding or modifying any database schema — Liquibase changeset creation, pgvector columns, Neon branch validation |
| **ci-cd** | `ci-cd/SKILL.md` | Modifying GitHub Actions workflows, Docker builds, deployment configuration, or secret management |

---

## Quick-Reference by Task

**"I need to add a new API endpoint"**
→ `backend-endpoint` (primary), `backend-testing` (for tests), `database-migration` (if new tables needed)

**"I need to add a new domain entity"**
→ `backend-domain-model` (primary), `database-migration` (for schema), `backend-testing` (for tests), `backend-endpoint` (to expose it via REST)

**"I need to modify the extraction/ingestion pipeline"**
→ `session-ingestion-pipeline` (primary), `backend-testing`

**"I need to modify Query Mode"**
→ `query-pipeline` (primary), `backend-testing`

**"I need to connect a frontend component to the backend"**
→ `frontend-api-resource` (primary)

**"I need to build the diff review screen or a new card type"**
→ `frontend-diff-review` (primary), `frontend-api-resource`

**"I need to add a view in Exploration Mode"**
→ `frontend-exploration` (primary), `frontend-api-resource`

**"I need to write a test"**
→ `backend-testing` (backend), `frontend-api-resource` §5 (frontend hook tests)

**"I need to change the database schema"**
→ `database-migration` (primary), `backend-domain-model` (if entity-level change)

**"CI is broken / I need to update the CI pipeline"**
→ `ci-cd`

---

## Architecture Invariants (never skip)

These rules apply to all skills. Any skill that appears to contradict them is wrong — the invariant wins.

1. **Domain core has zero framework imports.** `com.bluesteel.domain` never imports `org.springframework.*` or `jakarta.persistence.*`. Enforced by ArchUnit on every build.
2. **pgvector queries are native SQL.** Spring AI `VectorStore` is never used. All similarity searches are `@Query(nativeQuery=true)` or `JdbcTemplate`.
3. **No LLM calls without bounded context.** Every LLM call has a token budget check. The extraction pipeline makes at most 3 bounded LLM calls per session.
4. **No auto-commit.** World state is never modified without explicit user confirmation via the diff review flow.
5. **UNCERTAIN entities block commit.** The commit button is disabled until all UNCERTAIN cards are resolved. The backend also enforces this with `422`.
6. **Liquibase changelogs are append-only.** Never modify an applied changeset.
7. **Secrets are never committed.** `.env` and `.env.local` are always gitignored.
8. **Campaign role is not in the JWT.** Always resolved from `campaign_members` via DB.
9. **`components/ui/` is auto-generated.** Never edit it manually — wrap in `components/domain/`.
10. **Server state in TanStack Query; client state in Zustand.** Never put API-fetched data in Zustand.

---

## Decision Cross-Reference

If you encounter a `D-NNN` reference in a skill or in code, look it up in `DECISIONS.md`.
The most commonly referenced decisions:

| Decision | Topic |
|---|---|
| D-001 | World state is cumulative (append-only) |
| D-002 | User confirmation required before commit |
| D-003 | Query responses must cite specific sessions |
| D-021 | All domain entities carry `owner_id` and `campaign_id` |
| D-033 | Conflict detection is non-blocking (warning cards) |
| D-034 | LLM cost governance (bounded pipeline + spend cap) |
| D-041 | Entity resolution is two-stage (pgvector + LLM) |
| D-042 | UNCERTAIN entities must be resolved before commit |
| D-043 | JWT carries only `user_id`; campaign role is DB-resolved |
| D-049 | Local dev uses mock LLM adapters (zero API cost) |
| D-052 | Query Mode is synchronous; 504 on timeout |
| D-054 | Single active draft per campaign |
| D-055 | Offset pagination for entity lists; keyset for Timeline |
| D-062 | pgvector uses native SQL; Spring AI VectorStore not used |
| D-063 | Embedding generation is async post-commit |
