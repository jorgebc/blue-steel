# Roadmap Decomposition — Prompt & Protocol

## Why this exists

The autonomous pipeline (`.ai/`) runs a **local 14B coding model** (`qwen2.5-coder:14b`)
as its engineer. That model produces good code only when a task is **small, single-concern,
and grounded in symbols that already exist**. The roadmap's tasks are far too big for it —
e.g. F1.7 is ~13 interdependent files and assumes shadcn/ui primitives that were never
generated, so the model hallucinates APIs (`@shadcn/ui`, default `zustand` import, wrong
paths) and the run fails.

Fix the inputs, not the model: **decompose each remaining roadmap task into small
sub-tasks**, and **pull deterministic boilerplate out into one-time human SETUP steps** so
the model only writes small, novel code against real, existing symbols.

This file is the operator guide. Run it **one batch per Claude session** (see §Batches).

---

## The prompt (paste into a Claude session, fill in the batch)

> **Task:** Decompose the following Blue Steel roadmap tasks into small, pipeline-runnable
> sub-tasks, editing the active roadmap (`docs/roadmap/ROADMAP_V2.md` — see
> `docs/roadmap/README.md`) in place: **{FILL IN — e.g. F5.1, F5.2}**.
>
> **Read first (do not skip):**
> 1. The target task sections and their phase summary table in the active roadmap
>    (`docs/roadmap/ROADMAP_V2.md`).
> 2. Both layer guides: `apps/api/CLAUDE.md` (backend) and `apps/web/CLAUDE.md` (frontend).
> 3. **For any frontend/UI task, `docs/UX_CONSTITUTION.md` (D-087) is mandatory** — it *changes the
>    decomposition*: feedback is an `InlineBanner` (no toasts, D-083), loading is a skeleton (no
>    spinners, D-086), contextual actions use `FocusedOverlay` (no modals, D-082). Each is its own
>    `components/domain/` sub-task — never folded into a page or skipped.
> 4. The `skills/` files and the `docs/DECISIONS.md` D-numbers each target task cites.
> 5. The **current repo state** — list the relevant `apps/api/src` / `apps/web/src` dirs and
>    read existing files — so every `Dependencies` line is accurate and you never assume a
>    file exists that doesn't (or re-create one that does). **For frontend tasks, also read the
>    actual backend controllers/DTOs the FE must mirror, and embed the verified request/response
>    shapes in SETUP.** Don't assume a response carries fields it doesn't — e.g. `POST /auth/login`
>    returns only `{ accessToken, forcePasswordChange }`, so `currentUser` comes from a follow-up
>    `GET /users/me`, not the login response.
>
> **Sizing rule (the whole point):**
> - Each sub-task = **1–3 source files plus its test**, **one concern**, and must
>   type-check / compile **on its own** given its declared dependencies.
> - **Never** span `apps/api` and `apps/web` in one sub-task (backend and frontend are
>   separate sub-tasks).
> - If a unit needs more than ~3 files or mixes concerns, split it further.
> - Aim for **3–6 sub-tasks per parent task**, but **smaller is better than bigger** and UI-heavy
>   walking-skeleton tasks legitimately exceed it (F1.7 → 10 once the UX `components/domain/`
>   primitives are split out). Never force a merge just to hit the range.
>
> **Separate deterministic scaffolding into human SETUP (do NOT make it a pipeline sub-task):**
> - Anything a CLI/template does deterministically — `npm create vite`, `tsconfig`/Vite
>   config, `npx shadcn@latest init`, `shadcn add <component>…`, `npm install <pkg>…`,
>   Liquibase baseline wiring, etc. — goes into a single **`{ID}-SETUP`** checklist that a
>   **human runs by hand** before the sub-tasks.
> - Sub-tasks then **assume SETUP is done** and import the **real** generated symbols
>   (e.g. `@/components/ui/button`), **never** a hallucinated package like `@shadcn/ui`.
> - The SETUP block is a checklist of exact commands, not prose.
> - **SETUP must reflect the ACTUAL repo state** (check `package.json` / existing files first). If
>   Vite + deps are already scaffolded, do NOT re-run `npm create vite` / `npm install <pkg>` for
>   them — SETUP then covers only the *missing wiring*: the `@/` path alias (tsconfig `paths` + Vite
>   `resolve.alias`), Tailwind v4 (`@tailwindcss/vite` plugin + CSS `@theme` block — no
>   `tailwind.config.js`), `shadcn init`/`add`, and the Vitest setup file (`test.setupFiles`).
>
> **Order by dependency:** sequence sub-tasks topologically (e.g. types → store → client →
> guard → pages → wiring). Each sub-task's `Dependencies` lists the **earlier sub-task IDs**
> (and any prior roadmap task) it builds on, so each run sees already-written, real symbols.
>
> **Output format — MUST match the pipeline parser exactly:**
> The pipeline reads the active roadmap (`docs/roadmap/ROADMAP_V2.md`), finds a task by its
> `#### <id>` heading, and reads
> its one-line description from the phase summary table row `| <id> | desc | status |`. So
> for **every** sub-task you must add **both**:
> 1. a summary-table row: `| F1.7.1 | <one-line description> | 🔲 |`
> 2. a section in this exact shape:
>    ```
>    #### F1.7.1 — <Title>
>
>    **Goal:** <1–2 sentences.>
>
>    **Scope (in):**
>    - <exact file path 1>
>    - <exact file path 2 (+ its test)>
>
>    **Scope (out):** <what is explicitly deferred to a sibling/later sub-task.>
>
>    **Skills:** <skill names>  **Decisions:** <D-numbers>  **Dependencies:** F1.7-SETUP, <earlier ids>
>    ```
>
> **Edit protocol for the active roadmap file:**
> - Convert each parent `#### Fx.y — Title` into an **umbrella** heading: keep it, keep it
>   **before** its sub-tasks, and add a line `> **Umbrella task — run the Fx.y.N sub-tasks
>   below, not this.**`. Move its original Scope into the sub-tasks.
> - Insert the `{ID}-SETUP` block, then the ordered `#### Fx.y.N` sections, beneath the umbrella.
> - In the phase **Summary** table, keep the parent row and add the sub-task rows under it
>   (a `Fx.y-SETUP` row too, marked as a human step).
> - **Never** modify ✅ done tasks. Preserve all `D-…` citations.
>
> **Before you finish, self-check:**
> - Every sub-task names **exact file paths**, is **single-layer**, and is **independently
>   testable**.
> - Sub-tasks are **dependency-ordered** and each `Dependencies` line is real.
> - **SETUP covers every tool, config, package, and UI primitive** the sub-tasks import —
>   no sub-task imports something that neither SETUP nor an earlier sub-task produced.
> - You did not leave a parent task runnable as a single giant unit.

---

## Batches (one Claude session each)

Decompose in this order; keep each session to one batch so context stays focused.

| Session | Batch | Tasks | Status |
|---|---|---|---|
| A | Phase 1 remainder | F1.7, F1.8, F1.9 | F1.7 ☑ · F1.8 ☐ · F1.9 ☐ |
| B | Phase 2 — ingestion core | F2.1, F2.2, F2.3, F2.4 | ☐ |
| C | Phase 2 — resolution → commit | F2.5, F2.6, F2.7, F2.8 | ☐ |
| D | Phase 2 — Input Mode frontend | F2.9, F2.10, F2.11 | ☐ |

(Phases 3–4 are still coarse one-line blocks; expand + decompose them in a later round.)

After each session, mark it ☑ and sanity-check that the new sub-tasks parse (see
§Verification).

---

## Worked example — F1.7 (template + granularity benchmark)

This is the *target shape*. Your generated sections must be this small and this concrete.

**`F1.7-SETUP` (human, run once — NOT a pipeline task).** `apps/web` is already Vite-scaffolded with
its deps in `package.json`, so SETUP is only the *missing wiring* (do NOT re-run `npm create vite`):
```
cd apps/web && npm install
# 1. '@/' alias:  tsconfig.app.json "paths":{"@/*":["./src/*"]}  +  vite.config.ts resolve.alias
# 2. Tailwind v4: npm i -D @tailwindcss/vite ; add tailwindcss() to vite plugins (no tailwind.config.js)
# 3. npx shadcn@latest init  &&  npx shadcn@latest add button input label form card
# 4. Blue Steel @theme block from docs/UX_CONSTITUTION.md §8 → merge into src/index.css
# 5. src/test/setup.ts: jest-dom + vitest-axe matcher registration (see Notes) ; set test.setupFiles
```

| Sub-task | Files (≈) | Depends on |
|---|---|---|
| **F1.7.1** API/auth/health types — `ApiEnvelope<T>`, `AuthLoginResponse`, `UserMeResponse`, `HealthResponse` | `src/types/*.ts` | SETUP |
| **F1.7.2** Zustand `authStore` + `campaignStore` | `src/store/*.ts` + tests | F1.7.1 |
| **F1.7.3** HTTP client with 401 silent-refresh | `src/api/client.ts` + test | F1.7.1, F1.7.2 |
| **F1.7.4** auth + user API hooks (incl. `getCurrentUser` after login) | `src/api/{auth,users}.ts` + tests | F1.7.1, F1.7.3 |
| **F1.7.5** `RequireAuth` guard (+ force-password redirect) | `src/components/domain/RequireAuth.tsx` + test | F1.7.2 |
| **F1.7.6** `InlineBanner` — no-toast feedback (UX §5) | `src/components/domain/InlineBanner.tsx` + test | SETUP |
| **F1.7.7** `LoginPage` (RHF + shadcn Form) | `src/features/auth/LoginPage.tsx` + test | F1.7.1, F1.7.2, F1.7.4, F1.7.6 |
| **F1.7.8** `ChangePasswordPage` (RHF) | `src/features/auth/ChangePasswordPage.tsx` + test | F1.7.2, F1.7.4, F1.7.6 |
| **F1.7.9** `StatusPage` health round-trip (skeleton loading) | `src/api/health.ts`, `src/features/status/StatusPage.tsx` + test | F1.7.1, F1.7.3, F1.7.6 |
| **F1.7.10** `main.tsx` router+providers + `vercel.json` + README env doc | `src/main.tsx`, `vercel.json` | F1.7.2, F1.7.5, F1.7.7, F1.7.8, F1.7.9 |

Notes that prevent the known failures:
- `import { create } from 'zustand'` (named, not default).
- shadcn primitives come from `@/components/ui/*` (created by SETUP) — never `@shadcn/ui`.
- Type every callback param; remove unused locals (tsc runs strict).
- **`package.json` is authoritative over the layer guide's stack text** — this repo runs React 19 /
  `react-router-dom` v7 / Tailwind v4 / Vitest v4 (CLAUDE.md still says "React 18 / Router v6").
- **vitest-axe matcher registration is two parts** in `src/test/setup.ts`: `import * as m from
  'vitest-axe/matchers'; expect.extend(m)` registers it at RUNTIME; `import 'vitest-axe/extend-expect'`
  is **types-only (a runtime no-op)**. It is `vitest-axe`, not `jest-axe`.
- UX primitives are real sub-tasks, not afterthoughts: `InlineBanner` (no toast), skeleton (no
  spinner), `FocusedOverlay` (no modal) — build in `components/domain/`, then import in pages.

---

## Verification (run after Session A, the first real use)

From `.ai/pipeline`, confirm the pipeline can extract a sub-task and that the umbrella still
resolves without swallowing a sub-task:

```bash
python -c "import sys; sys.path.insert(0,'agents/planning'); import planning_crew as p; \
r=p.read_file(p._DOCS['roadmap']); \
print('desc:', repr(p._get_task_description('F1.7.1', r))); \
print('section head:', p._extract_task_from_roadmap('F1.7.1', r)[:200])"
```

Expect a clean one-line description and the `#### F1.7.1 …` section. Then optionally
dry-run planning for one small unit and eyeball that the plan is small and references the
SETUP-provided primitives:

```bash
python run_task.py --task F1.7.1 --phase 1
```
