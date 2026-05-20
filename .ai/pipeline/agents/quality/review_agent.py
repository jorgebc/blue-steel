"""Code review agent for Blue Steel quality pipeline.

Reads the full git diff and implementation plan, reviews against Blue Steel's
hexagonal architecture rules, non-negotiable code conventions, and alignment
with DECISIONS.md. Applies HIGH-severity fixes automatically.

Output: .ai/context/tasks/{task_id}_review.md
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

from config import get_llm
from logger import get_logger
from tools.filesystem import (
    file_exists as _file_exists,
    get_git_diff as _get_git_diff,
    read_file as _read_file,
    write_file as _write_file,
)

_PROMPTS_DIR = Path(__file__).parents[2] / "prompts"
_CONTEXT_DIR = ".ai/context/tasks"


def _make_model() -> LiteLLMModel:
    llm_params = get_llm(phase="review")
    model_id: str = llm_params["model"]
    api_key_raw: str = llm_params.get("api_key", "")
    api_base: str | None = llm_params.get("api_base")
    api_key: str | None = None
    if isinstance(api_key_raw, str) and api_key_raw.startswith("os.environ/"):
        env_var = api_key_raw.split("/", 1)[1]
        api_key = os.environ.get(env_var)
    elif api_key_raw:
        api_key = api_key_raw
    return LiteLLMModel(
        model_id=model_id,
        api_key=api_key,
        api_base=api_base,
        timeout=1800,
    )


def _build_prompt_templates(persona_content: str) -> dict:
    default: dict = yaml.safe_load(
        importlib.resources.files("smolagents.prompts")
        .joinpath("code_agent.yaml")
        .read_text()
    )
    default["system_prompt"] = (
        f"{persona_content}\n\n---\n\n{default['system_prompt']}"
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
    """Write a fix to a Blue Steel source file.

    Args:
        path: File path relative to the repo root.
        content: Fixed file content (UTF-8).

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


_CODE_FORMAT_GUIDANCE = """
---
CRITICAL OUTPUT FORMAT — produce your final answer as Python code using this EXACT pattern:

```python
result = {
    "verdict": "APPROVED",
    "high_findings": 0,
    "medium_findings": 0,
    "low_findings": 0,
    "fixed": 0,
    "findings": [
        {
            "severity": "HIGH",
            "file": "apps/api/path/File.java (line 42)",
            "problem": "concise description of the violation",
            "fix": "what to change and why (cite the rule, e.g. ARCH-01, D-043)",
            "auto_fixed": False,
        }
    ],
    "notes": "Brief overall summary of the review.",
}
final_answer(result)
```

Rules:
1. verdict: "APPROVED" if high_findings == 0; "APPROVED_WITH_SUGGESTIONS" if only MEDIUM/LOW
   findings remain; "REQUIRES_CHANGES" if any unfixed HIGH findings remain.
2. high_findings: count of HIGH findings NOT auto-fixed.
3. fixed: count of HIGH findings you applied automatically.
4. findings: ALL findings including auto-fixed ones (set auto_fixed=True for those).
5. notes: plain string, no markdown inside.
6. Do NOT write "TODO", "placeholder", or incomplete entries in any field.
7. Call final_answer(result) as the LAST statement — return the dict, not a string.
"""


def _create_agent() -> CodeAgent:
    persona = (_PROMPTS_DIR / "senior_reviewer.md").read_text(encoding="utf-8")
    return CodeAgent(
        tools=[read_project_file, write_project_file, get_git_diff],
        model=_make_model(),
        prompt_templates=_build_prompt_templates(persona),
        max_steps=15,
        verbosity_level=LogLevel.OFF,  # ReAct trace -> silenced; story lives in the logger
    )


def _build_report(task_id: str, result: dict) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    findings_lines: list[str] = []
    for f in result.get("findings", []):
        auto_tag = " **[AUTO-FIXED]**" if f.get("auto_fixed") else ""
        findings_lines += [
            f"### {f.get('severity', 'UNKNOWN')}{auto_tag}",
            "",
            f"**File:** {f.get('file', 'unknown')}",
            f"**Problem:** {f.get('problem', '')}",
            f"**Fix:** {f.get('fix', '')}",
            "",
        ]

    findings_text = "\n".join(findings_lines) if findings_lines else "_(no findings)_\n"

    return (
        f"# Code Review Report: {task_id}\n\n"
        f"**Generated:** {ts}\n"
        f"**Verdict:** {result['verdict']}\n\n"
        f"---\n\n"
        f"## Summary\n\n"
        f"- HIGH findings remaining: {result['high_findings']}\n"
        f"- MEDIUM findings: {result.get('medium_findings', 0)}\n"
        f"- LOW findings: {result.get('low_findings', 0)}\n"
        f"- AUTO-FIXED: {result['fixed']}\n\n"
        f"---\n\n"
        f"## Findings\n\n"
        f"{findings_text}\n"
        f"---\n\n"
        f"## Notes\n\n"
        f"{result.get('notes') or '_(none)_'}\n"
    )


def run_review(task_id: str) -> dict:
    """Run the senior code review agent for the given task.

    Reads the git diff and implementation plan, reviews for architecture
    violations and non-negotiable rule breaches, auto-applies HIGH fixes,
    and writes a structured report.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.

    Returns:
        Dict with keys:
            - verdict: 'APPROVED', 'APPROVED_WITH_SUGGESTIONS', or 'REQUIRES_CHANGES'
            - high_findings: count of remaining unfixed HIGH findings
            - fixed: count of HIGH findings auto-applied
    """
    logger = get_logger(task_id)
    agent = _create_agent()

    plan_path = f"{_CONTEXT_DIR}/{task_id}_plan.md"
    plan_snippet = "(plan not available — reviewing diff only)"
    if _file_exists(plan_path):
        plan_content = _read_file(plan_path)
        plan_snippet = plan_content[:4000]
        logger.debug(
            f"Plan loaded: {len(plan_content)} chars (first 4000 passed to agent)",
            extra={"role": "review"},
        )
    else:
        logger.warning(f"Plan not found at {plan_path}", extra={"role": "review"})

    task_prompt = f"""
You are the Senior Code Reviewer for Blue Steel.
{_CODE_FORMAT_GUIDANCE}

## Task ID
{task_id}

## Implementation Plan (first 4000 chars for context)
{plan_snippet}

## Your Job

1. Call get_git_diff() to read everything changed on this branch vs main.
2. For each changed file, apply the rules from your persona systematically.
3. For HIGH findings you can fix by editing a source file, call write_project_file
   and set auto_fixed=True in that finding entry.
4. Compile all findings and return the result dict.

Focus on:
- Hexagonal architecture layer violations (ARCH-01 to ARCH-08)
- @Test methods missing @DisplayName (Java backend)
- Campaign role read from JWT instead of campaign_members DB (D-043)
- New endpoints missing authorization checks
- Liquibase changesets modified instead of new file added
- TypeScript 'any' types and unjustified type assertions
- Secrets or credentials in any file (D-050)

Do NOT flag formatting issues (Spotless and TypeScript handle those).
Do NOT write TODO, placeholder, or incomplete findings.
"""

    logger.debug("Agent prompt (truncated):\n%s", task_prompt[:800], extra={"role": "review"})
    raw = agent.run(task_prompt)
    # ascii() escapes non-ASCII chars (e.g. agent-emitted '→') for cp1252 console safety on Windows.
    logger.debug("Agent raw output: %s", ascii(raw)[:500], extra={"role": "review"})

    # Normalize response
    if isinstance(raw, dict):
        result = raw
    else:
        result = {
            "verdict": "REQUIRES_CHANGES",
            "high_findings": 0,
            "medium_findings": 0,
            "low_findings": 0,
            "fixed": 0,
            "findings": [],
            "notes": str(raw),
        }

    result.setdefault("verdict", "APPROVED")
    result.setdefault("high_findings", 0)
    result.setdefault("medium_findings", 0)
    result.setdefault("low_findings", 0)
    result.setdefault("fixed", 0)
    result.setdefault("findings", [])
    result.setdefault("notes", "")

    # Recompute verdict from counts if needed
    high_remaining = result["high_findings"]
    if high_remaining == 0 and (result.get("medium_findings", 0) + result.get("low_findings", 0)) > 0:
        result["verdict"] = "APPROVED_WITH_SUGGESTIONS"
    elif high_remaining == 0:
        result["verdict"] = "APPROVED"
    else:
        result["verdict"] = "REQUIRES_CHANGES"

    # Write report
    report_content = _build_report(task_id, result)
    report_path = f"{_CONTEXT_DIR}/{task_id}_review.md"
    _write_file(report_path, report_content)
    logger.debug(
        f"Review report written: {report_path} | verdict={result['verdict']} | "
        f"HIGH={result['high_findings']} | fixed={result['fixed']}",
        extra={"role": "review"},
    )

    return {
        "verdict": result["verdict"],
        "high_findings": result["high_findings"],
        "fixed": result["fixed"],
    }
