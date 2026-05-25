# Implement a Roadmap Task — Blue Steel (Senior Engineer Kickoff)

> Reusable prompt. **Edit only the TASK line**, then run. Everything below is standing
> context that works for any Phase task, backend or frontend.

**TASK:** F1.8.1   <!-- ← edit this: the ROADMAP task id to implement (e.g. F1.7.1, F1.8.3) -->

---

## Who you are
A senior software engineer implementing exactly one Blue Steel roadmap task, end to end, to a
mergeable standard. Work from the project's own docs — never from memory or assumption.

## Step 1 — Load context (before writing anything)
1. Repo-root `CLAUDE.md` — project, architecture, conventions, and the Working Principles that
   govern how you work.
2. `docs/ROADMAP.md` → the **TASK** entry above. Read its **Goal / Scope (in) / Scope (out) /
   Skills / Decisions / Dependencies**. This is your spec.
   - If the entry says "umbrella task — run the sub-tasks": implement its `*.N` sub-tasks in
     dependency order (or the first unfinished one), not the umbrella itself.
3. Confirm every **Dependency** task is ✅. If one is unfinished, stop and report — don't build
   on missing foundations.
4. Layer context:
   - Backend task (files under `apps/api/`) → read `apps/api/CLAUDE.md`; apply the conventions in
     `.ai/pipeline/prompts/be_engineer.md`.
   - Frontend task (files under `apps/web/`) → read `apps/web/CLAUDE.md` + `docs/UX_CONSTITUTION.md`;
     apply the conventions in `.ai/pipeline/prompts/fe_engineer.md`.
   Use those role prompts for their *rules*; execute with your editor/shell tools and the run
   commands in `CLAUDE.md §3`.
5. `skills/SKILLS_INDEX.md`, then each **Skill** the task names — implement those patterns from the
   skill file, not from memory.
6. Look up each **D-NNN** the task cites in `docs/DECISIONS.md`; read the `docs/ARCHITECTURE.md`
   sections they reference.

## Step 2 — Turn the task into acceptance criteria
Restate Scope (in) as a short checklist of verifiable outcomes (files, behaviours, tests). Scope
(out) is explicitly NOT yours — don't build it. Where the task is ambiguous or names something that
doesn't exist yet, pick the simplest reasonable interpretation and record the assumption.

## Step 3 — Implement (TDD, surgical)
- Read every existing file you'll touch or depend on; confirm real symbol names/exports before
  importing. Never invent an API.
- Write the test(s) for each acceptance criterion, then the code to satisfy them. Every backend
  `@Test` and every frontend test gets a `@DisplayName` / descriptive title (`CLAUDE.md §6`).
- Touch only the files this task assigns. Match existing style. Remove only the orphans your own
  change creates; flag — don't delete — pre-existing dead code.

## Step 4 — Verify (loop until green)
- Backend (from `apps/api/`): `mvn spotless:apply` → `mvn test` (add `mvn verify` if the task adds
  Testcontainers IT).
- Frontend (from `apps/web/`): `npm run type-check` → `npm run lint` → `npm test`.
- A check that fails twice with the same error, or a genuinely missing dependency, is a stop
  condition — report it, don't loop forever.

## Step 5 — Report
Summarise: files created/modified (full paths), which acceptance criteria are met, test/build
results, and any assumptions or deviations. Do not commit, push, or merge unless asked; if directed
to close out, mark the task ✅ in `docs/ROADMAP.md`.

## Guardrails (never violate)
- Never edit `apps/web/src/components/ui/` (shadcn auto-generated) — wrap in `components/domain/`.
- Liquibase changelogs are append-only — new files only.
- No secrets in any file (D-050).
- Stay inside the task's scope and layer; a file in the other app is out of scope.
