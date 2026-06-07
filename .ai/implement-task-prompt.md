# Implement a Roadmap Task — Blue Steel (Senior Engineer Kickoff)

> Reusable prompt. **Edit only the TASK line**, then run. Everything below is standing
> context that works for any Phase task, backend or frontend.

**TASK:** F3.2 and all subtasks

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
7. Create a new branch

## Step 2 — Turn the task into acceptance criteria
Restate Scope (in) as a short checklist of verifiable outcomes (files, behaviours, tests). Scope
(out) is explicitly NOT yours — don't build it. Where the task is ambiguous or names something that
doesn't exist yet, pick the simplest reasonable interpretation and record the assumption.

## Step 2.5 — Respect the deployment resource budget (Render free tier)

Production runs on a **Render free-tier web service (~512 MB RAM, sleeps after 15 min idle)** with a
**Neon serverless PostgreSQL** database (limited connections). Memory is the binding constraint.
Design every feature so capacity is a *parameter*, never a rewrite:

- **Push heavy work to the database, not the JVM.** Vector search, aggregation, filtering, sorting
  belong in native SQL on Neon (ARCH-04, D-062) — the JVM should receive only bounded result sets.
- **Bound anything that scales with data.** Any in-memory collection whose size grows with rows,
  tokens, batch size, context window, top-N, or fetch size must have an explicit limit.
- **Make every such limit an env-overridable property**, conservatively defaulted, following the
  existing convention in `application.properties`: `some.knob=${SOME_KNOB:default}` read via
  `@Value` (or `@ConfigurationProperties`). Precedents: `query.retrieval.top-n`,
  `blue-steel.resolution.top-n`, `blue-steel.conflict.context-top-n`, `blue-steel.llm.*-max-tokens`,
  HikariCP `maximum-pool-size=3` ("sized for Render free tier").
- **The goal:** when more memory becomes available, capacity rises by changing an env var in the
  Render dashboard (restart only) — **never** by re-implementing the feature. State the property +
  env var name and its default in your Step 5 report.

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

## Step 6 — Version bump (only when a ROADMAP phase milestone completes)
Per-task work does **not** bump the version. When the task you finished completes a whole ROADMAP
**phase** (e.g. the last epic of Phase N), bump the single repo-wide SemVer version (**D-090**):
- Set **both** `apps/web/package.json` `version` and `apps/api/pom.xml` `<version>` to the **same**
  plain `MAJOR.MINOR.PATCH` (no `-SNAPSHOT`); they must always be equal and match the latest `v*`
  tag on `main`.
- Pre-1.0: `0.x` minors track phase milestones in **completion order** (build order D-094) —
  `0.1.0` Phase 1 · `0.2.0` Phase 2 · `0.3.0` Phase 4 (Exploration) · `0.4.0` Phase 3 (Query);
  `1.0.0` = v1 (Input + Query + Exploration) complete.
- Commit the bump on a branch as a `chore:` commit. Do **not** create or push the annotated
  `vX.Y.Z` tag yourself — tagging is a release action on `main` after merge; flag it for the human.
- If unsure whether the task closes a phase, ask — don't bump speculatively.

> **Committing multi-line messages:** in the Bash tool, write the message to a temp file and
> `git commit -F <file>` — never `git commit -m` with a multi-line string, and never the PowerShell
> `@'…'@` here-string (it leaves a stray `@` in the subject).

## Guardrails (never violate)
- Never edit `apps/web/src/components/ui/` (shadcn auto-generated) — wrap in `components/domain/`.
- Liquibase changelogs are append-only — new files only.
- No secrets in any file (D-050).
- Stay inside the task's scope and layer; a file in the other app is out of scope.
- Memory budget: data-scaled in-memory collections must be bounded by an env-overridable property
  (Render free tier ~512 MB) — see Step 2.5.
