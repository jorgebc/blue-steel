"""Verification agent for Blue Steel quality pipeline.

Runs quality checks in order:
  spotless -> mvn test -> (mvn verify if PIPELINE_RUN_INTEGRATION=true)
  -> npm type-check -> npm test -> npm audit

For each failing check (except npm audit and mvn verify), attempts up to 3
LLM-powered auto-fix iterations on source code only. Never touches test files
or config files. After 3 failed attempts, marks the check BLOCKED.

npm audit failures are BLOCKED immediately (CVEs require human intervention).
mvn verify failures are marked FAIL (infra-dependent, not auto-fixable).
Missing tools are SKIPPED and documented in .ai/context/tasks/SETUP_NOTES.md.

Output: .ai/context/tasks/{task_id}_verification.md
Statuses: PASS | FAIL | BLOCKED | SKIPPED
"""

import os
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

from smolagents import CodeAgent, LiteLLMModel, tool

from config import get_llm
from tools.filesystem import (
    REPO_ROOT,
    get_modified_files as _get_modified_files,
    read_file as _read_file,
    write_file as _write_file,
)
from tools.shell_runner import (
    run_command as _run_command,
    run_linter_backend as _run_linter,
    run_security_audit_frontend as _run_audit,
    run_tests_backend as _run_tests_be,
    run_tests_frontend as _run_tests_fe,
    run_tests_integration_backend as _run_tests_integration,
    run_typecheck_frontend as _run_typecheck,
)

_CONTEXT_DIR = ".ai/context/tasks"
_API_ROOT = REPO_ROOT / "apps" / "api"

# Patterns that identify test files — never auto-fix these
_TEST_SUFFIXES = (
    "Test.java",
    "IT.java",
    ".test.ts",
    ".test.tsx",
    ".spec.ts",
    ".spec.tsx",
)

# Substrings that identify config files — never auto-fix these
_CONFIG_SUBSTRINGS = (
    "package.json",
    "package-lock.json",
    "tsconfig",
    "vite.config",
    "application.yml",
    "application-",
    "pom.xml",
    "db/changelog/",
    ".xml",
    ".yaml",
    ".yml",
    ".env",
)

# Phrases indicating the command binary is not installed
_MISSING_TOOL_PHRASES = (
    "is not recognized",
    "command not found",
    "no such file or directory",
    "not found",
    "cannot find",
)


def _is_protected_path(path: str) -> bool:
    p = path.lower()
    if any(p.endswith(s.lower()) for s in _TEST_SUFFIXES):
        return True
    if any(s.lower() in p for s in _CONFIG_SUBSTRINGS):
        return True
    return False


def _is_tool_missing(result: dict) -> bool:
    combined = (
        (result.get("stdout") or "") + " " + (result.get("stderr") or "")
    ).lower()
    return any(phrase in combined for phrase in _MISSING_TOOL_PHRASES)


def _document_missing_tool(task_id: str, check_name: str, error: str) -> None:
    notes_path = f"{_CONTEXT_DIR}/SETUP_NOTES.md"
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    entry = (
        f"\n## Missing Tool: {check_name} (task {task_id}, {ts})\n\n"
        f"The `{check_name}` check could not run because a required tool "
        f"is not installed or not on PATH.\n\n"
        f"**Error snippet:**\n```\n{error[:500]}\n```\n\n"
        f"**Action required:** Install the missing tool and re-run the pipeline.\n"
    )
    try:
        existing = _read_file(notes_path)
    except FileNotFoundError:
        existing = "# Pipeline Setup Notes\n\nTool gaps discovered during verification runs.\n"
    _write_file(notes_path, existing + entry)


def _make_model() -> LiteLLMModel:
    llm_params = get_llm(phase="execution")
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


@tool
def read_source_file(path: str) -> str:
    """Read a Blue Steel source file by repo-relative path.

    Args:
        path: File path relative to the repo root.

    Returns:
        File content as a UTF-8 string.
    """
    try:
        return _read_file(path)
    except FileNotFoundError:
        return f"[File not found: {path}]"


@tool
def write_source_file(path: str, content: str) -> str:
    """Write a fix to a Blue Steel source file. Rejects test and config files.

    Cannot write to files ending in Test.java, IT.java, .test.ts, .test.tsx,
    .spec.ts, .spec.tsx, or to package.json, tsconfig*, pom.xml, *.yaml, *.xml,
    db/changelog/, or .env files.

    Args:
        path: File path relative to the repo root.
        content: Fixed file content (UTF-8).

    Returns:
        Confirmation string or raises PermissionError for protected paths.
    """
    if _is_protected_path(path):
        raise PermissionError(
            f"Auto-fix cannot modify test or config files: {path}"
        )
    _write_file(path, content)
    return f"Fixed: {path}"


@tool
def list_modified_source_files() -> list:
    """List files modified on the current branch relative to main.

    Returns:
        List of repo-relative file paths changed on this branch.
    """
    return _get_modified_files(base="main")


def _run_auto_fix_agent(check_name: str, error_output: str) -> str:
    """Run a CodeAgent to attempt a source-code fix for a failing check."""
    agent = CodeAgent(
        tools=[read_source_file, write_source_file, list_modified_source_files],
        model=_make_model(),
        max_steps=8,
    )
    prompt = f"""
You are a code fixer for Blue Steel. A quality check has failed and you must
attempt to fix the root cause by editing source files.

## Failing Check
{check_name}

## Error Output
{error_output[:3000]}

## Instructions
1. Call list_modified_source_files() to see which files changed on this branch.
2. For each relevant file, call read_source_file(path) to examine it.
3. Identify the root cause from the error output.
4. Apply the minimal fix using write_source_file(path, fixed_content).

Hard constraints — NEVER modify:
- Test files: files ending in Test.java, IT.java, .test.ts, .test.tsx, .spec.ts, .spec.tsx
- Config files: package.json, tsconfig*, vite.config*, pom.xml, application*.yml, *.xml, *.env
- Liquibase changelogs: any path containing db/changelog/
- shadcn/ui components: apps/web/src/components/ui/

Return a brief summary of what you fixed, or "No fix applied" if the issue is
not in source code (e.g., infrastructure not available).

```python
result = "Brief description of what was fixed, or 'No fix applied: <reason>'."
final_answer(result)
```
"""
    try:
        return str(agent.run(prompt))
    except Exception as exc:
        return f"Auto-fix agent error: {exc}"


# ── Individual check runners ──────────────────────────────────────────────────


def _check_spotless(task_id: str) -> dict:
    """Spotless has a deterministic auto-fix (spotless:apply). Retry up to 3x."""
    error_output = ""
    for attempt in range(1, 4):
        print(f"    [spotless] Attempt {attempt}/3...")
        result = _run_linter()

        if _is_tool_missing(result):
            error = (
                (result.get("stdout") or "") + (result.get("stderr") or "")
            ).strip()
            _document_missing_tool(task_id, "spotless (mvn)", error)
            return {"status": "SKIPPED", "attempts": 0, "error_output": "Tool not available: mvn"}

        if result["success"]:
            return {"status": "PASS", "attempts": attempt, "error_output": ""}

        error_output = (
            (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
        ).strip()

        if attempt < 3:
            print(f"    [spotless] FAIL — running spotless:apply...")
            apply = _run_command(
                "mvn spotless:apply",
                cwd=str(_API_ROOT),
                timeout=120,
            )
            if not apply["success"]:
                return {
                    "status": "BLOCKED",
                    "attempts": attempt,
                    "error_output": error_output,
                }

    return {"status": "BLOCKED", "attempts": 3, "error_output": error_output}


def _check_with_llm_fix(task_id: str, name: str, run_fn, max_attempts: int = 3) -> dict:
    """Run a check with LLM-powered auto-fix, retrying up to max_attempts times."""
    error_output = ""
    for attempt in range(1, max_attempts + 1):
        print(f"    [{name}] Attempt {attempt}/{max_attempts}...")
        result = run_fn()

        if _is_tool_missing(result):
            error = (
                (result.get("stdout") or "") + (result.get("stderr") or "")
            ).strip()
            _document_missing_tool(task_id, name, error)
            return {"status": "SKIPPED", "attempts": 0, "error_output": "Tool not available: " + name}

        if result["success"]:
            return {"status": "PASS", "attempts": attempt, "error_output": ""}

        error_output = (
            (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
        ).strip()
        print(f"    [{name}] FAIL (rc={result.get('returncode', '?')})")

        if attempt < max_attempts:
            print(f"    [{name}] Attempting LLM auto-fix ({attempt}/{max_attempts - 1})...")
            fix_summary = _run_auto_fix_agent(name, error_output)
            print(f"    [{name}] Fix: {fix_summary[:120]}")

    return {"status": "BLOCKED", "attempts": max_attempts, "error_output": error_output}


def _check_once_no_fix(task_id: str, name: str, run_fn) -> dict:
    """Run a check once without auto-fix; FAIL on failure (not BLOCKED)."""
    print(f"    [{name}] Running (no auto-fix)...")
    result = run_fn()

    if _is_tool_missing(result):
        error = (
            (result.get("stdout") or "") + (result.get("stderr") or "")
        ).strip()
        _document_missing_tool(task_id, name, error)
        return {"status": "SKIPPED", "attempts": 0, "error_output": "Tool not available: " + name}

    if result["success"]:
        return {"status": "PASS", "attempts": 1, "error_output": ""}

    error_output = (
        (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
    ).strip()
    return {"status": "FAIL", "attempts": 1, "error_output": error_output}


def _check_block_on_fail(task_id: str, name: str, run_fn) -> dict:
    """Run a check once; BLOCKED on failure (fix requires human intervention)."""
    print(f"    [{name}] Running (block on failure)...")
    result = run_fn()

    if _is_tool_missing(result):
        error = (
            (result.get("stdout") or "") + (result.get("stderr") or "")
        ).strip()
        _document_missing_tool(task_id, name, error)
        return {"status": "SKIPPED", "attempts": 0, "error_output": "Tool not available: " + name}

    if result["success"]:
        return {"status": "PASS", "attempts": 1, "error_output": ""}

    error_output = (
        (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
    ).strip()
    return {"status": "BLOCKED", "attempts": 1, "error_output": error_output}


# ── Report builder ─────────────────────────────────────────────────────────────


def _build_report(task_id: str, checks: dict[str, dict]) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        f"# Verification Report: {task_id}",
        "",
        f"**Generated:** {ts}",
        "",
        "---",
        "",
        "## Check Results",
        "",
        "| Check | Status | Attempts |",
        "|-------|--------|----------|",
    ]
    for name, info in checks.items():
        lines.append(
            f"| {name} | {info['status']} | {info.get('attempts', '-')} |"
        )
    lines += ["", "---", ""]

    for name, info in checks.items():
        lines += [
            f"## {name}",
            "",
            f"**Status:** {info['status']}",
            f"**Attempts:** {info.get('attempts', '-')}",
        ]
        err = (info.get("error_output") or "").strip()
        if err:
            lines += [
                "",
                "**Error output (truncated to 1000 chars):**",
                "```",
                err[:1000],
                "```",
            ]
        lines.append("")

    return "\n".join(lines)


# ── Public API ─────────────────────────────────────────────────────────────────


def run_verification(task_id: str) -> dict:
    """Run all verification checks for the given task.

    Checks run in order:
      spotless -> mvn test -> (mvn verify if PIPELINE_RUN_INTEGRATION=true)
      -> npm type-check -> npm test -> npm audit

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.

    Returns:
        Dict with keys:
            - passed: True if all non-skipped checks are PASS
            - blocked: True if any check is BLOCKED
            - summary: plain-English result summary
    """
    print(f"\n{'='*60}")
    print(f"Blue Steel Verification Agent — Task: {task_id}")
    print(f"{'='*60}\n")

    run_integration = (
        os.environ.get("PIPELINE_RUN_INTEGRATION", "false").lower() == "true"
    )
    checks: dict[str, dict] = {}

    # 1. Spotless (backend formatting — deterministic fix via spotless:apply)
    print("[1/6] spotless:check (backend formatting)...")
    checks["spotless"] = _check_spotless(task_id)

    # 2. mvn test (unit + ArchUnit — LLM auto-fix on source files)
    print("\n[2/6] mvn test (unit + ArchUnit)...")
    checks["mvn_test"] = _check_with_llm_fix(task_id, "mvn_test", _run_tests_be)

    # 3. mvn verify (integration tests — infra-dependent, no auto-fix)
    if run_integration:
        print("\n[3/6] mvn verify (integration tests via Testcontainers)...")
        checks["mvn_verify"] = _check_once_no_fix(
            task_id, "mvn_verify", _run_tests_integration
        )
    else:
        print("\n[3/6] mvn verify — SKIPPED (set PIPELINE_RUN_INTEGRATION=true to enable)")
        checks["mvn_verify"] = {
            "status": "SKIPPED",
            "attempts": 0,
            "error_output": "",
        }

    # 4. npm type-check (TypeScript — LLM auto-fix on .ts/.tsx source files)
    print("\n[4/6] npm run type-check (TypeScript)...")
    checks["npm_typecheck"] = _check_with_llm_fix(
        task_id, "npm_typecheck", _run_typecheck
    )

    # 5. npm test (Vitest — LLM auto-fix on source files, not test files)
    print("\n[5/6] npm test (Vitest)...")
    checks["npm_test"] = _check_with_llm_fix(task_id, "npm_test", _run_tests_fe)

    # 6. npm audit (CVE scan — BLOCKED on failure, cannot auto-fix without package.json)
    print("\n[6/6] npm audit (production CVE scan)...")
    checks["npm_audit"] = _check_block_on_fail(task_id, "npm_audit", _run_audit)

    # Write report
    report_content = _build_report(task_id, checks)
    report_path = f"{_CONTEXT_DIR}/{task_id}_verification.md"
    _write_file(report_path, report_content)
    print(f"\nVerification report written: {report_path}")

    # Compute overall result
    blocked = any(c["status"] == "BLOCKED" for c in checks.values())
    passed = all(c["status"] in ("PASS", "SKIPPED") for c in checks.values())

    statuses = " | ".join(
        f"{k}={v['status']}" for k, v in checks.items()
    )
    summary = (
        f"Task {task_id} verification: "
        f"{'PASSED' if passed else 'FAILED'} | {statuses}"
    )

    print(f"\nResult: passed={passed}, blocked={blocked}")
    return {"passed": passed, "blocked": blocked, "summary": summary}
