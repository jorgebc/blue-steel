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
from callbacks import PipelineConsoleCallback
from config import get_llm, get_model_options
from logger import MARKER_FAIL, MARKER_INFO, MARKER_OK, get_logger
from state import PipelineState
from tools.filesystem import read_file, write_file

_LOGS_DIR = _PIPELINE_DIR.parent / "logs"  # .ai/logs/
_CONTEXT_DIR = ".ai/context/tasks"


# ── Nodes ─────────────────────────────────────────────────────────────────────

def _planning_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    logger = get_logger(task_id)
    logger.info(f"{MARKER_INFO} Starting planning phase", extra={"role": "planning"})
    try:
        plan_path = run_planning(task_id)  # run_planning already logs "[OK] Plan written: ..."
        return {
            "plan_path": plan_path,
            "phase": 1,
            "log": [f"Planning complete: {plan_path}"],
        }
    except Exception as exc:
        logger.error(
            f"{MARKER_FAIL} Planning failed: {type(exc).__name__}: {exc}",
            extra={"role": "planning"},
            exc_info=True,
        )
        return {
            "blocked": True,
            "blocked_reason": f"Planning failed: {exc}",
            "log": [f"Planning ERROR: {exc}"],
        }


def _execution_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    iteration = state.get("iteration_count", 0)
    logger = get_logger(task_id)
    logger.info(
        f"{MARKER_INFO} Execution iteration {iteration + 1}",
        extra={"role": "execution"},
    )
    try:
        summary = run_execution(task_id)
        logger.info(
            f"{MARKER_OK} Execution complete: {summary.get('report_path')}",
            extra={"role": "execution"},
        )

        # An engineer that self-reports failure is a hard stop. The objective
        # quality gate would catch broken output anyway, but failing fast here
        # avoids wasting the (long) quality phase and removes the contradictory
        # "FE FAILED" / "task completed" signal that motivated this fix.
        failed = [
            role
            for role, ok in (
                ("Backend Engineer", summary.get("be_success", True)),
                ("Frontend Engineer", summary.get("fe_success", True)),
            )
            if not ok
        ]
        if failed:
            # Console/banner reason stays concise — one line. The concrete
            # tsc/eslint/mvn output is carried separately in blocked_detail so
            # error.md can stay actionable without the full block being echoed
            # three times across the execution node, error node, and footer.
            reason = (
                f"{' and '.join(failed)} reported FAILED during execution "
                f"(backend files: {len(summary.get('be_files', []))}, "
                f"frontend files: {len(summary.get('fe_files', []))}). "
                f"See the execution report for details: {summary.get('report_path')}"
            )
            detail = (summary.get("failure_diagnostics") or "")[:1500]
            logger.error(
                f"{MARKER_FAIL} {reason}",
                extra={"role": "execution"},
            )
            return {
                "execution_summary": summary,
                "iteration_count": iteration + 1,
                "phase": 2,
                "blocked": True,
                "blocked_reason": reason,
                "blocked_detail": detail,
                "log": [f"Execution failed (iter {iteration + 1}): {reason}"],
            }

        return {
            "execution_summary": summary,
            "iteration_count": iteration + 1,
            "phase": 2,
            "log": [f"Execution complete (iter {iteration + 1}): {summary.get('report_path')}"],
        }
    except Exception as exc:
        logger.error(
            f"{MARKER_FAIL} Execution failed: {type(exc).__name__}: {exc}",
            extra={"role": "execution"},
            exc_info=True,
        )
        return {
            "blocked": True,
            "blocked_reason": f"Execution failed: {exc}",
            "log": [f"Execution ERROR: {exc}"],
        }


def _quality_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    logger = get_logger(task_id)
    logger.info(f"{MARKER_INFO} Starting quality phase", extra={"role": "quality"})
    try:
        result = run_quality(task_id)
        updates: dict = {
            "quality_passed": result.get("passed", False),
            "secops_verdict": result.get("secops_verdict"),
            "phase": 3,
            "log": [
                f"Quality complete: passed={result.get('passed')}, "
                f"secops={result.get('secops_verdict')}"
            ],
        }
        if result.get("review_verdict"):
            updates["review_verdict"] = result["review_verdict"]

        if result.get("blocked"):
            updates["blocked"] = True
            updates["blocked_reason"] = (
                f"Quality blocked at phase '{result.get('stopped_at')}'"
            )
        elif not result.get("passed") and result.get("stopped_at"):
            updates["blocked"] = True
            updates["blocked_reason"] = (
                f"Quality failed at phase '{result.get('stopped_at')}'"
            )
        logger.info(
            f"{MARKER_OK} Quality: passed={result.get('passed')}, "
            f"secops={result.get('secops_verdict')}",
            extra={"role": "quality"},
        )
        return updates
    except Exception as exc:
        logger.error(
            f"{MARKER_FAIL} Quality pipeline raised exception: {type(exc).__name__}: {exc}",
            extra={"role": "quality"},
            exc_info=True,
        )
        return {
            "blocked": True,
            "blocked_reason": f"Quality pipeline raised exception: {exc}",
            "log": [f"Quality ERROR: {exc}"],
        }


def _final_review_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    logger = get_logger(task_id)
    logger.info(f"{MARKER_INFO} Running final coherence review", extra={"role": "final"})

    try:
        # Single completion with no tool diff competing for the window, so the budget
        # is comfortable: pass the full plan + execution report (generously bounded)
        # so the coherence check isn't judging plan-vs-impl against a quarter of each.
        plan_path = state.get("plan_path") or f"{_CONTEXT_DIR}/{task_id}_plan.md"
        plan_content = read_file(plan_path)[:12000]

        decisions_content = ""
        try:
            decisions_content = read_file("docs/DECISIONS.md")[:4000]
        except Exception:
            pass

        exec_content = ""
        try:
            exec_content = read_file(f"{_CONTEXT_DIR}/{task_id}_execution.md")[:8000]
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

        # Never stream tokens to the console — the spinner conveys liveness and the
        # full response is archived in the log file below. Large num_ctx + low temp
        # for local runs (see config.get_model_options) so the plan/exec excerpts fit.
        response = litellm.completion(
            **llm_params,
            **get_model_options("final"),
            messages=[{"role": "user", "content": prompt}],
            timeout=120,
            stream=False,
        )
        verdict_text = response.choices[0].message.content.strip()
        logger.debug(f"Final review response:\n{verdict_text}", extra={"role": "final"})

        verdict = (
            "APPROVED"
            if verdict_text.upper().startswith("APPROVED")
            else "REQUIRES_CHANGES"
        )
        logger.info(f"{MARKER_OK} Final review verdict: {verdict}", extra={"role": "final"})
        return {
            "review_verdict": verdict,
            "phase": 4,
            "log": [f"Final review: {verdict}"],
        }
    except Exception as exc:
        # If the LLM is unreachable, mark with a sentinel so the done report can warn the user
        # instead of falsely advertising an APPROVED outcome.
        logger.warning(
            f"Final review error ({type(exc).__name__}: {exc}) — marking ERROR_DEFAULTED_APPROVED",
            extra={"role": "final"},
            exc_info=True,
        )
        return {
            "review_verdict": "ERROR_DEFAULTED_APPROVED",
            "final_review_error": f"{type(exc).__name__}: {exc}",
            "phase": 4,
            "log": [f"Final review ERROR (defaulted APPROVED): {exc}"],
        }


def _done_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    logger = get_logger(task_id)
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    verdict = state.get("review_verdict")
    iteration_count = state.get("iteration_count", 0)
    review_unresolved = verdict == "REQUIRES_CHANGES"
    review_errored = verdict == "ERROR_DEFAULTED_APPROVED"

    summary_lines: list[str] = []
    if review_unresolved:
        summary_lines += [
            "> **WARNING:** Pipeline completed after max iterations with unresolved review findings.",
            "> ROADMAP.md was NOT marked complete (status left as 🔄 in-progress).",
            "",
        ]
    elif review_errored:
        err = state.get("final_review_error") or "unknown error"
        summary_lines += [
            "> **WARNING:** Final coherence review could not run.",
            f"> Reason: {err}",
            "> Treat the APPROVED verdict below as DEFAULTED, not validated.",
            "",
        ]

    summary_lines += [
        f"# Pipeline Complete: {task_id}",
        "",
        f"**Completed:** {ts}",
        f"**Iterations:** {iteration_count}",
        "",
        "## Phase Results",
        "",
        f"- Verification : {'PASSED' if state.get('quality_passed') else 'N/A'}",
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
    logger.info(f"{MARKER_OK} Summary written: {done_path}", extra={"role": "pipeline"})

    if review_unresolved:
        logger.warning(
            f"{MARKER_FAIL} ROADMAP not marked complete: review verdict is REQUIRES_CHANGES.",
            extra={"role": "pipeline"},
        )
    else:
        _mark_task_done_in_roadmap(task_id)

    return {
        "completed": True,
        "log": [f"Pipeline done. Summary: {done_path}"],
    }


def _error_node(state: PipelineState) -> dict:
    task_id = state["task_id"]
    reason = state.get("blocked_reason") or "Unknown error"
    detail = state.get("blocked_detail") or ""
    logger = get_logger(task_id)
    # Console gets only the concise reason; the full concrete error (if any) goes
    # to error.md and the log file, never echoed across three console surfaces.
    logger.error(f"{MARKER_FAIL} Pipeline error: {reason}", extra={"role": "pipeline"})
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    concrete_error = f"## Concrete Error\n\n{detail}\n\n" if detail.strip() else ""
    content = (
        f"# Pipeline Error: {task_id}\n\n"
        f"**Generated:** {ts}\n"
        f"**Reason:** {reason}\n\n"
        f"{concrete_error}"
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


# Active roadmap file — on version rollover, update per docs/roadmap/README.md
ROADMAP_PATH = "docs/roadmap/ROADMAP_V2.md"


def _mark_task_done_in_roadmap(task_id: str) -> None:
    """Replace 🔲 or 🔄 status with ✅ for the given task in the active roadmap."""
    try:
        roadmap = read_file(ROADMAP_PATH)
        updated_lines = []
        for line in roadmap.splitlines():
            if f"| {task_id} " in line or f"| {task_id}\t" in line:
                line = line.replace("\U0001f532", "✅").replace("\U0001f504", "✅")
            updated_lines.append(line)
        updated = "\n".join(updated_lines)
        if updated != roadmap:
            write_file(ROADMAP_PATH, updated)
    except Exception as exc:
        # Non-fatal: roadmap update failure should not block the pipeline
        get_logger(task_id).warning(
            f"Could not update ROADMAP.md: {exc}", extra={"role": "pipeline"}
        )


# ── Conditional routers ───────────────────────────────────────────────────────

def _route_after_planning(state: PipelineState) -> str:
    if state.get("blocked") or not state.get("plan_path"):
        return "error"
    return "execution"


def _route_after_execution(state: PipelineState) -> str:
    if state.get("blocked") or not state.get("execution_summary"):
        return "error"
    return "quality"


def _route_after_quality(state: PipelineState) -> str:
    if state.get("blocked") or not state.get("quality_passed"):
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
    builder.add_conditional_edges(
        "execution",
        _route_after_execution,
        {"quality": "quality", "error": "error"},
    )
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

    callback = PipelineConsoleCallback(task_id)
    run_config = {
        "configurable": {"thread_id": task_id},
        "callbacks": [callback],
    }

    logger = get_logger(task_id)

    try:
        if resume:
            logger.info(f"Resuming pipeline for {task_id}...", extra={"role": "pipeline"})
            try:
                saved = graph.get_state(run_config)
                if saved and saved.values:
                    result = graph.invoke(None, config=run_config)
                else:
                    logger.info("No checkpoint found — starting fresh.", extra={"role": "pipeline"})
                    result = graph.invoke(_fresh_state(task_id), config=run_config)
            except Exception:
                result = graph.invoke(_fresh_state(task_id), config=run_config)
        else:
            logger.info(f"Starting full pipeline for {task_id}...", extra={"role": "pipeline"})
            result = graph.invoke(_fresh_state(task_id), config=run_config)
    finally:
        conn.close()

    return result


def _fresh_state(task_id: str) -> PipelineState:
    return {
        "task_id": task_id,
        "phase": 0,
        "plan_path": None,
        "execution_summary": None,
        "quality_passed": False,
        "review_verdict": None,
        "secops_verdict": None,
        "iteration_count": 0,
        "blocked": False,
        "blocked_reason": None,
        "blocked_detail": None,
        "completed": False,
        "log": [],
    }
