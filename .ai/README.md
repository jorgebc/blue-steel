# Blue Steel AI Pipeline

Multi-agent development pipeline that autonomously implements roadmap tasks.
Phases: Planning (PO + Architect) -> Execution (BE + FE engineers) -> Quality (verification + review + secops) -> Final Review -> Done.

> This README is the operator guide (setup, commands, recovery). For the system
> architecture and how the pipeline is structured, see [`AGENTS.md`](AGENTS.md).

---

## Prerequisites

- **Python 3.10 or later** required (the pipeline uses PEP 604 `X | Y` union syntax;
  Python 3.9 will fail with a cryptic `SyntaxError`).
- **Ollama** running locally for `--mode local` (the default). Pull the models the
  pipeline expects before the first run:

  ```bash
  ollama pull qwen3:14b           # local-reasoning model (PO, architect, review)
  ollama pull qwen2.5-coder:14b   # local-coding model (BE/FE engineers)
  ```

  Roughly **16 GB of RAM** is needed to run both models concurrently. If you only
  have a single model loaded, expect Ollama to swap weights between phases, which
  adds 20–60 s per swap.

- **Podman (or Docker)** — required by the SonarQube quality gate (below) and by the
  optional integration-test check (`PIPELINE_RUN_INTEGRATION=true`).

- **SonarQube `sonarqube-local` container** — required for any task with **backend
  scope**. The BE engineer phase runs a Sonar scan via `run_sonar_backend`, which
  **fails the phase if `SONAR_TOKEN` is unset or the container is not running**.
  Front-end-only tasks do not need it. One-time setup (see root `CLAUDE.md` §6 for
  the canonical version):

  ```bash
  podman start sonarqube-local          # server at http://localhost:9000, project key blue-steel-api
  # SonarQube UI → My Account → Security → Generate Token, then export it (see below)
  ```

  The backend `pom.xml` must include `sonar-maven-plugin` (already configured).

## Setup

```bash
# From the repo root:
pip install -r .ai/requirements.txt
```

**Environment variables.** On startup `run_task.py` auto-loads `.env.local` (then
`.env`) from the repo root, so the simplest path is to drop your secrets there
(both files are gitignored — D-050). Variables already set in your shell take
precedence, so you can also export them instead:

```bash
# .env.local at the repo root
SONAR_TOKEN=<token>          # backend tasks
ANTHROPIC_API_KEY=<key>      # --mode cloud only
```

```powershell
# …or export into the current shell (PowerShell)
$env:SONAR_TOKEN = "<token>"
$env:ANTHROPIC_API_KEY = "<key>"
```

| Var | When needed |
|---|---|
| `SONAR_TOKEN` | Any **backend** task (Sonar gate in BE engineer phase). Never commit (D-050). |
| `ANTHROPIC_API_KEY` | `--mode cloud` only |

For local mode (default) no LLM API keys are required — Ollama must be running. A
backend task still needs `SONAR_TOKEN` + the `sonarqube-local` container in either mode.

---

## Switching between cloud and local LLMs

Edit `.ai/litellm_config.yaml` — no code changes needed.

```yaml
# Use Claude (cloud):
- model_name: cloud-model
  litellm_params:
    model: anthropic/claude-sonnet-4-5
    api_key: os.environ/ANTHROPIC_API_KEY

# Use local Ollama models:
- model_name: local-reasoning
  litellm_params:
    model: ollama/qwen3:14b
    api_base: http://localhost:11434
```

Pass `--mode local` (default) or `--mode cloud` to `run_task.py`.

---

## Common commands

```bash
# Plan task F1.7 using local Ollama:
python run_task.py --task F1.7 --phase 1

# Execute (requires plan to exist):
python run_task.py --task F1.7 --phase 2

# Quality check only:
python run_task.py --task F1.7 --phase 3

# Full pipeline with local models:
python run_task.py --task F1.7 --phase all

# Full pipeline with Claude (cloud):
python run_task.py --task F1.7 --phase all --mode cloud

# Resume an interrupted full pipeline run:
python run_task.py --task F1.7 --phase all --resume

# Other active roadmap tasks:
python run_task.py --task F1.8 --phase all
python run_task.py --task F1.9 --phase all
python run_task.py --task F2.1 --phase all
```

---

## Generated reports

All reports land in `.ai/context/tasks/` and are gitignored.

| File | Phase | Content |
|---|---|---|
| `{id}_plan.md` | Planning | 8-section implementation plan |
| `{id}_execution.md` | Execution | BE + FE file lists, build results |
| `{id}_verification.md` | Quality | Build, test, lint, type-check results |
| `{id}_review.md` | Quality | Senior reviewer findings |
| `{id}_secops.md` | Quality | Security findings (OWASP, SAST) |
| `{id}_done.md` | Done | Pipeline summary + all report links |
| `{id}_error.md` | Error | Failure reason + recovery instructions |
| `{id}_blocker.md` | Quality | Created when pipeline is blocked |
| `SETUP_NOTES.md` | Quality | Cumulative log of missing-tool gaps discovered during verification runs (shared across tasks) |

Checkpoint databases are stored in `.ai/logs/` (also gitignored).

## Observing a run

The **console** shows a phase-by-phase timeline only — a start banner, a live spinner
with elapsed/estimated time while the phase works, and a timed done-line. Prompts, LLM
results and agent reasoning are intentionally kept off the console.

The full detail goes to a per-task log file:

```bash
.ai/logs/{task_id}.log        # prompts, agent outputs, router decisions, full tracebacks
                              # (ANSI-stripped, secrets scrubbed — tail it while the run works)
.ai/logs/_phase_durations.json # learned per-phase durations; drives the console time estimates
```

Errors always surface on the console in red (`[FAIL]`/`[BLOCKED]`); their full
traceback is written to the log file. When stdout is redirected (CI / piped), the
spinner and colors are disabled automatically and only the timeline lines are emitted.

---

## Understanding BLOCKED

**BLOCKED** means the pipeline stopped and requires human intervention.
This happens when:

- Verification finds build or test failures that cannot be auto-fixed after retries
- SecOps finds CRITICAL or HIGH unresolved security findings
- An unexpected exception stops a phase

**How to recover:**

1. Open the `{task_id}_blocker.md` or `{task_id}_error.md` report
2. Fix the identified issue manually (code, test, dependency, config)
3. Resume: `python run_task.py --task {id} --phase all --resume`
   - Or restart from the failing phase: `--phase 2` or `--phase 3`

---

## Protected paths — agents must never modify

The following paths are treated as append-only or auto-generated:

- `apps/web/src/components/ui/` — auto-generated by shadcn/ui; never edit manually
- `apps/api/src/main/resources/db/changelog/` — Liquibase migrations; existing files must never be modified (append new files only)

These constraints are enforced in the BE and FE engineer agent prompts.

---

## Pipeline architecture

```
run_task.py
  |
  +--phase 1--> planning_crew.py   (PO round 1 -> Architect round 1 -> PO round 2 -> Architect round 2)
  +--phase 2--> execution_crew.py  (be_agent + fe_agent in sequence)
  +--phase 3--> quality_pipeline.py (verification_agent -> review_agent -> secops_agent)
  +--phase all-> orchestrator.py   (LangGraph StateGraph, SqliteSaver checkpoints)
```

Checkpoints are stored per task at `.ai/logs/{task_id}_checkpoint.db`.
The `--resume` flag loads the last checkpoint and continues from the last completed node.

---

## Manual completion steps

If the pipeline cannot complete (Ollama timeout, missing API key, model error):

1. Run `--phase 1` to verify the plan is generated
2. Inspect `.ai/context/tasks/{task_id}_plan.md`
3. If plan looks good, run `--phase 2` for execution
4. Run `--phase 3` for quality checks
5. Review each report in `.ai/context/tasks/`
6. Commit the implemented changes manually following Blue Steel's commit convention:
   ```
   git add <files>
   git commit -m "feat(api): implement {task description}"
   ```
