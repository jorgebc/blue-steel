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

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from logger import MARKER_INFO, get_logger

# Bound the captured output so a runaway log line never blows up the file or the report.
_TAIL_CHARS = 2000

# Error signatures that no amount of code rewriting will resolve regardless of context —
# they always need a new dependency / @types package. Looping on these only burns
# wall-clock time, so the circuit breaker trips on the first occurrence. (Frontend/
# TypeScript oriented; harmless no-ops against Java/Maven output.)
#
# Note what is deliberately NOT here:
# - "cannot find module" is handled separately (see `_has_missing_package` /
#   `_bare_missing_specifiers`): a *relative* specifier names a project file the engineer
#   simply hasn't written yet (recoverable by creating it), and a *bare* specifier is no
#   longer an instant trip — a hallucinated/undeclared import (e.g. `@shadcn/ui`) is the
#   engineer's own removable mistake, so it gets ONE fix attempt and only trips if the same
#   bare module is still missing on the next run of the same check (see `should_abort`).
# - "cannot find name 'process'/'require'" (node globals) are recoverable enough — the agent
#   can relocate/remove the offending file — that they don't warrant a first-occurrence trip;
#   the no-progress guard (condition (b)) still catches them if a real retry makes no headway.
_UNRECOVERABLE_SIGNATURES = (
    "could not find a declaration file for module",
)

# Pulls the module specifier out of a `Cannot find module 'X'` (TS2307) diagnostic.
_CANNOT_FIND_MODULE_RE = re.compile(r"cannot find module ['\"]([^'\"]+)['\"]", re.IGNORECASE)


def _has_missing_package(blob: str) -> bool:
    """True if `blob` reports a missing *bare* (package) module.

    A bare specifier (`axios`, `next/router`, `@scope/pkg`) needs an npm install / declared
    NEW DEPENDENCY, or is a hallucinated import the engineer must remove. Relative specifiers
    (`./`, `../`, `/`) name a project file the engineer is expected to create, so they are
    excluded. Used to prioritise diagnostics; the abort decision uses persistence across runs
    (see `should_abort`), not this first-occurrence check.
    """
    return bool(_bare_missing_specifiers(blob))


def _bare_missing_specifiers(blob: str) -> set[str]:
    """Bare (package) module specifiers reported missing in `blob`; relative ones excluded."""
    return {
        specifier
        for specifier in _CANNOT_FIND_MODULE_RE.findall(blob)
        if specifier and specifier[0] not in "./"
    }


def _entry_bare_missing(entry: dict) -> set[str]:
    """Bare missing-module specifiers from a recorded check entry's output tails."""
    return _bare_missing_specifiers(f"{entry.get('stderr_tail', '')}\n{entry.get('stdout_tail', '')}")

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


def _latest_by_tool() -> dict[str, dict]:
    """Map each tool name to its most recent recorded result.

    The breaker reasons per-tool rather than off `_history[-1]`: agents batch
    several checks into one step (e.g. typecheck -> lint -> tests), so the last
    entry is often a trivially-passing check (`tests` exits 0 with no test files)
    that would otherwise mask a still-failing typecheck/lint earlier in the step.
    """
    latest: dict[str, dict] = {}
    for entry in _history:
        latest[entry["tool"]] = entry
    return latest


def _unresolved_checks() -> list[dict]:
    """The checks whose most recent run failed (unresolved), in tool-call order."""
    return [e for e in _latest_by_tool().values() if not e["success"]]


def last_check_failed() -> bool:
    """True if any check the agent ran is still unresolved (its latest run failed).

    An engineer that ends on a red check did not finish cleanly, regardless of what
    its `final_answer` claims — used to override an over-optimistic success flag.
    Evaluated per-tool so a trailing, trivially-passing `tests` run does not hide a
    still-failing typecheck or lint from the same step.
    """
    return bool(_unresolved_checks())


def should_abort() -> tuple[bool, str]:
    """Whether the run should stop early, with a human-readable reason.

    Trips on (a) a known genuinely-unrecoverable signature in any unresolved check
    (first occurrence), or (b) a still-failing check that made no progress on retry —
    either the same first error line twice, or the same *bare* missing module still
    absent across its last two failing runs. A missing bare module therefore gets ONE
    fix attempt (a hallucinated/undeclared import like `@shadcn/ui` is removable; a
    genuinely-missing package persists and trips on the second run). Relative missing
    modules never trip — they are files the engineer is expected to create. All
    conditions use the latest result per tool, so a passing check later in the same step
    cannot mask an earlier failure.
    """
    unresolved = _unresolved_checks()
    if not unresolved:
        return False, ""

    # (a) Known-unrecoverable signature in any still-failing check (first occurrence).
    for entry in unresolved:
        blob = f"{entry['stderr_tail']}\n{entry['stdout_tail']}".lower()
        for signature in _UNRECOVERABLE_SIGNATURES:
            if signature in blob:
                return True, f"unrecoverable error in {entry['tool']}: '{signature}'"

    # (b) A still-failing check that made no progress across its last two failing runs.
    for entry in unresolved:
        tool_failures = [
            e for e in _history if e["tool"] == entry["tool"] and not e["success"]
        ]
        if len(tool_failures) >= 2:
            last_two = tool_failures[-2:]
            # Same first error line twice — a fix loop going nowhere.
            if len({_first_error_line(e) for e in last_two}) == 1:
                return True, f"'{entry['tool']}' failed twice with the same error"
            # The same bare (package) module is still missing after a fix attempt —
            # either a hallucinated import the engineer won't drop or a genuinely
            # uninstalled package; neither is fixable by further looping.
            persisted = _entry_bare_missing(last_two[0]) & _entry_bare_missing(last_two[1])
            if persisted:
                spec = sorted(persisted)[0]
                return True, f"missing package persisted across retries in {entry['tool']}: '{spec}'"
    return False, ""


def last_failure_diagnostics() -> str:
    """Render a still-failing check as a concrete markdown block, or ''.

    Prefers an unresolved check whose output matches an unrecoverable signature
    (the real blocker, e.g. a missing module) over later, more cosmetic failures;
    falls back to the most recent failing check otherwise.
    """
    failures = [e for e in _history if not e["success"]]
    if not failures:
        return ""
    last = None
    for entry in _unresolved_checks():
        blob = f"{entry['stderr_tail']}\n{entry['stdout_tail']}".lower()
        if any(sig in blob for sig in _UNRECOVERABLE_SIGNATURES) or _has_missing_package(blob):
            last = entry
            break
    if last is None:
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
