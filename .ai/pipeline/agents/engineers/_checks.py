"""Shared check-result capture and circuit breaker for the engineer agents.

The engineer CodeAgents run quality checks (type-check, lint, tests, sonar) as
tools, but smolagents silences the ReAct trace (`LogLevel.OFF`) and the tool
wrappers used to return their `{stdout, stderr, returncode}` straight to the LLM
without logging them. The real `tsc`/`eslint` output therefore never reached the
per-task log or the reports, leaving failures undiagnosable — the symptom that
prompted this module.

Responsibilities:
- `record_and_log` — log every check result to the task log at DEBUG (file-only,
  so the console phase timeline stays clean) and remember it for the run.
- `last_failure_diagnostics` — render the most recent failing check as a concrete
  markdown block for the execution report.
- `should_abort` / `abort_step_callback` — let the agent stop early instead of
  looping for ~90 minutes on an error no code rewrite can fix.

The active task id and role are set per run via `reset()`; the `@tool` wrappers in
`fe_agent`/`be_agent` do not carry that context themselves.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from logger import MARKER_INFO, get_logger

# Bound the captured output so a runaway log line never blows up the file or the report.
_TAIL_CHARS = 2000

# Error signatures that no amount of code rewriting will resolve — they need a new
# dependency or a different approach. Looping on these only burns wall-clock time,
# so the circuit breaker trips on the first occurrence. (Frontend/TypeScript
# oriented; harmless no-ops against Java/Maven output.)
_UNRECOVERABLE_SIGNATURES = (
    "cannot find module",
    "could not find a declaration file for module",
    "cannot find name 'process'",
    "cannot find name 'require'",
)

_active_task_id: str | None = None
_active_role: str = "execution"
_history: list[dict] = []


def reset(task_id: str, role: str = "execution") -> None:
    """Begin capturing checks for a fresh engineer run. Call alongside reset_write_tracker()."""
    global _active_task_id, _active_role
    _active_task_id = task_id
    _active_role = role
    _history.clear()


def _first_error_line(entry: dict) -> str:
    """Best-effort single line that identifies a failure, for comparing repeats."""
    for blob in (entry.get("stderr_tail", ""), entry.get("stdout_tail", "")):
        for line in blob.splitlines():
            stripped = line.strip()
            if stripped:
                return stripped
    return f"returncode={entry.get('returncode')}"


def record_and_log(tool_name: str, result: dict) -> dict:
    """Remember and DEBUG-log a check result, then return it unchanged to the agent."""
    entry = {
        "tool": tool_name,
        "returncode": result.get("returncode"),
        "success": bool(result.get("success")),
        "stdout_tail": (result.get("stdout") or "")[-_TAIL_CHARS:],
        "stderr_tail": (result.get("stderr") or "")[-_TAIL_CHARS:],
    }
    _history.append(entry)

    if _active_task_id:
        status = "OK" if entry["success"] else f"FAILED (returncode {entry['returncode']})"
        get_logger(_active_task_id).debug(
            "check %s -> %s\n--- stdout (tail) ---\n%s\n--- stderr (tail) ---\n%s",
            tool_name,
            status,
            entry["stdout_tail"] or "(empty)",
            entry["stderr_tail"] or "(empty)",
            extra={"role": _active_role},
        )
    return result


def last_check_failed() -> bool:
    """True if the most recent check the agent ran was a failure.

    An engineer that ends on a red check did not finish cleanly, regardless of what
    its `final_answer` claims — used to override an over-optimistic success flag.
    """
    return bool(_history) and not _history[-1]["success"]


def should_abort() -> tuple[bool, str]:
    """Whether the run should stop early, with a human-readable reason.

    Trips on (a) a known-unrecoverable error signature, or (b) the same check
    failing twice in a row with the same first error line (a fix loop going nowhere).
    """
    if not _history:
        return False, ""
    last = _history[-1]
    if last["success"]:
        return False, ""

    blob = f"{last['stderr_tail']}\n{last['stdout_tail']}".lower()
    for signature in _UNRECOVERABLE_SIGNATURES:
        if signature in blob:
            return True, f"unrecoverable error: '{signature}'"

    same_tool_failures = [
        e for e in _history if e["tool"] == last["tool"] and not e["success"]
    ]
    if len(same_tool_failures) >= 2:
        recent_lines = {_first_error_line(e) for e in same_tool_failures[-2:]}
        if len(recent_lines) == 1:
            return True, f"'{last['tool']}' failed twice with the same error"
    return False, ""


def last_failure_diagnostics() -> str:
    """Render the most recent failing check as a concrete markdown block, or ''."""
    failures = [e for e in _history if not e["success"]]
    if not failures:
        return ""
    last = failures[-1]
    parts = [
        f"Check `{last['tool']}` failed (returncode {last['returncode']}).",
        "",
    ]
    stdout = last["stdout_tail"].strip()
    stderr = last["stderr_tail"].strip()
    if stdout:
        parts += ["stdout (tail):", "```", stdout, "```", ""]
    if stderr:
        parts += ["stderr (tail):", "```", stderr, "```", ""]
    return "\n".join(parts).strip()


def abort_step_callback(memory_step, agent=None) -> None:
    """smolagents step callback: interrupt the agent when the circuit breaker trips.

    Registered for ActionStep; receives the agent instance via kwargs. Calling
    `agent.interrupt()` raises on the next step-loop check, which `run()` catches
    and turns into a structured failure carrying `last_failure_diagnostics()`.
    """
    abort, reason = should_abort()
    if abort and agent is not None:
        if _active_task_id:
            get_logger(_active_task_id).info(
                f"{MARKER_INFO} circuit breaker tripped ({reason}) — stopping early",
                extra={"role": _active_role},
            )
        agent.interrupt()
