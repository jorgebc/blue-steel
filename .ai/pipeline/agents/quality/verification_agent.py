"""Verification agent for Blue Steel quality pipeline.

Runs quality checks in order:
  spotless -> mvn test -> (mvn verify if PIPELINE_RUN_INTEGRATION=true)
  -> npm type-check -> npm test -> npm audit

For each failing check (except npm audit and mvn verify), attempts up to 3
LLM-powered auto-fix iterations on source code only. Never touches test files
or config files. After 3 failed attempts, marks the check BLOCKED. If the
auto-fixer determines the root cause is a protected config file or a dependency
change, it stops early and marks the check BLOCKED (no point burning retries).

npm audit failures are BLOCKED immediately (CVEs require human intervention).
mvn verify failures are marked FAIL (infra-dependent, not auto-fixable).
A genuinely missing tool is BLOCKED (and documented in SETUP_NOTES.md) so it
fails the run loudly instead of silently passing — detection scans stderr only,
so a compiler's own "cannot find ..." diagnostics are never misread as missing.
The ONLY pass-neutral skip is mvn verify when PIPELINE_RUN_INTEGRATION is unset
(marked intentional=True); any other SKIPPED does not count as PASS.

Output: .ai/context/tasks/{task_id}_verification.md
Statuses: PASS | FAIL | BLOCKED | SKIPPED (SKIPPED only when intentional)
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

from smolagents import CodeAgent, LiteLLMModel, LogLevel, tool

from config import get_llm
from logger import MARKER_BLOCKED, get_logger
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

# Sentinel the auto-fix agent returns when the root cause is unfixable in source
# (lives in a protected config file or needs a dependency change). Lets the check
# stop after a single attempt instead of burning all retries on an impossible fix.
_BLOCKED_CONFIG_SENTINEL = "BLOCKED_PROTECTED_CONFIG:"

# Phrases that mean the shell could not launch the binary at all — i.e. the
# tool is genuinely not installed / not on PATH. These are emitted by the shell
# (cmd / pwsh / POSIX sh), NOT by the tool itself. We deliberately exclude
# generic substrings like "not found" / "cannot find" because compilers emit
# them as legitimate diagnostics (e.g. tsc's "Cannot find module 'axios'",
# "Cannot find name 'process'") — matching those misreads a real check failure
# as a missing tool. See SETUP_NOTES.md history for the regression this caused.
_MISSING_TOOL_PHRASES = (
    "is not recognized",  # Windows cmd: 'npm' is not recognized as an internal...
    "command not found",  # POSIX sh: npm: command not found
    "no such file or directory",  # exec failure when the binary path is wrong
)


def _is_protected_path(path: str) -> bool:
    p = path.lower()
    if any(p.endswith(s.lower()) for s in _TEST_SUFFIXES):
        return True
    if any(s.lower() in p for s in _CONFIG_SUBSTRINGS):
        return True
    return False


def _aggregate_verdict(checks: dict[str, dict]) -> tuple[bool, bool]:
    """Reduce per-check results to (passed, blocked).

    A check passes only if it is PASS or an *explicitly intentional* skip
    (currently just mvn_verify when integration tests are disabled). A plain
    SKIPPED no longer counts as PASS — a genuinely missing tool is BLOCKED (see
    the check runners), so it correctly fails the run instead of masking it.
    """
    blocked = any(c["status"] == "BLOCKED" for c in checks.values())
    passed = all(
        c["status"] == "PASS" or c.get("intentional") for c in checks.values()
    )
    return passed, blocked


def _is_tool_missing(result: dict) -> bool:
    """True only when the shell could not launch the tool (genuinely missing).

    Scans stderr ONLY: a missing executable is reported by the shell on stderr,
    whereas tools write their own diagnostics to stdout (tsc) or prefixed stderr
    lines like "npm error code 2". Scanning stdout here is what caused real
    compiler errors to be misclassified as a missing tool.
    """
    stderr = (result.get("stderr") or "").lower()
    return any(phrase in stderr for phrase in _MISSING_TOOL_PHRASES)


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
    # Auto-fix needs root-cause diagnosis, not just code transcription — use the
    # reasoning model rather than the code-completion model.
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


_PROMPTS_DIR = Path(__file__).parents[2] / "prompts"


def _load_persona(check_name: str) -> str:
    """Pick a Blue Steel engineering persona based on which check is failing.

    npm_typecheck and npm_test failures live in frontend code, so we load
    fe_engineer.md. Everything else (mvn_test, spotless) is backend.
    """
    fname = "fe_engineer.md" if check_name.startswith("npm_") else "be_engineer.md"
    try:
        return (_PROMPTS_DIR / fname).read_text(encoding="utf-8")
    except OSError:
        return ""


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


def _run_auto_fix_agent(check_name: str, error_output: str, task_id: str | None = None) -> str:
    """Run a CodeAgent to attempt a source-code fix for a failing check."""
    agent = CodeAgent(
        tools=[read_source_file, write_source_file, list_modified_source_files],
        model=_make_model(),
        max_steps=8,
        verbosity_level=LogLevel.OFF,  # ReAct trace -> silenced; story lives in the logger
    )
    persona = _load_persona(check_name)
    persona_block = (
        f"## Blue Steel Engineering Persona\n\n{persona}\n\n---\n"
        if persona else ""
    )
    prompt = f"""
{persona_block}You are a code fixer for Blue Steel. A quality check has failed and you must
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

If the root cause is NOT fixable in source code — because the real fix would
require editing a protected config file (package.json, tsconfig*, vite.config,
pom.xml, *.yml, *.xml, .env) or adding/removing a dependency — do NOT attempt a
workaround. Return exactly one line starting with the sentinel below, naming the
file(s) and the precise reason, so the pipeline can stop and report it:

    BLOCKED_PROTECTED_CONFIG: <which protected file/dependency and why it must change>

Example: `BLOCKED_PROTECTED_CONFIG: apps/web/tsconfig.json is invalid JSON (orphaned "types" key); src/api/client.ts imports 'axios' which is not a declared dependency.`

Otherwise, return a brief summary of what you fixed, or "No fix applied: <reason>".

```python
result = "Brief description of what was fixed, or 'No fix applied: <reason>', or 'BLOCKED_PROTECTED_CONFIG: <reason>'."
final_answer(result)
```
"""
    logger = get_logger(task_id) if task_id else None
    if logger:
        logger.debug(
            "Agent prompt (truncated):\n%s",
            prompt[:800],
            extra={"role": "verification"},
        )
    try:
        raw = agent.run(prompt)
        if logger:
            # ascii() escapes non-ASCII chars (e.g. agent-emitted '→') for cp1252 console safety.
            logger.debug(
                "Agent raw output: %s",
                ascii(raw)[:500],
                extra={"role": "verification"},
            )
        return str(raw)
    except Exception as exc:
        if logger:
            logger.debug(
                "Agent raised exception: %s",
                ascii(str(exc))[:500],
                extra={"role": "verification"},
            )
        return f"Auto-fix agent error: {exc}"


# ── Individual check runners ──────────────────────────────────────────────────


def _check_spotless(task_id: str) -> dict:
    """Spotless has a deterministic auto-fix (spotless:apply). Retry up to 3x."""
    logger = get_logger(task_id)
    error_output = ""
    for attempt in range(1, 4):
        logger.debug(f"[spotless] attempt {attempt}/3...", extra={"role": "verification"})
        result = _run_linter()

        if _is_tool_missing(result):
            error = (
                (result.get("stdout") or "") + (result.get("stderr") or "")
            ).strip()
            _document_missing_tool(task_id, "spotless (mvn)", error)
            return {
                "status": "BLOCKED",
                "attempts": 0,
                "error_output": (
                    "Tool not available: mvn is not installed or not on PATH. "
                    "The spotless check could not run — install Maven and re-run. "
                    "(See .ai/context/tasks/SETUP_NOTES.md.)"
                ),
            }

        if result["success"]:
            return {"status": "PASS", "attempts": attempt, "error_output": ""}

        error_output = (
            (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
        ).strip()

        if attempt < 3:
            logger.debug("[spotless] FAIL — running spotless:apply...", extra={"role": "verification"})
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
    logger = get_logger(task_id)
    error_output = ""
    for attempt in range(1, max_attempts + 1):
        logger.debug(f"[{name}] attempt {attempt}/{max_attempts}...", extra={"role": "verification"})
        result = run_fn()

        if _is_tool_missing(result):
            error = (
                (result.get("stdout") or "") + (result.get("stderr") or "")
            ).strip()
            _document_missing_tool(task_id, name, error)
            return {
                "status": "BLOCKED",
                "attempts": 0,
                "error_output": (
                    f"Tool not available: the '{name}' check could not run because "
                    "its tool is not installed or not on PATH. Install it and "
                    "re-run. (See .ai/context/tasks/SETUP_NOTES.md.)"
                ),
            }

        if result["success"]:
            return {"status": "PASS", "attempts": attempt, "error_output": ""}

        error_output = (
            (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
        ).strip()
        logger.debug(f"[{name}] FAIL (rc={result.get('returncode', '?')})", extra={"role": "verification"})

        if attempt < max_attempts:
            logger.debug(
                f"[{name}] attempting LLM auto-fix ({attempt}/{max_attempts - 1})...",
                extra={"role": "verification"},
            )
            fix_summary = _run_auto_fix_agent(name, error_output, task_id=task_id)
            logger.debug(f"[{name}] fix: {fix_summary[:120]}", extra={"role": "verification"})

            # Root cause is unfixable in source (protected config / dependency).
            # No point re-running run_fn or burning the remaining attempts.
            if fix_summary.lstrip().startswith(_BLOCKED_CONFIG_SENTINEL):
                reason = fix_summary.lstrip()[len(_BLOCKED_CONFIG_SENTINEL):].strip()
                logger.info(
                    f"{MARKER_BLOCKED} [{name}] root cause in protected config — "
                    "stopping early (auto-fix cannot touch config files).",
                    extra={"role": "verification"},
                )
                return {
                    "status": "BLOCKED",
                    "attempts": attempt,
                    "error_output": (
                        f"Root cause is in a protected config file — auto-fix "
                        f"cannot modify it. {reason}\n\n"
                        f"--- Original check output ---\n{error_output}"
                    ),
                }

    return {"status": "BLOCKED", "attempts": max_attempts, "error_output": error_output}


def _check_once_no_fix(task_id: str, name: str, run_fn) -> dict:
    """Run a check once without auto-fix; FAIL on failure (not BLOCKED)."""
    get_logger(task_id).debug(f"[{name}] running (no auto-fix)...", extra={"role": "verification"})
    result = run_fn()

    if _is_tool_missing(result):
        error = (
            (result.get("stdout") or "") + (result.get("stderr") or "")
        ).strip()
        _document_missing_tool(task_id, name, error)
        return {
            "status": "BLOCKED",
            "attempts": 0,
            "error_output": (
                f"Tool not available: the '{name}' check could not run because "
                "its tool is not installed or not on PATH. Install it and "
                "re-run. (See .ai/context/tasks/SETUP_NOTES.md.)"
            ),
        }

    if result["success"]:
        return {"status": "PASS", "attempts": 1, "error_output": ""}

    error_output = (
        (result.get("stdout") or "") + "\n" + (result.get("stderr") or "")
    ).strip()
    return {"status": "FAIL", "attempts": 1, "error_output": error_output}


def _check_block_on_fail(task_id: str, name: str, run_fn) -> dict:
    """Run a check once; BLOCKED on failure (fix requires human intervention)."""
    get_logger(task_id).debug(f"[{name}] running (block on failure)...", extra={"role": "verification"})
    result = run_fn()

    if _is_tool_missing(result):
        error = (
            (result.get("stdout") or "") + (result.get("stderr") or "")
        ).strip()
        _document_missing_tool(task_id, name, error)
        return {
            "status": "BLOCKED",
            "attempts": 0,
            "error_output": (
                f"Tool not available: the '{name}' check could not run because "
                "its tool is not installed or not on PATH. Install it and "
                "re-run. (See .ai/context/tasks/SETUP_NOTES.md.)"
            ),
        }

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
    logger = get_logger(task_id)

    run_integration = (
        os.environ.get("PIPELINE_RUN_INTEGRATION", "false").lower() == "true"
    )
    checks: dict[str, dict] = {}

    # 1. Spotless (backend formatting — deterministic fix via spotless:apply)
    logger.debug("[1/6] spotless:check (backend formatting)...", extra={"role": "verification"})
    checks["spotless"] = _check_spotless(task_id)

    # 2. mvn test (unit + ArchUnit — LLM auto-fix on source files)
    logger.debug("[2/6] mvn test (unit + ArchUnit)...", extra={"role": "verification"})
    checks["mvn_test"] = _check_with_llm_fix(task_id, "mvn_test", _run_tests_be)

    # 3. mvn verify (integration tests — infra-dependent, no auto-fix)
    if run_integration:
        logger.debug("[3/6] mvn verify (integration tests via Testcontainers)...", extra={"role": "verification"})
        checks["mvn_verify"] = _check_once_no_fix(
            task_id, "mvn_verify", _run_tests_integration
        )
    else:
        logger.debug(
            "[3/6] mvn verify — SKIPPED (set PIPELINE_RUN_INTEGRATION=true to enable)",
            extra={"role": "verification"},
        )
        checks["mvn_verify"] = {
            "status": "SKIPPED",
            "attempts": 0,
            "error_output": "",
            # The ONLY benign, pass-neutral skip: integration tests are opt-in.
            # Every other SKIPPED/BLOCKED counts against the verdict (see below).
            "intentional": True,
        }

    # 4. npm type-check (TypeScript — LLM auto-fix on .ts/.tsx source files)
    logger.debug("[4/6] npm run type-check (TypeScript)...", extra={"role": "verification"})
    checks["npm_typecheck"] = _check_with_llm_fix(
        task_id, "npm_typecheck", _run_typecheck
    )

    # 5. npm test (Vitest — LLM auto-fix on source files, not test files)
    logger.debug("[5/6] npm test (Vitest)...", extra={"role": "verification"})
    checks["npm_test"] = _check_with_llm_fix(task_id, "npm_test", _run_tests_fe)

    # 6. npm audit (CVE scan — BLOCKED on failure, cannot auto-fix without package.json)
    logger.debug("[6/6] npm audit (production CVE scan)...", extra={"role": "verification"})
    checks["npm_audit"] = _check_block_on_fail(task_id, "npm_audit", _run_audit)

    # Write report
    report_content = _build_report(task_id, checks)
    report_path = f"{_CONTEXT_DIR}/{task_id}_verification.md"
    _write_file(report_path, report_content)
    logger.debug(f"Verification report written: {report_path}", extra={"role": "verification"})

    # Compute overall result (see _aggregate_verdict for the PASS/SKIPPED rules).
    passed, blocked = _aggregate_verdict(checks)

    statuses = " | ".join(
        f"{k}={v['status']}" for k, v in checks.items()
    )
    summary = (
        f"Task {task_id} verification: "
        f"{'PASSED' if passed else 'FAILED'} | {statuses}"
    )

    logger.debug(
        f"Verification result: passed={passed}, blocked={blocked} | {statuses}",
        extra={"role": "verification"},
    )
    return {"passed": passed, "blocked": blocked, "summary": summary}
