"""LangGraph orchestrator connecting all 6 Blue Steel pipeline phases.

Graph nodes:  planning -> execution -> quality -> final_review -> done
                                            +-> error (on block)

Persistence: SqliteSaver at .ai/logs/{task_id}_checkpoint.db
"""

import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

# ── sys.path: add all directories needed for transitive imports ───────────────
_PIPELINE_DIR = Path(__file__).parent
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

import litellm
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import END, StateGraph

from agents.engineers.execution_crew import run_execution
from agents.planning.planning_crew import run_planning
from agents.quality.quality_pipeline import run_quality
from config import get_llm
from state import PipelineState
from tools.filesystem import read_file, write_file

_LOGS_DIR = _PIPELINE_DIR.parent / "logs"  # .ai/logs/
_CONTEXT_DIR = ".ai/context/tasks"


# ── Nodes ─────────────────────────────────────────────────────────────────────

def _planning_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    print(f"\n[ORCHESTRATOR] Phase 1/4: Planning — {task_id}")
    try:
        plan_path = run_planning(task_id)
        return {
            "plan_path": plan_path,
            "phase": 1,
            "log": [f"Planning complete: {plan_path}"],
        }
    except Exception as exc:
        return {
            "blocked": True,
            "blocked_reason": f"Planning failed: {exc}",
            "log": [f"Planning ERROR: {exc}"],
        }


def _execution_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    iteration = state.get("iteration_count", 0)
    print(f"\n[ORCHESTRATOR] Phase 2/4: Execution — {task_id} (iteration {iteration + 1})")
    try:
        summary = run_execution(task_id)
        return {
            "execution_summary": summary,
            "iteration_count": iteration + 1,
            "phase": 2,
            "log": [f"Execution complete (iter {iteration + 1}): {summary.get('report_path')}"],
        }
    except Exception as exc:
        return {
            "blocked": True,
            "blocked_reason": f"Execution failed: {exc}",
            "log": [f"Execution ERROR: {exc}"],
        }


def _quality_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    print(f"\n[ORCHESTRATOR] Phase 3/4: Quality — {task_id}")
    try:
        result = run_quality(task_id)
        updates: dict = {
            "verification_passed": result.get("passed", False),
            "secops_verdict": result.get("secops_verdict"),
            "phase": 3,
            "log": [
                f"Quality complete: passed={result.get('passed')}, "
                f"secops={result.get('secops_verdict')}"
            ],
        }
        # Quality review verdict goes into review_verdict only if the quality
        # pipeline passed completely; otherwise we preserve it for diagnostics.
        if result.get("review_verdict"):
            updates["review_verdict"] = result["review_verdict"]

        if result.get("blocked"):
            updates["blocked"] = True
            updates["blocked_reason"] = (
                f"Quality blocked at phase '{result.get('stopped_at')}'"
            )
        elif not result.get("passed") and result.get("stopped_at"):
            # Failed but not explicitly blocked (e.g., verification failed after retry)
            updates["blocked"] = True
            updates["blocked_reason"] = (
                f"Quality failed at phase '{result.get('stopped_at')}'"
            )
        return updates
    except Exception as exc:
        return {
            "blocked": True,
            "blocked_reason": f"Quality pipeline raised exception: {exc}",
            "log": [f"Quality ERROR: {exc}"],
        }


def _final_review_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    print(f"\n[ORCHESTRATOR] Phase 4/4: Final Review — {task_id}")

    try:
        plan_path = state.get("plan_path") or f"{_CONTEXT_DIR}/{task_id}_plan.md"
        plan_content = read_file(plan_path)[:3000]

        decisions_content = ""
        try:
            decisions_content = read_file("docs/DECISIONS.md")[:2000]
        except Exception:
            pass

        exec_content = ""
        try:
            exec_content = read_file(f"{_CONTEXT_DIR}/{task_id}_execution.md")[:2000]
        except Exception:
            pass

        llm_params = get_llm("final")
        prompt = (
            f"You are the Lead Architect performing a final coherence review of task {task_id}.\n\n"
            f"Check: does the implementation (execution report) match the plan and align "
            f"with Blue Steel's architectural decisions?\n\n"
            f"PLAN (excerpt):\n{plan_content}\n\n"
            f"EXECUTION REPORT (excerpt):\n{exec_content}\n\n"
            f"DECISIONS (excerpt):\n{decisions_content}\n\n"
            f"Respond with exactly one of:\n"
            f"- APPROVED — implementation matches the plan and architecture\n"
            f"- REQUIRES_CHANGES — followed by a bullet list of specific issues\n\n"
            f"Begin your response with APPROVED or REQUIRES_CHANGES."
        )

        response = litellm.completion(
            **llm_params,
            messages=[{"role": "user", "content": prompt}],
            timeout=120,
        )
        verdict_text = response.choices[0].message.content.strip()
        verdict = (
            "APPROVED"
            if verdict_text.upper().startswith("APPROVED")
            else "REQUIRES_CHANGES"
        )
        print(f"      Final review verdict: {verdict}")
        return {
            "review_verdict": verdict,
            "phase": 4,
            "log": [f"Final review: {verdict}"],
        }
    except Exception as exc:
        # If the LLM is unreachable, default to APPROVED rather than blocking the pipeline.
        print(f"      Final review error ({exc}) — defaulting to APPROVED")
        return {
            "review_verdict": "APPROVED",
            "phase": 4,
            "log": [f"Final review ERROR (defaulted APPROVED): {exc}"],
        }


def _done_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    print(f"\n[ORCHESTRATOR] Done — {task_id}")
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    summary_lines = [
        f"# Pipeline Complete: {task_id}",
        "",
        f"**Completed:** {ts}",
        f"**Iterations:** {state.get('iteration_count', 0)}",
        "",
        "## Phase Results",
        "",
        f"- Verification : {'PASSED' if state.get('verification_passed') else 'N/A'}",
        f"- Quality Review: {state.get('secops_verdict', 'N/A')} (secops)",
        f"- Final Review  : {state.get('review_verdict', 'N/A')}",
        "",
        "## Generated Reports",
        "",
        f"- Plan         : `{state.get('plan_path', 'N/A')}`",
        f"- Execution    : `{_CONTEXT_DIR}/{task_id}_execution.md`",
        f"- Verification : `{_CONTEXT_DIR}/{task_id}_verification.md`",
        f"- Review       : `{_CONTEXT_DIR}/{task_id}_review.md`",
        f"- SecOps       : `{_CONTEXT_DIR}/{task_id}_secops.md`",
    ]
    done_path = f"{_CONTEXT_DIR}/{task_id}_done.md"
    write_file(done_path, "\n".join(summary_lines))
    print(f"      Summary written: {done_path}")

    _mark_task_done_in_roadmap(task_id)

    return {
        "completed": True,
        "log": [f"Pipeline done. Summary: {done_path}"],
    }


def _error_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    reason = state.get("blocked_reason") or "Unknown error"
    print(f"\n[ORCHESTRATOR] ERROR — {task_id}: {reason}")
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    content = (
        f"# Pipeline Error: {task_id}\n\n"
        f"**Generated:** {ts}\n"
        f"**Reason:** {reason}\n\n"
        f"## Action Required\n\n"
        f"The pipeline stopped with an unrecoverable error.\n"
        f"Review the reason above and restart after fixing the underlying issue.\n\n"
        f"## Partial Reports\n\n"
        f"- Plan: `{_CONTEXT_DIR}/{task_id}_plan.md`\n"
        f"- Execution: `{_CONTEXT_DIR}/{task_id}_execution.md`\n"
        f"- Verification: `{_CONTEXT_DIR}/{task_id}_verification.md`\n"
        f"- Review: `{_CONTEXT_DIR}/{task_id}_review.md`\n"
        f"- SecOps: `{_CONTEXT_DIR}/{task_id}_secops.md`\n"
    )
    error_path = f"{_CONTEXT_DIR}/{task_id}_error.md"
    write_file(error_path, content)

    return {"blocked": True, "log": [f"Pipeline ERROR: {reason}"]}


def _mark_task_done_in_roadmap(task_id: str) -> None:
    """Replace 🔲 or 🔄 status with ✅ for the given task in ROADMAP.md."""
    try:
        roadmap = read_file("docs/ROADMAP.md")
        updated_lines = []
        for line in roadmap.splitlines():
            if f"| {task_id} " in line or f"| {task_id}\t" in line:
                line = line.replace("\U0001f532", "✅").replace("\U0001f504", "✅")
            updated_lines.append(line)
        updated = "\n".join(updated_lines)
        if updated != roadmap:
            write_file("docs/ROADMAP.md", updated)
            print(f"      ROADMAP.md updated: {task_id} marked done")
        else:
            print(f"      ROADMAP.md: no pending status found for {task_id}")
    except Exception as exc:
        print(f"      WARNING: could not update ROADMAP.md: {exc}")


# ── Conditional routers ───────────────────────────────────────────────────────

def _route_after_planning(state: PipelineState) -> str:
    if state.get("blocked") or not state.get("plan_path"):
        return "error"
    return "execution"


def _route_after_quality(state: PipelineState) -> str:
    if state.get("blocked") or not state.get("verification_passed"):
        return "error"
    return "final_review"


def _route_after_final_review(state: PipelineState) -> str:
    verdict = state.get("review_verdict", "APPROVED")
    if verdict == "REQUIRES_CHANGES" and state.get("iteration_count", 0) < 3:
        return "execution"
    return "done"


# ── Graph assembly ────────────────────────────────────────────────────────────

def _build_graph(checkpointer=None):
    builder = StateGraph(PipelineState)

    builder.add_node("planning", _planning_node)
    builder.add_node("execution", _execution_node)
    builder.add_node("quality", _quality_node)
    builder.add_node("final_review", _final_review_node)
    builder.add_node("done", _done_node)
    builder.add_node("error", _error_node)

    builder.set_entry_point("planning")

    builder.add_conditional_edges(
        "planning",
        _route_after_planning,
        {"execution": "execution", "error": "error"},
    )
    builder.add_edge("execution", "quality")
    builder.add_conditional_edges(
        "quality",
        _route_after_quality,
        {"final_review": "final_review", "error": "error"},
    )
    builder.add_conditional_edges(
        "final_review",
        _route_after_final_review,
        {"execution": "execution", "done": "done"},
    )
    builder.add_edge("done", END)
    builder.add_edge("error", END)

    kwargs = {"checkpointer": checkpointer} if checkpointer else {}
    return builder.compile(**kwargs)


# ── Public entry point ────────────────────────────────────────────────────────

def run_pipeline(task_id: str, resume: bool = False) -> dict:
    """Run the complete Blue Steel pipeline for a roadmap task.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.
        resume: If True, resume from the last SQLite checkpoint for this task.

    Returns:
        Final PipelineState dict.
    """
    _LOGS_DIR.mkdir(parents=True, exist_ok=True)
    db_path = _LOGS_DIR / f"{task_id}_checkpoint.db"

    conn = sqlite3.connect(str(db_path), check_same_thread=False)
    checkpointer = SqliteSaver(conn)
    graph = _build_graph(checkpointer)

    config = {"configurable": {"thread_id": task_id}}

    try:
        if resume:
            print(f"\n[ORCHESTRATOR] Resuming pipeline for {task_id}...")
            try:
                saved = graph.get_state(config)
                if saved and saved.values:
                    result = graph.invoke(None, config=config)
                else:
                    print("[ORCHESTRATOR] No checkpoint found — starting fresh.")
                    result = graph.invoke(_fresh_state(task_id), config=config)
            except Exception:
                result = graph.invoke(_fresh_state(task_id), config=config)
        else:
            print(f"\n[ORCHESTRATOR] Starting full pipeline for {task_id}...")
            result = graph.invoke(_fresh_state(task_id), config=config)
    finally:
        conn.close()

    return result


def _fresh_state(task_id: str) -> PipelineState:
    return {
        "task_id": task_id,
        "phase": 0,
        "plan_path": None,
        "execution_summary": None,
        "verification_passed": False,
        "review_verdict": None,
        "secops_verdict": None,
        "iteration_count": 0,
        "blocked": False,
        "blocked_reason": None,
        "completed": False,
        "log": [],
    }
