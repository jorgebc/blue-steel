"""SecOps agent for Blue Steel quality pipeline.

Runs npm security audit and manually reviews the git diff for Blue Steel's
threat model: JWT forgery, campaign-level privilege escalation, SQL/JPQL
injection, secrets in logs/responses, missing authorization, frontend XSS.

Does NOT duplicate what CI (Trivy SBOM scan, GitHub dependency-review-action)
already catches. Focuses on logic-level vulnerabilities missed by static tools.

Applies remediations automatically for CRITICAL/HIGH findings where a source
code edit is sufficient. Documents MEDIUM/LOW for human review.

Output: .ai/context/tasks/{task_id}_secops.md
"""

import importlib.resources
import os
import sys
import yaml
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

from smolagents import CodeAgent, LiteLLMModel, LogLevel, tool

from config import get_llm, get_model_options
from llm_timeout import build_chat_model
from logger import get_logger
from tools.filesystem import (
    get_git_diff as _get_git_diff,
    read_file as _read_file,
    write_file as _write_file,
)
from tools.shell_runner import run_security_audit_frontend as _run_audit

_PROMPTS_DIR = Path(__file__).parents[2] / "prompts"
_CONTEXT_DIR = ".ai/context/tasks"


def _make_model() -> LiteLLMModel:
    """Build the SecOps model via the shared factory (config + per-call wall-clock guard)."""
    return build_chat_model(phase="secops")


def _build_prompt_templates(persona_content: str) -> dict:
    default: dict = yaml.safe_load(
        importlib.resources.files("smolagents.prompts")
        .joinpath("code_agent.yaml")
        .read_text()
    )
    # Persona is static markdown that may contain literal braces (e.g. JSX
    # `style={{}}`). Wrap it in a Jinja2 raw block so smolagents' StrictUndefined
    # template renderer treats those braces as text, not template syntax.
    default["system_prompt"] = (
        "{% raw %}\n"
        f"{persona_content}\n"
        "{% endraw %}\n\n"
        "---\n\n"
        f"{default['system_prompt']}"
    )
    return default


@tool
def read_project_file(path: str) -> str:
    """Read a Blue Steel project file by repo-relative path.

    Args:
        path: File path relative to the repo root.

    Returns:
        File content as UTF-8 string, or an error message if not found.
    """
    try:
        return _read_file(path)
    except FileNotFoundError:
        return f"[File not found: {path}]"


@tool
def write_project_file(path: str, content: str) -> str:
    """Write a security remediation to a Blue Steel source file.

    Args:
        path: File path relative to the repo root.
        content: Remediated file content (UTF-8).

    Returns:
        Confirmation string.
    """
    _write_file(path, content)
    return f"Written: {path}"


@tool
def get_git_diff(base: str = "main") -> str:
    """Return the full diff of the current branch against base.

    Args:
        base: Base branch name (default: 'main').

    Returns:
        Git diff output as a string.
    """
    return _get_git_diff(base)


@tool
def run_security_audit_frontend() -> dict:
    """Run npm audit --audit-level=high --production in apps/web/.

    Returns:
        Dict with keys: stdout, stderr, returncode, success, summary.
        - stdout: raw npm audit output
        - stderr: warnings/errors from npm
        - returncode: process exit code (0 = no high CVEs)
        - success: True if no high-severity CVEs found (returncode == 0)
        - summary: one-line human-readable summary of the audit result
        Use result['stdout'] for the full audit text. Do NOT access result.summary
        (it is a dict key, not an attribute).
    """
    raw = _run_audit()
    vuln_line = next(
        (ln.strip() for ln in raw.get("stdout", "").splitlines() if "vulnerabilit" in ln),
        None,
    )
    raw["summary"] = vuln_line or ("no vulnerabilities found" if raw.get("success") else "vulnerabilities found — see stdout")
    return raw


_CODE_FORMAT_GUIDANCE = """
---
Return your result by calling final_answer(result) as the last statement, using the exact dict
shape and rules defined in your system prompt under "## Outputs".
"""


def _create_agent() -> CodeAgent:
    persona = (_PROMPTS_DIR / "secops.md").read_text(encoding="utf-8")
    return CodeAgent(
        tools=[
            read_project_file,
            write_project_file,
            get_git_diff,
            run_security_audit_frontend,
        ],
        model=_make_model(),
        prompt_templates=_build_prompt_templates(persona),
        max_steps=15,
        verbosity_level=LogLevel.OFF,  # ReAct trace -> silenced; story lives in the logger
    )


def _build_report(task_id: str, result: dict, audit_output: str) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    findings_lines: list[str] = []
    for f in result.get("findings", []):
        status_tag = f" **[{f.get('status', 'OPEN')}]**"
        findings_lines += [
            f"### {f.get('severity', 'UNKNOWN')} — {f.get('threat', '?')}{status_tag}",
            "",
            f"**File:** {f.get('file', 'unknown')}",
            f"**Problem:** {f.get('problem', '')}",
            f"**Fix:** {f.get('fix', '')}",
            "",
        ]

    findings_text = "\n".join(findings_lines) if findings_lines else "_(no findings)_\n"
    audit_snippet = (audit_output or "(not run)").strip()[:800]

    return (
        f"# SecOps Report: {task_id}\n\n"
        f"**Generated:** {ts}\n"
        f"**Verdict:** {result['verdict']}\n\n"
        f"---\n\n"
        f"## Summary\n\n"
        f"- CRITICAL findings: {result.get('critical', 0)}\n"
        f"- HIGH findings: {result.get('high', 0)}\n"
        f"- MEDIUM findings: {result.get('medium', 0)}\n"
        f"- LOW findings: {result.get('low', 0)}\n"
        f"- RESOLVED automatically: {result.get('resolved', 0)}\n\n"
        f"---\n\n"
        f"## npm Audit Output\n\n"
        f"```\n{audit_snippet}\n```\n\n"
        f"---\n\n"
        f"## Findings\n\n"
        f"{findings_text}\n"
        f"---\n\n"
        f"## Notes\n\n"
        f"{result.get('notes') or '_(none)_'}\n"
    )


def run_secops(task_id: str) -> dict:
    """Run the SecOps agent for the given task.

    Executes npm audit, reviews the git diff for Blue Steel's threat vectors,
    applies CRITICAL/HIGH remediations automatically, and writes a report.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.

    Returns:
        Dict with keys:
            - verdict: 'APPROVED' or 'BLOCKED'
            - critical: count of unresolved CRITICAL findings
            - high: count of unresolved HIGH findings
            - resolved: count of CRITICAL/HIGH findings auto-remediated
    """
    logger = get_logger(task_id)
    agent = _create_agent()

    task_prompt = f"""
You are the SecOps agent for Blue Steel.
{_CODE_FORMAT_GUIDANCE}

## Task ID
{task_id}

## Your Job

1. Call run_security_audit_frontend() to check npm production dependencies for CVEs.
   Record the summary in audit_output. If it fails (success=False), note it but
   remember Trivy CI already scans for CVEs — focus on whether the diff introduces
   a new risky dependency not yet in the scan cycle.

2. Call get_git_diff() to read all code changes on this branch.

3. For each changed file, systematically check all six threat vectors from your persona:
   T1 (JWT), T2 (campaign escalation), T3 (SQL injection), T4 (secrets in logs),
   T5 (missing auth), T6 (frontend XSS).

4. For CRITICAL and HIGH findings where you can apply the fix by editing a source file,
   call write_project_file and set status="RESOLVED" in the finding.

5. Count unresolved CRITICAL and HIGH findings. If any remain, verdict="BLOCKED".
   If all CRITICAL/HIGH are resolved or zero, verdict="APPROVED".

6. Return the result dict. Do NOT write TODO, placeholder, or incomplete findings.
"""

    logger.debug("Agent prompt (truncated):\n%s", task_prompt[:800], extra={"role": "secops"})
    raw = agent.run(task_prompt)
    # ascii() escapes non-ASCII chars (e.g. agent-emitted '→') for cp1252 console safety on Windows.
    logger.debug("Agent raw output: %s", ascii(raw)[:500], extra={"role": "secops"})

    # Normalize response
    if isinstance(raw, dict):
        result = raw
    else:
        result = {
            "verdict": "APPROVED",
            "critical": 0,
            "high": 0,
            "medium": 0,
            "low": 0,
            "resolved": 0,
            "findings": [],
            "audit_output": "",
            "notes": str(raw),
        }

    result.setdefault("verdict", "APPROVED")
    result.setdefault("critical", 0)
    result.setdefault("high", 0)
    result.setdefault("medium", 0)
    result.setdefault("low", 0)
    result.setdefault("resolved", 0)
    result.setdefault("findings", [])
    result.setdefault("audit_output", "")
    result.setdefault("notes", "")

    # Recompute verdict from unresolved critical/high counts
    open_critical_high = sum(
        1
        for f in result["findings"]
        if f.get("severity") in ("CRITICAL", "HIGH")
        and f.get("status") != "RESOLVED"
    )
    result["critical"] = sum(
        1
        for f in result["findings"]
        if f.get("severity") == "CRITICAL" and f.get("status") != "RESOLVED"
    )
    result["high"] = sum(
        1
        for f in result["findings"]
        if f.get("severity") == "HIGH" and f.get("status") != "RESOLVED"
    )
    result["resolved"] = sum(
        1
        for f in result["findings"]
        if f.get("severity") in ("CRITICAL", "HIGH")
        and f.get("status") == "RESOLVED"
    )
    result["verdict"] = "BLOCKED" if open_critical_high > 0 else "APPROVED"

    # Write report
    audit_output = result.get("audit_output", "")
    report_content = _build_report(task_id, result, audit_output)
    report_path = f"{_CONTEXT_DIR}/{task_id}_secops.md"
    _write_file(report_path, report_content)
    logger.debug(
        f"SecOps report written: {report_path} | verdict={result['verdict']} | "
        f"CRITICAL={result['critical']} | HIGH={result['high']} | resolved={result['resolved']}",
        extra={"role": "secops"},
    )

    return {
        "verdict": result["verdict"],
        "critical": result["critical"],
        "high": result["high"],
        "resolved": result["resolved"],
    }
