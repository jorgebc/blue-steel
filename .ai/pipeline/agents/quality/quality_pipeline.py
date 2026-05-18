"""Quality coordinator for Blue Steel AI pipeline.

Runs verification -> review -> secops in order, with conditional routers
between each phase that can stop, retry, or continue the pipeline.

Router logic:
  After verification:
    blocked=True              -> write blocker report, stop pipeline
    passed=False              -> retry verification once; if still failing, stop
    passed=True               -> continue to review

  After review:
    REQUIRES_CHANGES
     + high_findings > 0      -> re-run verification after auto-fixes
    APPROVED_WITH_SUGGESTIONS -> continue to secops
    APPROVED                  -> continue to secops

  After secops:
    BLOCKED                   -> stop, require human intervention
    APPROVED                  -> pipeline complete

Output: .ai/context/tasks/{task_id}_verification.md
        .ai/context/tasks/{task_id}_review.md
        .ai/context/tasks/{task_id}_secops.md
"""

import sys
from datetime import datetime, timezone
from pathlib import Path

# ── Windows Unicode fix ────────────────────────────────────────────────────────
if sys.platform == "win32":
    try:
        import ctypes
        import ctypes.wintypes

        _k32 = ctypes.windll.kernel32
        _handle = _k32.GetStdHandle(-11)
        _mode = ctypes.wintypes.DWORD()
        _k32.GetConsoleMode(_handle, ctypes.byref(_mode))
        _k32.SetConsoleMode(_handle, _mode.value | 0x0004)
    except Exception:
        pass
    try:
        from rich._win32_console import LegacyWindowsTerm as _LWT

        _orig_write = _LWT.write_text

        def _safe_write(self, text: str) -> None:
            try:
                _orig_write(self, text)
            except (UnicodeEncodeError, UnicodeDecodeError):
                _orig_write(
                    self,
                    text.encode("cp1252", errors="replace").decode("cp1252"),
                )

        _LWT.write_text = _safe_write
    except ImportError:
        pass

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from tools.filesystem import write_file as _write_file

import verification_agent
import review_agent
import secops_agent

_CONTEXT_DIR = ".ai/context/tasks"


def _write_blocker_report(task_id: str, phase: str, reason: str) -> str:
    """Write a pipeline blocker report and return its path."""
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    content = (
        f"# Pipeline Blocker: {task_id}\n\n"
        f"**Generated:** {ts}\n"
        f"**Phase:** {phase}\n"
        f"**Reason:** {reason}\n\n"
        f"---\n\n"
        f"## Action Required\n\n"
        f"The quality pipeline was stopped at the **{phase}** phase.\n"
        f"Human intervention is required before this task can proceed.\n\n"
        f"Review the corresponding report for details:\n"
        f"- Verification: `.ai/context/tasks/{task_id}_verification.md`\n"
        f"- Review: `.ai/context/tasks/{task_id}_review.md`\n"
        f"- SecOps: `.ai/context/tasks/{task_id}_secops.md`\n"
    )
    blocker_path = f"{_CONTEXT_DIR}/{task_id}_blocker.md"
    _write_file(blocker_path, content)
    return blocker_path


def _report_paths(task_id: str) -> dict[str, str]:
    return {
        "verification_report": f"{_CONTEXT_DIR}/{task_id}_verification.md",
        "review_report": f"{_CONTEXT_DIR}/{task_id}_review.md",
        "secops_report": f"{_CONTEXT_DIR}/{task_id}_secops.md",
    }


def run_quality(task_id: str) -> dict:
    """Run the full quality pipeline for the given task.

    Orchestrates: verification -> review -> secops with conditional routers.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.

    Returns:
        Dict with keys:
            - passed: True if all three phases completed without blocking
            - blocked: True if the pipeline was stopped by a blocker
            - stopped_at: 'verification' | 'review' | 'secops' | None
            - verification_verdict: summary from verification phase
            - review_verdict: 'APPROVED' | 'APPROVED_WITH_SUGGESTIONS' | 'REQUIRES_CHANGES'
            - secops_verdict: 'APPROVED' | 'BLOCKED'
            - verification_report: path to verification report file
            - review_report: path to review report file
            - secops_report: path to secops report file
    """
    print(f"\n{'='*60}")
    print(f"Blue Steel Quality Pipeline — Task: {task_id}")
    print(f"{'='*60}\n")

    paths = _report_paths(task_id)
    state: dict = {
        "passed": False,
        "blocked": False,
        "stopped_at": None,
        "verification_verdict": "NOT_RUN",
        "review_verdict": "NOT_RUN",
        "secops_verdict": "NOT_RUN",
        **paths,
    }

    # ── Phase 1: Verification ──────────────────────────────────────────────────
    print("\n[QUALITY] Phase 1/3: Verification")
    v_result = verification_agent.run_verification(task_id)
    state["verification_verdict"] = (
        "PASSED" if v_result["passed"] else
        "BLOCKED" if v_result["blocked"] else
        "FAILED"
    )

    # Router: blocked -> stop immediately
    if v_result["blocked"]:
        print(
            "\n[ROUTER] Verification BLOCKED — stopping pipeline. "
            "Human intervention required."
        )
        blocker_path = _write_blocker_report(
            task_id,
            "verification",
            v_result.get("summary", "One or more checks are BLOCKED after 3 auto-fix attempts."),
        )
        state["blocked"] = True
        state["stopped_at"] = "verification"
        return state

    # Router: not passed (FAIL status on some checks) -> retry once
    if not v_result["passed"]:
        print(
            "\n[ROUTER] Verification FAILED (not blocked) — retrying once..."
        )
        v_result = verification_agent.run_verification(task_id)
        state["verification_verdict"] = (
            "PASSED" if v_result["passed"] else
            "BLOCKED" if v_result["blocked"] else
            "FAILED"
        )

        if v_result["blocked"]:
            print(
                "\n[ROUTER] Verification BLOCKED on retry — stopping pipeline."
            )
            _write_blocker_report(
                task_id,
                "verification",
                v_result.get("summary", "Blocked after retry."),
            )
            state["blocked"] = True
            state["stopped_at"] = "verification"
            return state

        if not v_result["passed"]:
            print(
                "\n[ROUTER] Verification still FAILING after retry — stopping pipeline."
            )
            _write_blocker_report(
                task_id,
                "verification",
                v_result.get("summary", "Checks still failing after one retry."),
            )
            state["blocked"] = False
            state["stopped_at"] = "verification"
            return state

    print("\n[ROUTER] Verification PASSED — proceeding to review.")

    # ── Phase 2: Code Review ───────────────────────────────────────────────────
    print("\n[QUALITY] Phase 2/3: Code Review")
    r_result = review_agent.run_review(task_id)
    state["review_verdict"] = r_result["verdict"]

    # Router: REQUIRES_CHANGES with high findings -> re-run verification after fixes
    if r_result["verdict"] == "REQUIRES_CHANGES" and r_result["high_findings"] > 0:
        print(
            f"\n[ROUTER] Review found {r_result['high_findings']} unfixed HIGH finding(s). "
            f"Re-running verification after auto-fixes..."
        )
        v_result = verification_agent.run_verification(task_id)
        state["verification_verdict"] = (
            "PASSED" if v_result["passed"] else
            "BLOCKED" if v_result["blocked"] else
            "FAILED"
        )

        if v_result["blocked"] or not v_result["passed"]:
            print(
                "\n[ROUTER] Verification failed after review fixes — stopping pipeline."
            )
            _write_blocker_report(
                task_id,
                "review",
                (
                    f"Review found {r_result['high_findings']} HIGH finding(s) "
                    f"and verification failed after applying fixes."
                ),
            )
            state["blocked"] = v_result["blocked"]
            state["stopped_at"] = "review"
            return state

    print(
        f"\n[ROUTER] Review verdict: {r_result['verdict']} — "
        f"proceeding to SecOps."
    )

    # ── Phase 3: SecOps ────────────────────────────────────────────────────────
    print("\n[QUALITY] Phase 3/3: SecOps")
    s_result = secops_agent.run_secops(task_id)
    state["secops_verdict"] = s_result["verdict"]

    # Router: BLOCKED -> stop, require human intervention
    if s_result["verdict"] == "BLOCKED":
        print(
            f"\n[ROUTER] SecOps BLOCKED — {s_result.get('critical', 0)} CRITICAL, "
            f"{s_result.get('high', 0)} HIGH unresolved. "
            f"Human intervention required."
        )
        _write_blocker_report(
            task_id,
            "secops",
            (
                f"SecOps found {s_result.get('critical', 0)} unresolved CRITICAL "
                f"and {s_result.get('high', 0)} unresolved HIGH security findings."
            ),
        )
        state["blocked"] = True
        state["stopped_at"] = "secops"
        return state

    print("\n[ROUTER] SecOps APPROVED — quality pipeline complete.")
    state["passed"] = True
    state["stopped_at"] = None

    _print_summary(task_id, state, v_result, r_result, s_result)
    return state


def _print_summary(
    task_id: str,
    state: dict,
    v_result: dict,
    r_result: dict,
    s_result: dict,
) -> None:
    print(f"\n{'='*60}")
    print(f"Quality Pipeline Complete — Task: {task_id}")
    print(f"{'='*60}")
    print(f"  Verification : {state['verification_verdict']}")
    print(
        f"  Review       : {state['review_verdict']} "
        f"(high={r_result['high_findings']}, fixed={r_result['fixed']})"
    )
    print(
        f"  SecOps       : {state['secops_verdict']} "
        f"(critical={s_result.get('critical', 0)}, "
        f"high={s_result.get('high', 0)}, "
        f"resolved={s_result.get('resolved', 0)})"
    )
    print(f"  Overall      : {'PASSED' if state['passed'] else 'FAILED'}")
    print(f"{'='*60}\n")
