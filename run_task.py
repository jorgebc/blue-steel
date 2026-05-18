#!/usr/bin/env python3
"""Entry point for the Blue Steel multi-agent AI development pipeline.

Usage examples:
  python run_task.py --task F1.7 --phase 1          # planning only
  python run_task.py --task F1.7 --phase 2          # execution only
  python run_task.py --task F1.7 --phase 3          # quality only
  python run_task.py --task F1.7 --phase all        # full pipeline
  python run_task.py --task F1.7 --phase all --resume
  python run_task.py --task F1.7 --phase all --mode cloud
"""

import os
import sys
import time
from pathlib import Path

import click

# ── sys.path: make the pipeline package importable ────────────────────────────
_REPO_ROOT = Path(__file__).parent
_PIPELINE_DIR = _REPO_ROOT / ".ai" / "pipeline"
_PLANNING_DIR = _PIPELINE_DIR / "agents" / "planning"
_ENGINEERS_DIR = _PIPELINE_DIR / "agents" / "engineers"
_QUALITY_DIR = _PIPELINE_DIR / "agents" / "quality"

for _p in [
    str(_PIPELINE_DIR),
    str(_PLANNING_DIR),
    str(_ENGINEERS_DIR),
    str(_QUALITY_DIR),
]:
    if _p not in sys.path:
        sys.path.insert(0, _p)


# ── Helpers ───────────────────────────────────────────────────────────────────

_CONTEXT_DIR = ".ai/context/tasks"

_REPORT_LABELS = [
    ("Plan",         "{}_plan.md"),
    ("Execution",    "{}_execution.md"),
    ("Verification", "{}_verification.md"),
    ("Review",       "{}_review.md"),
    ("SecOps",       "{}_secops.md"),
    ("Done summary", "{}_done.md"),
    ("Error",        "{}_error.md"),
]


def _phase_banner(label: str) -> None:
    width = 60
    click.echo("\n" + "=" * width)
    click.echo(f"  {label}")
    click.echo("=" * width)


def _print_report_table(task_id: str) -> None:
    click.echo("\n--- Generated reports ---")
    found_any = False
    for label, pattern in _REPORT_LABELS:
        path = Path(f"{_CONTEXT_DIR}/{pattern.format(task_id)}")
        if path.exists():
            click.echo(f"  {label:<14} {path}")
            found_any = True
    if not found_any:
        click.echo("  (none)")


def _overall_verdict(task_id: str, result: dict) -> str:
    if result.get("completed"):
        return "DONE"
    if result.get("blocked"):
        return f"BLOCKED — {result.get('blocked_reason', 'see error report')}"
    return "INCOMPLETE"


# ── Individual phase runners ──────────────────────────────────────────────────

def _run_phase_1(task_id: str) -> None:
    """Planning phase: PO + Architect conversation -> _plan.md."""
    from agents.planning.planning_crew import run_planning

    _phase_banner(f"Phase 1 — Planning  ({task_id})")
    t0 = time.time()
    plan_path = run_planning(task_id)
    elapsed = time.time() - t0
    click.echo(f"\nPhase 1 complete in {elapsed:.1f}s")
    click.echo(f"Plan: {plan_path}")
    _print_report_table(task_id)


def _run_phase_2(task_id: str) -> None:
    """Execution phase: BE + FE engineer agents -> _execution.md."""
    from agents.engineers.execution_crew import run_execution

    _phase_banner(f"Phase 2 — Execution  ({task_id})")
    t0 = time.time()
    summary = run_execution(task_id)
    elapsed = time.time() - t0
    click.echo(f"\nPhase 2 complete in {elapsed:.1f}s")
    click.echo(f"Report: {summary.get('report_path')}")
    _print_report_table(task_id)


def _run_phase_3(task_id: str) -> None:
    """Quality phase: verification + review + secops -> three reports."""
    from agents.quality.quality_pipeline import run_quality

    _phase_banner(f"Phase 3 — Quality  ({task_id})")
    t0 = time.time()
    result = run_quality(task_id)
    elapsed = time.time() - t0
    click.echo(f"\nPhase 3 complete in {elapsed:.1f}s")
    click.echo(f"  Verification : {result.get('verification_verdict')}")
    click.echo(f"  Review       : {result.get('review_verdict')}")
    click.echo(f"  SecOps       : {result.get('secops_verdict')}")
    click.echo(f"  Passed       : {result.get('passed')}")
    _print_report_table(task_id)


def _run_full_pipeline(task_id: str, resume: bool) -> None:
    """Full pipeline: all phases connected by LangGraph."""
    from orchestrator import run_pipeline

    _phase_banner(f"Full Pipeline  ({task_id}){' [RESUME]' if resume else ''}")
    t0 = time.time()
    result = run_pipeline(task_id, resume=resume)
    elapsed = time.time() - t0

    click.echo(f"\nPipeline finished in {elapsed:.1f}s")
    click.echo(f"Verdict: {_overall_verdict(task_id, result)}")

    if result.get("log"):
        click.echo("\n--- Execution log ---")
        for entry in result["log"]:
            click.echo(f"  {entry}")

    _print_report_table(task_id)


# ── CLI ───────────────────────────────────────────────────────────────────────

@click.command()
@click.option("--task", required=True, help="Task ID to run (e.g. F1.7, F2.3)")
@click.option(
    "--mode",
    default="local",
    show_default=True,
    type=click.Choice(["cloud", "local"]),
    help="LLM mode: cloud (Claude via Anthropic) or local (Ollama)",
)
@click.option(
    "--phase",
    default="all",
    show_default=True,
    help="Pipeline phase: 1=planning, 2=execution, 3=quality, all=full pipeline",
)
@click.option(
    "--resume",
    is_flag=True,
    default=False,
    help="Resume an interrupted full-pipeline run from last checkpoint",
)
def main(task: str, mode: str, phase: str, resume: bool) -> None:
    """Run a Blue Steel AI pipeline task."""
    # Propagate mode to all pipeline modules via env var
    os.environ["PIPELINE_MODE"] = mode

    click.echo(f"Task:   {task}")
    click.echo(f"Mode:   {mode}")
    click.echo(f"Phase:  {phase}")
    if resume:
        click.echo("Resume: enabled")

    if phase == "1":
        _run_phase_1(task)
    elif phase == "2":
        _run_phase_2(task)
    elif phase == "3":
        _run_phase_3(task)
    elif phase == "all":
        _run_full_pipeline(task, resume=resume)
    else:
        click.echo(
            f"Unknown phase '{phase}'. Use 1, 2, 3, or all.",
            err=True,
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
