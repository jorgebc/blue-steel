# AGENTS.md — Blue Steel AI Pipeline

> Technical context and source of truth for the autonomous AI pipeline under `.ai/`.
> Audience: AI agents and engineers modifying or extending the pipeline.
> This document describes **system structure and behavior**. It does **not** define
> agent prompts or per-agent internal logic. For setup, commands, and recovery, see
> [`README.md`](README.md).

---

## 1. Overview

`.ai/` is an autonomous multi-agent development pipeline. Given a roadmap task ID
(e.g. `F1.7`), it plans, implements, and validates the change against the
application code in `apps/api` (Java/Spring Boot) and `apps/web` (React/TypeScript).

- Triggered **manually** from the command line; never runs automatically.
- Produces **plans, code changes, and review reports** — it never commits.
- Reads the project's own docs (`docs/PRD.md`, the active roadmap in `docs/roadmap/`,
  `ARCHITECTURE.md`, `DECISIONS.md`) and the task entry in the active roadmap
  (`docs/roadmap/ROADMAP_V2.md`) as its source of intent.

The pipeline itself is implemented in Python and is independent of the application
code it modifies.

---

## 2. System Architecture

The pipeline has three component types and one orchestration layer:

| Layer | Responsibility | Location |
|---|---|---|
| **Orchestrator** | Connects phases as a directed graph, persists state, routes on success/failure | `pipeline/orchestrator.py` |
| **Agents** | LLM workers that reason and act within one phase | `pipeline/agents/**` |
| **Prompts** | Per-agent persona text injected into each agent | `pipeline/prompts/*.md` |
| **Tools** | Deterministic side effects (file I/O, shell, git) callable by agents | `pipeline/tools/*.py` |

**Orchestration** uses LangGraph (`StateGraph`) with a shared `PipelineState`
TypedDict (`pipeline/state.py`) and a SQLite checkpointer for resumable runs.

**Agents** are built on `smolagents.CodeAgent` driven by `LiteLLMModel`. Model
selection is abstracted behind `pipeline/config.py` + `litellm_config.yaml`, so the
same agents run against local (Ollama) or cloud (Claude) models with no code change.

**Separation of responsibilities:** orchestration decides *what runs next*; agents
decide *how to satisfy a phase*; tools perform *all side effects*. Agents do not
write files or run commands except through tools, and the filesystem tool enforces a
repo-scoped sandbox with protected paths.

---

## 3. Directory Structure

```
run_task.py                    # repo-root CLI entry point (sets PIPELINE_MODE, loads .env)
.ai/
  README.md                    # operator guide: setup, commands, recovery
  AGENTS.md                    # this file — architecture context
  requirements.txt             # Python dependencies
  litellm_config.yaml          # model/provider routing (local vs cloud)
  context/tasks/               # generated reports per task (gitignored)
  logs/                        # per-task logs + sqlite checkpoints (gitignored)
  pipeline/
    orchestrator.py            # LangGraph StateGraph + checkpointing
    state.py                   # PipelineState (shared graph state)
    config.py                  # phase -> model resolution from litellm_config.yaml
    logger.py, callbacks.py, progress.py   # logging, console callback, timeline UI
    agents/
      planning/                # po_agent, architect_agent, planning_crew
      engineers/               # be_agent, fe_agent, execution_crew
      quality/                 # verification_agent, review_agent, secops_agent, quality_pipeline
    prompts/                   # one persona .md per agent
    tools/                     # filesystem, shell_runner, git_tools
```

Key directories:

- **`pipeline/agents/<group>/`** — agents grouped by phase. Each group has a `*_crew`
  / `*_pipeline` module that sequences its agents and writes the phase report.
- **`pipeline/prompts/`** — persona definitions, one per agent role.
- **`pipeline/tools/`** — the only modules permitted to cause side effects.
- **`context/tasks/`** — all generated reports (`*_plan.md`, `*_execution.md`, etc.);
  gitignored.
- **`logs/`** — per-task `{task_id}.log`, `{task_id}_checkpoint.db`, and
  `_phase_durations.json`; gitignored.

---

## 4. Pipeline Workflow

Full-pipeline node sequence (LangGraph): `planning → execution → quality →
final_review → done`, with an `error` node reachable from any phase.

1. **Planning** (`planning_crew`)
   - Product Owner defines scope and acceptance criteria.
   - Architect produces an initial technical proposal.
   - Product Owner challenges the proposal.
   - Architect produces the final 8-section plan → `{id}_plan.md`.
2. **Execution** (`execution_crew`) — detects backend/frontend scope from plan
   §3, runs the Backend then Frontend engineer → `{id}_execution.md`.
3. **Quality** (`quality_pipeline`) — runs in order with routers between each step:
   - **Verification** — build/test/lint/type-check; retries auto-fixes.
   - **Senior review** — code review; HIGH findings can re-trigger verification.
   - **SecOps** — security review (SAST/audit); CRITICAL/HIGH unresolved → block.
4. **Final review** — Lead-Architect coherence check: does the implementation match
   the plan and architecture? Returns `APPROVED` or `REQUIRES_CHANGES`.
5. **Done** — writes `{id}_done.md`; marks the task complete in the active roadmap
   (`docs/roadmap/ROADMAP_V2.md`) **only** when the final review is a validated `APPROVED`.

**Routing rules:**

- A failed/blocked phase routes to `error`, which writes `{id}_error.md` and stops.
- `REQUIRES_CHANGES` at final review loops back to **execution** for up to **3
  iterations**, then completes with the unresolved status recorded (ROADMAP not marked).
- Unrecoverable verification failures, blocking SecOps findings, or an engineer that
  self-reports failure stop the run (**BLOCKED** — see §8).

Phases 1, 2, and 3 are also runnable standalone (`--phase 1|2|3`) outside the graph.

---

## 5. Agents

All agents are `smolagents.CodeAgent` instances; persona text comes from
`pipeline/prompts/`. Listed by group with high-level purpose only.

**Planning — `pipeline/agents/planning/`**
- `po_agent` — Product Owner: defines scope/acceptance criteria, then challenges the architecture.
- `architect_agent` — Architect: produces and finalizes the technical implementation plan.
- `planning_crew` — sequences the PO ↔ Architect rounds and writes the plan.

**Engineers — `pipeline/agents/engineers/`**
- `be_agent` — Backend engineer for the Java/Spring Boot codebase (`apps/api`).
- `fe_agent` — Frontend engineer for the React/TypeScript codebase (`apps/web`).
- `execution_crew` — determines scope and runs the engineers in sequence.

**Quality — `pipeline/agents/quality/`**
- `verification_agent` — runs build/test/lint/type-check checks and attempts auto-fixes.
- `review_agent` — senior code reviewer producing a verdict and findings.
- `secops_agent` — security review (SAST + dependency audit) with a block verdict.
- `quality_pipeline` — sequences the three quality agents with routers.

**Orchestrator-level**
- `final_review` node — Lead-Architect coherence review; calls the LLM directly
  (not a `CodeAgent`) and returns `APPROVED` / `REQUIRES_CHANGES`.

---

## 6. Tooling

All tools live in `pipeline/tools/` and are the only modules that cause side effects.

- **`filesystem.py`** — sandboxed read/write/list scoped to the repo root. Enforces
  `PROTECTED_WRITE_PATHS`: writes to shadcn `apps/web/src/components/ui/` and
  modifications to existing Liquibase changelogs are rejected (new changesets allowed).
  Also exposes `get_modified_files` (branch diff vs. `main`).
- **`shell_runner.py`** — wraps build/quality commands as `{stdout, stderr, returncode,
  success}` results: backend tests/lint/integration, frontend tests/type-check/lint/
  npm-audit, `run_sonar_backend` (local SonarQube quality gate, filtered to
  changed files; hard-fails if `SONAR_TOKEN` or the container is missing), and
  `install_frontend_dependencies` (allowlist-scoped `npm install` used by the
  execution pre-flight for plan-declared new dependencies).
- **`agents/engineers/_checks.py`** — shared check-result capture for the engineers:
  logs every `tsc`/`eslint`/`mvn`/sonar result to the task log at DEBUG, renders the
  last failure as a concrete diagnostics block for the execution report, and runs a
  circuit breaker (`abort_step_callback`) that interrupts the agent early on an
  unrecoverable error (missing module/type) or a fix loop repeating the same failure.
- **`git_tools.py`** — branch/stage/commit helpers that are **intentionally not wired**
  into the orchestrator. The pipeline never auto-commits; these exist as a foundation
  for a possible future phase only.

---

## 7. Execution Model

- **Trigger:** `python run_task.py --task <ID> [--phase 1|2|3|all] [--mode local|cloud]
  [--resume]`. Default phase is `all`, default mode is `local`.
- **Mode selection:** `run_task.py` sets `PIPELINE_MODE`; `config.py` resolves each
  phase to a model entry in `litellm_config.yaml` (local maps phases to
  `local-reasoning`/`local-coding`; cloud uses a single `cloud-model`).
- **Full run:** the orchestrator invokes the LangGraph graph with a `SqliteSaver`
  checkpoint at `.ai/logs/{task_id}_checkpoint.db`. `--resume` reloads the last
  checkpoint and continues from the last completed node (`--phase all` only).
- **Single phase:** `--phase 1|2|3` calls the corresponding crew directly, without
  checkpointing.
- **Outputs:** markdown reports under `.ai/context/tasks/` and a per-task log under
  `.ai/logs/`. Console shows a phase timeline; full detail goes to the log file.

---

## 8. Constraints and Principles

- **Never auto-commits.** All commit authoring is manual, after a human reviews the
  generated reports.
- **Protected paths are append-only / off-limits:** shadcn-generated
  `apps/web/src/components/ui/` and existing Liquibase changelogs (enforced in
  `filesystem.py` and the engineer prompts).
- **Secrets are never committed** in any form (D-050); `SONAR_TOKEN` /
  `ANTHROPIC_API_KEY` come from `.env.local` / the environment.
- **BLOCKED means stop for human intervention** — caused by unrecoverable verification
  failures, blocking SecOps findings, an engineer self-reporting failure, or an
  unexpected exception. Recovery is documented in `README.md`.
- **ROADMAP is marked complete only on a validated `APPROVED`** final review;
  `REQUIRES_CHANGES` after max iterations leaves the task in-progress.
- **Side effects stay in tools**; orchestration routing stays deterministic.
- **New dependencies are autonomous, not gated.** The architect may introduce a library only by
  declaring it in the plan as `NEW DEPENDENCY (frontend|backend): <pkg> — <justification>`. The
  execution pre-flight installs declared frontend packages (allowlist-scoped `npm install`); the BE
  engineer adds declared backend packages to `pom.xml`. Every installed dependency is listed in the
  execution report — the pipeline never blocks on it because a human reviews all changes before commit.
  An *undeclared*, uninstalled package is a mistake and trips the engineer circuit breaker instead.
- Generated reports, logs, and checkpoints are gitignored.

---

## 9. Extension Guidelines

- **New agent:** add it under the matching `pipeline/agents/<group>/`, with a persona
  file in `pipeline/prompts/`, and wire it into that group's `*_crew` / `*_pipeline`.
- **New phase:** add a node and its conditional router edges in `orchestrator.py`, and
  add any new fields to `PipelineState` in `state.py`. Keep routers explicit about
  success vs. block.
- **New side effect:** add a function to a module in `pipeline/tools/` and call it from
  an agent — respect the filesystem sandbox and protected paths. Do not perform I/O
  directly inside orchestration or agent glue code.
- **New model / provider:** edit `litellm_config.yaml` and, if a phase needs a
  different model, `config.py`'s phase→model map. No agent code change required.
- **Principles:** keep orchestration deterministic and resumable; keep agent behavior
  in prompts; keep effects in tools; never introduce auto-commit or writes outside the
  sandbox.

---

## 10. Relationship with README.md

`README.md` and `AGENTS.md` are complementary and must not duplicate each other:

- **`README.md`** — operator-facing: prerequisites, setup, commands, generated-report
  reference, observing a run, and BLOCKED recovery.
- **`AGENTS.md`** — architecture-facing: how the system is structured and how it
  behaves, optimized for agent and engineer context.

When the pipeline's structure or behavior changes, update this document; when its
setup or commands change, update `README.md`.
