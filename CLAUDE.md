# CLAUDE.md — Blue Steel

> ⚠️ The **Operational** section of this document (build commands, paths, test commands) must be filled in before Phase 1 begins. The Principles section is stable.

---

## Part 1 — Principles & Conventions

These are fixed decisions made during the Definition & Analysis phase. They govern every implementation choice.

### Engineering Philosophy

| Reference | Principle applied |
|---|---|
| **Robert C. Martin** | SOLID, clean code, single responsibility at every layer |
| **Alistair Cockburn** | Hexagonal architecture — the domain is isolated from all infrastructure |
| **Joshua Bloch** | API design done right — minimal surface, clear contracts, no surprises |
| **Kent Beck** | TDD from day one — no production code without a failing test first |
| **Martin Fowler** | Evolutionary architecture — design for change, not for today's assumptions |

These are not aspirations. They are constraints.

---

### Architecture Rules (non-negotiable)

- The domain core (`com.bluesteel.domain`) has zero imports from `org.springframework.*`, `jakarta.persistence.*`, or any infrastructure library — enforced by ArchUnit on every build
- Ports are Java interfaces in `application.port.in` and `application.port.out` — the domain has no knowledge of ports
- Adapters implement ports and are never called directly by domain code
- JPA entities live exclusively in `adapters.out.persistence` — they are a persistence detail, not the domain model
- Configuration classes are co-located with the adapter they configure (D-039)

---

### Domain Model Conventions

- Domain entities are plain Java classes — no JPA annotations, no Spring annotations
- Value objects are Java Records — immutable by construction
- Aggregates enforce their own invariants — no anemic domain model
- Checked exceptions are not used in domain or application layers — domain uses unchecked domain exceptions
- All public API methods are documented; internal implementation methods are not

---

### Key Decisions Reference

A summary of the most operationally-relevant decisions. Full rationale in DECISIONS.md.

| Area | Decision | Reference |
|---|---|---|
| Backend stack | Java 25 + Spring Boot 4.0.3 + Maven | D-027, D-028 |
| Frontend stack | React 18 + Vite + TypeScript + shadcn/ui | D-030 |
| Database | PostgreSQL + pgvector on Neon (free tier) | D-031, D-047 |
| LLM provider | Anthropic (Claude) via Spring AI | D-032 |
| Embedding model | OpenAI `text-embedding-3-small`, 1536 dimensions | D-040 |
| Auth | Stateless JWT | D-043 |
| Migrations | Liquibase | D-029 |
| Environments | `local` (Docker Compose) + `prod` only | D-044 |
| Local LLM dev | Mock ports by default; `llm-real` profile for real APIs | D-049 |
| Testing (backend) | JUnit 5 + Mockito + Testcontainers + PITest + ArchUnit | D-036, D-037, D-056 |
| Testing (frontend) | Vitest + React Testing Library + axe-core | See ARCHITECTURE.md §9.6 |
| Secrets | `.env` on Oracle VM, never committed | D-050 |

---

## Part 2 — Operational (to be completed before Phase 1)

> ⚠️ Fill in this section when the repository scaffold is in place.

### Repository Layout

```
blue-steel/
├── docs/           ← all project documentation
├── apps/
│   ├── api/        ← Java 25 / Spring Boot backend
│   └── web/        ← React / Vite / TypeScript frontend
├── CLAUDE.md       ← repo root; picked up automatically by tooling
├── README.md
└── .gitignore
```

### Backend (`apps/api`)

```bash
# Build
# TODO: fill in

# Run tests (unit + integration)
# TODO: fill in

# Run mutation tests (domain core)
# TODO: fill in

# Run architecture tests
# TODO: fill in (ArchUnit runs with unit tests)

# Start locally (Docker Compose)
# TODO: fill in

# Start with real LLM APIs
# TODO: fill in (llm-real Spring profile)
```

### Frontend (`apps/web`)

```bash
# Install dependencies
# TODO: fill in

# Type check
# TODO: fill in

# Lint
# TODO: fill in

# Run tests
# TODO: fill in

# Build
# TODO: fill in

# Start dev server
# TODO: fill in
```

### Environment Variables

Production secrets are stored in a `.env` file on the Oracle VM (D-050). Local development uses a `.env.local` file (not committed).

Required variables (full list to be finalized when scaffold is built):

```
DATABASE_URL=
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
JWT_SECRET=
```

---

*Principles section is stable and final. Operational section is populated during Phase 1 scaffold setup.*
