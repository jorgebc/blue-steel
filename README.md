# Blue Steel

> An AI-assisted narrative memory system for tabletop RPG campaigns.

---

## What it is

Tabletop RPG campaigns are long-running, complex narratives. Characters evolve, alliances shift, locations are revealed, and secrets come to light — across months or years of play. Keeping track of it all is hard. Most groups don't.

**Blue Steel** is an external brain for your campaign. It ingests session summaries, extracts structured knowledge, and makes everything queryable — so nothing gets forgotten and the story stays coherent.

---

## What it does

**Input:** Upload a session summary after each play session. The system extracts actors, events, locations, and relationships. You review, correct if needed, and commit. The world state updates.

**Query:** Ask natural language questions about your campaign. *"What does Aldric know about the Conclave?"* *"Who was at the battle of Thornwall?"* Every answer is sourced back to a specific session.

**Explore:** Browse the world visually across four interconnected views — a **Timeline** of events, **Entity** profiles, **Spaces** and locations, and a **Relations** graph of connections between actors. Annotate anything. Nothing you see here is invented.

---

## Project status

**Current phase:** Definition & Analysis — no code yet.

We are defining what to build, the technology stack, and the architecture before writing a single line of code.

---

## Documentation

All project decisions and specifications live in `/docs`:

| Document | Purpose |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Product requirements — what and why |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Stack, structure, patterns, conventions |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phases, functional blocks, status *(in progress — complete before development starts)* |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Decision log — every significant technical and product choice |
| [`docs/CLAUDE.md`](docs/CLAUDE.md) | Principles and conventions for AI-assisted development *(in progress — complete before development starts)* |

---

## Principles

This project is built with a clear engineering philosophy:

- **SOLID** principles and clean code (Robert C. Martin)
- **Hexagonal architecture** — Ports & Adapters (Alistair Cockburn)
- **API design** done right (Joshua Bloch)
- **TDD** from the start (Kent Beck)
- **Evolutionary architecture** — design for change (Martin Fowler)

---

## License

MIT
