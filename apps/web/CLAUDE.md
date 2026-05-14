# CLAUDE.md — Frontend (`apps/web`)

> Read repo-root `CLAUDE.md` first. This file covers frontend-only concerns.
> For any non-trivial task, also check `skills/SKILLS_INDEX.md`.

---

## 1. Stack

| Concern | Choice |
|---|---|
| Language | TypeScript (latest stable) |
| Framework | React 18 |
| Build | Vite |
| Components | shadcn/ui (primitives in `components/ui/` — **never edit manually**) |
| Server state | TanStack Query v5 |
| Client state | Zustand v5 |
| Routing | React Router v6 |
| Graph | React Flow v12 (`@xyflow/react`, NOT `reactflow`) |
| Forms | React Hook Form v7 |
| HTTP | Fetch API + hand-written typed client (`src/api/client.ts`) |
| Testing | Vitest + React Testing Library + axe-core (`vitest-axe`) |
| Linting | ESLint + Prettier |

---

## 2. Directory Structure

```
apps/web/src/
├── api/               ← typed HTTP client; one file per API resource
├── components/
│   ├── ui/            ← AUTO-GENERATED (shadcn/ui) — read-only
│   └── domain/        ← shared domain-aware components (used across ≥2 features)
├── features/
│   ├── input/         ← Input Mode: session submission + diff review + commit
│   ├── query/         ← Query Mode: question form + answer + citations
│   └── exploration/   ← Exploration Mode
│       ├── timeline/  ← keyset-paginated event feed
│       ├── entities/  ← offset-paginated actor list + profiles
│       ├── spaces/    ← offset-paginated space list + profiles
│       └── relations/ ← React Flow graph (@xyflow/react)
├── store/             ← Zustand: authStore, campaignStore, UI flags
├── hooks/             ← shared custom hooks
├── types/             ← TypeScript interfaces mirroring backend DTOs (hand-maintained)
└── main.tsx
```

---

## 3. Run Commands

```bash
# From apps/web/
npm install                              # local dev install (CI uses npm ci)
npm run dev                             # dev server

npm audit --audit-level=high --production  # dependency vulnerability check
npm run type-check                      # TypeScript (tsc --noEmit)
npm run lint                            # ESLint
npx prettier --write src/              # auto-format (not in CI — keep code clean locally)
npm test                                # Vitest in CI mode (same as npx vitest run)
npm run build                           # production build
```

**CI step order** (mirrors `frontend.yml`): `npm audit → type-check → lint → test → build`

---

## 4. Architecture Rules

**State boundaries (never cross):**
- Server-fetched data → TanStack Query cache. Never put it in Zustand.
- Auth token, active campaign context, UI flags → Zustand. Never fetch data outside TanStack Query hooks.

**Auth token:**
- JWT access token lives in memory (Zustand `authStore`) — **never `localStorage`**.
- Refresh tokens are `httpOnly` cookies. Frontend never reads them.
- On `401`: attempt one silent refresh (`POST /auth/refresh` with `credentials: 'include'`), retry once. Redirect to login if that also returns `401`.

**Campaign role:**
- NOT in the JWT. Comes from campaign membership API response. Stored in `campaignStore.currentUserRole`.
- Always derive role from `useCampaignStore(s => s.currentUserRole)`, never from token.

**API client:** `src/api/client.ts` wraps `fetch` with auth headers and silent refresh logic. Components never call `fetch` directly — they use hooks from `src/api/`.

**Types:** `src/types/` are hand-maintained mirrors of backend DTOs. When a backend DTO changes, update the corresponding type in the same PR. TypeScript compiler is the primary drift detector.

---

## 5. Domain Concepts & Rules

**Diff Review (Input Mode):**
- `DiffPayload` has 4 card types: `NEW` (full profile), `EXISTING` (delta only), `UNCERTAIN` (resolution required), `ConflictCard` (non-blocking, must acknowledge)
- **UNCERTAIN cards block commit** — commit button disabled until all resolved. Backend enforces `422 UNCERTAIN_ENTITIES_PRESENT` as defence in depth (D-042).
- `CommitPayload` uses `card_decisions` + `uncertain_resolutions` + `acknowledged_conflicts` — match ARCHITECTURE.md §7.6 exactly.
- No `add` action in v1 (D-053). Do not render an "Add entity" affordance.

**Session ingestion polling:**
- `POST /sessions` returns `{ sessionId, status: 'processing' }`. Must poll `GET .../status` until `draft` or `failed`.
- `400 SUMMARY_TOO_LARGE` — show `max_tokens` field and suggest splitting summary.
- Draft recovery: if `status === 'draft'` session exists, offer "Resume" before allowing new submission (D-054, returns `409` if ignored).

**Exploration Mode:**
- Read-only for world state. No entity edit endpoints from any exploration view (D-010).
- Annotations: any member can post; immutable after creation; author or GM can delete. No edit button.
- `"Propose a change"` button: render as **disabled stub** with tooltip in v1 (D-012).
- Timeline → `useInfiniteQuery` (keyset/cursor pagination, D-055). Entity lists → `useQuery` with page param (offset pagination).

**Relations Graph:**
- Import from `@xyflow/react`, NOT `reactflow`. CSS: `@xyflow/react/dist/style.css`.
- `nodeTypes` must be defined outside the component or memoised with `useMemo`.
- No world state edits from graph interactions.

**Query Mode:**
- Synchronous — no streaming (D-052). `504 QUERY_TIMEOUT` → user-friendly message with rephrasing suggestion.
- Stateless — no Q&A history (D-058). Use component `useState` for the answer; do not cache in TanStack Query.
- Citations must render as navigable links to session detail.
- Never render answer with `dangerouslySetInnerHTML` — LLM output is plain text.

**Role gating:** Input Mode actions (upload, review, commit) visible only to `gm` and `editor`. `player` sees Query Mode + Exploration only.

---

## 6. Key Conventions

| Rule | Detail |
|---|---|
| Never edit `components/ui/` | Wrap in `components/domain/` instead |
| Feature-scoped until shared | Only move to `components/domain/` or `hooks/` when used by ≥2 features |
| All IDs are UUIDs (`string`) | Don't convert; parse timestamps at render boundary only |
| Forms use React Hook Form + shadcn Form primitives | Map API `400` `field` errors via `setError` |
| Accessibility is not optional | axe-core assertion on every component in `components/domain/` and every feature-level page |

---

## 7. Relevant Skills

- **`frontend-api-resource`** — typed API client files, TanStack Query hooks, DTO types, auth token handling
- **`frontend-diff-review`** — diff review screen: card types, UNCERTAIN resolution, commit button, payload assembly
- **`frontend-exploration`** — Timeline (keyset), Entities/Spaces (offset), Relations graph (React Flow v12), annotations
- **`frontend-query-mode`** — Query Mode UI: question form, mutation hook, answer display, citation rendering, 504 handling
- **`frontend-testing`** — Vitest setup, React Testing Library, axe-core assertions, hook isolation, fetch mocking
- **`auth`** — in-memory token storage, silent refresh, route guards, role derivation from campaign membership
- **`react-hook-form`** — React Hook Form v7 + shadcn Form primitives, API error mapping to form fields
