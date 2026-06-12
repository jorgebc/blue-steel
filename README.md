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

**Current phase:** Phase 1 — active development.

Definition & Analysis is complete. Architecture decisions are finalized and documented. Phase 1 development is underway.

---

## Local development

### Prerequisites

| Tool | Minimum version |
|---|---|
| JDK | 25 |
| Maven | 3.9 |
| Node.js | 20 |
| npm | bundled with Node |
| Docker **or** Podman | any recent version |

### Start the full stack

**1. Start the database** (from repo root)

```bash
docker compose up -d        # or: podman compose up -d
```

Starts PostgreSQL 16 + pgvector on `localhost:5432`.
Verify: `docker ps` shows a running `pgvector/pgvector:pg16` container.

**2. Start the backend** (from `apps/api/`)

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

The `local` profile ships with hardcoded safe defaults — no `.env` file needed:

| Setting | Local value |
|---|---|
| Database URL | `jdbc:postgresql://localhost:5432/bluesteel` |
| JWT secret | pre-set dev secret (≥ 32 bytes) |
| Admin email | `admin@local.dev` |
| Admin password | `Admin!Local123456` |
| Email | logged to console (mock — no provider key needed) |

Verify: `GET http://localhost:8080/api/v1/health` → `{"data":{"status":"UP","db":"UP"},...}`

**3. Configure the frontend env** (from `apps/web/`, once)

```bash
cp apps/web/.env.example apps/web/.env.local
```

`.env.example` ships with `VITE_API_BASE_URL=http://localhost:8080` — correct for local dev.
Edit `.env.local` only if your backend runs on a different port.

**4. Start the frontend** (from `apps/web/`)

```bash
cd apps/web
npm install
npm run dev
```

Frontend starts on `http://localhost:5173`.

### Manual test scenarios

| URL | Expected behaviour |
|---|---|
| `/status` | Health page loads without login. Shows `API: UP` / `Database: UP`. |
| `/login` | Login form renders. Invalid credentials show an error banner (no toast). |
| `/login` with `admin@local.dev` / `Admin!Local123456` | Redirects to `/` on success. |
| `/` (no session) | Redirects to `/login`. |
| `/change-password` (no session) | Redirects to `/login`. |
| Hard-refresh on any route | Stays on that route (SPA routing intact). |

### Optional: Real LLM profiles

```bash
# Google Gemini — chat + embeddings (requires GEMINI_API_KEY in env)
mvn spring-boot:run "-Dspring-boot.run.profiles=local,llm-real"

# Local Ollama — offline, zero cost (requires Ollama running on localhost)
mvn spring-boot:run "-Dspring-boot.run.profiles=local,llm-ollama"
```

With `llm-ollama`, set `EMBEDDING_DIMENSION=1024` and recreate the DB to match the 1024-dim
embedding column: `docker compose down -v && docker compose up -d`

---

## Documentation

All project decisions and specifications live in `/docs`:

| Document | Purpose |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Product requirements — what and why |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Stack, structure, patterns, conventions |
| [`docs/roadmap/`](docs/roadmap/README.md) | Roadmap index + per-version roadmaps (phases, functional blocks, status) |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Decision log — every significant technical and product choice |
| [`CLAUDE.md`](CLAUDE.md) | Conventions and context for AI-assisted development |

---

## Principles

This project is built with a clear engineering philosophy:

- **SOLID** principles and clean code (Robert C. Martin)
- **Hexagonal architecture** — Ports & Adapters (Alistair Cockburn)
- **API design** done right (Joshua Bloch)
- **TDD** from the start (Kent Beck)
- **Evolutionary architecture** — design for change (Martin Fowler)

---

## Security

SBOMs (Software Bill of Materials) are generated automatically on every push to `main` using [CycloneDX](https://cyclonedx.org/) and scanned for known vulnerabilities with [Trivy](https://github.com/aquasecurity/trivy).

| What | When |
|---|---|
| Vulnerability scan | Every push to `main` and every Monday at 08:00 UTC |
| Manual trigger | GitHub Actions → **vulnerability-scan** → Run workflow |
| Results | GitHub Security tab → **Code scanning** |

**Scan categories:**
- `sbom-frontend` — React/npm dependency scan (`apps/web`)
- `sbom-backend` — Java/Maven dependency scan (`apps/api`)

SBOM JSON files are uploaded as workflow artifacts for download and audit. CVE exceptions are managed in [`.trivyignore`](.trivyignore) at the repository root.

---

## License

MIT
