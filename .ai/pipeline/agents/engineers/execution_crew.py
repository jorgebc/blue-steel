"""Execution crew for Blue Steel AI pipeline.

Reads the plan produced by the planning phase, determines whether it has
backend and/or frontend scope, runs the appropriate engineer agents, and
writes a structured execution report.

Output: .ai/context/tasks/{task_id}_execution.md
"""

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

# ── Windows Unicode fix ────────────────────────────────────────────────────────
if sys.platform == "win32":
    try:
        import ctypes
        import ctypes.wintypes
        _ENABLE_VT = 0x0004
        _STD_OUT = -11
        _k32 = ctypes.windll.kernel32
        _handle = _k32.GetStdHandle(_STD_OUT)
        _mode = ctypes.wintypes.DWORD()
        _k32.GetConsoleMode(_handle, ctypes.byref(_mode))
        _k32.SetConsoleMode(_handle, _mode.value | _ENABLE_VT)
    except Exception:
        pass
    try:
        from rich._win32_console import LegacyWindowsTerm as _LWT
        _orig_write = _LWT.write_text
        def _safe_write(self, text: str) -> None:
            try:
                _orig_write(self, text)
            except (UnicodeEncodeError, UnicodeDecodeError):
                _orig_write(self, text.encode("cp1252", errors="replace").decode("cp1252"))
        _LWT.write_text = _safe_write
    except ImportError:
        pass

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from tools.filesystem import read_file, write_file, REPO_ROOT
from tools.shell_runner import install_frontend_dependencies
from logger import MARKER_FAIL, MARKER_INFO, MARKER_OK, get_logger

import be_agent
import fe_agent

_PLAN_DIR = ".ai/context/tasks"
_EXECUTION_DIR = ".ai/context/tasks"

# Patterns that indicate backend scope in section 3 of the plan
_BE_INDICATORS = [
    "apps/api/",
    "com.bluesteel.",
    "db/changelog/",
    ".java",
    "Spring Boot",
    "Liquibase",
    "JPA",
]

# Patterns that indicate frontend scope in section 3 of the plan
_FE_INDICATORS = [
    "apps/web/",
    ".tsx",
    ".ts",
    "React",
    "TanStack",
    "Zustand",
    "shadcn",
    "features/",
    "components/domain/",
]


def _extract_section_3(plan_content: str) -> str:
    """Extract Section 3 (Proposed Technical Solution) from the plan markdown."""
    # Match "## 3." up to the next "## " heading or end of file
    match = re.search(
        r"(##\s*3\..*?)(?=\n##\s*\d+\.|\Z)",
        plan_content,
        re.DOTALL | re.IGNORECASE,
    )
    return match.group(1).strip() if match else plan_content


def _determine_scope(section_3: str) -> tuple[bool, bool]:
    """Return (has_backend, has_frontend) based on indicators in section 3."""
    has_backend = any(indicator in section_3 for indicator in _BE_INDICATORS)
    has_frontend = any(indicator in section_3 for indicator in _FE_INDICATORS)
    return has_backend, has_frontend


def _extract_migration_filename(files: list[str]) -> str | None:
    """Return the Liquibase migration filename if one was written, else None."""
    for path in files:
        if "db/changelog/" in path and path.endswith(".xml"):
            return Path(path).name
    return None


# Plans declare a genuinely-needed new library with a parseable line:
#   NEW DEPENDENCY (frontend): axios — justification...
# The pipeline never commits, so a human reviews every dependency before merge;
# we therefore install declared frontend deps autonomously rather than stopping.
_NEW_DEP_RE = re.compile(
    r"NEW DEPENDENCY\s*\(\s*(frontend|backend)\s*\)\s*:\s*(\S+)",
    re.IGNORECASE,
)


def _parse_new_dependencies(plan_content: str) -> dict[str, list[str]]:
    """Return declared new dependencies grouped by scope: {'frontend': [...], 'backend': [...]}."""
    deps: dict[str, list[str]] = {"frontend": [], "backend": []}
    for match in _NEW_DEP_RE.finditer(plan_content):
        scope = match.group(1).lower()
        pkg = match.group(2).strip().strip("`'\".,;")
        if pkg and pkg not in deps[scope]:
            deps[scope].append(pkg)
    return deps


def _pkg_name(spec: str) -> str:
    """Strip a version range from an npm spec: 'react@^19' -> 'react', '@scope/x@1' -> '@scope/x'."""
    if spec.startswith("@"):
        scope_name, sep, _version = spec[1:].partition("@")
        return f"@{scope_name}" if sep else spec
    return spec.split("@", 1)[0]


def _missing_frontend_deps(declared: list[str]) -> list[str]:
    """Return the declared frontend specs whose package is absent from apps/web/package.json."""
    if not declared:
        return []
    try:
        pkg_json = json.loads(read_file("apps/web/package.json"))
    except (FileNotFoundError, json.JSONDecodeError):
        return list(declared)
    installed = set(pkg_json.get("dependencies", {})) | set(
        pkg_json.get("devDependencies", {})
    )
    return [spec for spec in declared if _pkg_name(spec) not in installed]


def _install_declared_frontend_deps(declared: list[str], logger) -> list[str]:
    """Pre-flight: install any declared frontend dep missing from package.json.

    Returns the specs actually installed (for the execution report). Logged but
    non-fatal on failure — if the install fails, the engineer's circuit breaker
    catches the resulting missing-module error with concrete output.
    """
    missing = _missing_frontend_deps(declared)
    if not missing:
        return []
    logger.info(
        f"{MARKER_INFO} Installing plan-declared frontend dependencies: {missing}",
        extra={"role": "fe_engineer"},
    )
    result = install_frontend_dependencies(missing)
    installed = result.get("installed", [])
    if result.get("success"):
        logger.info(
            f"{MARKER_OK} Installed: {installed}", extra={"role": "fe_engineer"}
        )
    else:
        logger.error(
            f"{MARKER_FAIL} Dependency install failed (rc={result.get('returncode')}): "
            f"{result.get('stderr', '').strip()[:500]}",
            extra={"role": "fe_engineer"},
        )
    return installed


def _engineer_section(title: str, role: str, result: dict | None, skip_note: str) -> list[str]:
    """Build one engineer's report section, including Failure Diagnostics when it failed."""
    if result is None:
        return [f"## {title}", "", f"**Status:** {skip_note}", ""]

    success = result.get("success", False)
    files = result.get("files_modified", [])
    notes = result.get("notes", "")
    lines = [
        f"## {title}",
        "",
        f"**Status:** {'SUCCESS' if success else 'FAILED'}",
        "",
        "### Files Created / Modified",
        "",
    ]
    lines += [f"- `{f}`" for f in files] if files else ["_(none)_"]
    lines += ["", "### Notes", "", notes or "_(no notes)_", ""]

    # Concrete check output — the real tsc/eslint/mvn failure, not the LLM's prose.
    diagnostics = result.get("diagnostics")
    if not success and diagnostics:
        lines += ["### Failure Diagnostics", "", diagnostics, ""]
    return lines


def _build_execution_report(
    task_id: str,
    be_result: dict | None,
    fe_result: dict | None,
    migration_filename: str | None,
    timestamp: str,
    installed_deps: list[str] | None = None,
) -> str:
    """Assemble the execution report markdown."""
    lines = [
        f"# Execution Report: {task_id}",
        "",
        f"**Generated:** {timestamp}",
        f"**Task:** {task_id}",
        "",
        "---",
        "",
    ]

    lines += _engineer_section(
        "Backend Engineer",
        "be_engineer",
        be_result,
        "SKIPPED (no backend scope detected in plan section 3)",
    )
    lines += ["---", ""]

    lines += _engineer_section(
        "Frontend Engineer",
        "fe_engineer",
        fe_result,
        "SKIPPED (no frontend scope detected in plan section 3)",
    )
    lines += ["---", ""]

    # Installed dependencies — the review point for any new library introduced.
    lines += ["## Installed Dependencies", ""]
    if installed_deps:
        lines += [f"- `{dep}`" for dep in installed_deps]
        lines += [
            "",
            "_Declared as NEW DEPENDENCY in the plan and installed during the "
            "pre-flight. Review before commit._",
            "",
        ]
    else:
        lines += ["_None_", ""]

    lines += ["---", ""]

    # DB migration summary
    lines += [
        "## DB Migration",
        "",
    ]
    if migration_filename:
        lines.append(f"**Created:** `{migration_filename}`")
    else:
        lines.append("**Created:** None")
    lines.append("")

    # Overall summary
    be_ok = be_result is None or be_result.get("success", False)
    fe_ok = fe_result is None or fe_result.get("success", False)
    overall = "SUCCESS" if (be_ok and fe_ok) else "FAILED"

    lines += [
        "---",
        "",
        "## Overall",
        "",
        f"**Result:** {overall}",
        "",
    ]

    return "\n".join(lines)


def run_execution(task_id: str) -> dict:
    """Run BE and FE engineer agents for the given task_id.

    Reads .ai/context/tasks/{task_id}_plan.md, determines scope,
    runs engineer agents, writes the execution report, and returns
    a summary dict.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.

    Returns:
        Dict with keys:
            - be_files: list of backend files modified
            - fe_files: list of frontend files modified
            - migration_filename: str or None
            - be_success: bool
            - fe_success: bool
            - report_path: path to the written execution report
    """
    logger = get_logger(task_id)

    # ── Load plan ─────────────────────────────────────────────────────────────
    plan_path = f"{_PLAN_DIR}/{task_id}_plan.md"
    plan_content = read_file(plan_path)
    logger.debug(
        f"Plan loaded: {plan_path} ({len(plan_content)} chars)",
        extra={"role": "execution"},
    )

    # ── Determine scope from section 3 ────────────────────────────────────────
    section_3 = _extract_section_3(plan_content)
    has_backend, has_frontend = _determine_scope(section_3)
    logger.info(
        f"{MARKER_INFO} Scope: backend={has_backend}, frontend={has_frontend}",
        extra={"role": "execution"},
    )

    # ── New-dependency pre-flight ─────────────────────────────────────────────
    # Install plan-declared frontend dependencies before the engineer runs, so a
    # genuinely-needed new library does not surface as an unrecoverable
    # missing-module error. Backend deps are added to pom.xml by the BE engineer.
    new_deps = _parse_new_dependencies(plan_content)
    installed_deps: list[str] = []
    if has_frontend:
        installed_deps = _install_declared_frontend_deps(new_deps["frontend"], logger)

    be_result: dict | None = None
    fe_result: dict | None = None

    # ── Run backend engineer first ────────────────────────────────────────────
    if has_backend:
        logger.info(f"{MARKER_INFO} Backend Engineer working...", extra={"role": "be_engineer"})
        be_result = be_agent.run(task_id=task_id, plan_content=plan_content)
        be_ok = be_result.get("success", False)
        be_files = be_result.get("files_modified", [])
        marker = MARKER_OK if be_ok else MARKER_FAIL
        logger.info(
            f"{marker} Backend Engineer: {'OK' if be_ok else 'FAILED'} "
            f"({len(be_files)} file(s) modified)",
            extra={"role": "be_engineer"},
        )
    else:
        logger.info(f"{MARKER_INFO} Backend Engineer: skipped (no backend scope)", extra={"role": "be_engineer"})

    # ── Run frontend engineer ─────────────────────────────────────────────────
    if has_frontend:
        logger.info(f"{MARKER_INFO} Frontend Engineer working...", extra={"role": "fe_engineer"})
        fe_result = fe_agent.run(task_id=task_id, plan_content=plan_content)
        fe_ok = fe_result.get("success", False)
        fe_files = fe_result.get("files_modified", [])
        marker = MARKER_OK if fe_ok else MARKER_FAIL
        logger.info(
            f"{marker} Frontend Engineer: {'OK' if fe_ok else 'FAILED'} "
            f"({len(fe_files)} file(s) modified)",
            extra={"role": "fe_engineer"},
        )
    else:
        logger.info(f"{MARKER_INFO} Frontend Engineer: skipped (no frontend scope)", extra={"role": "fe_engineer"})

    # ── Assemble summary ──────────────────────────────────────────────────────
    all_files = (
        (be_result.get("files_modified", []) if be_result else [])
        + (fe_result.get("files_modified", []) if fe_result else [])
    )
    migration_filename = _extract_migration_filename(all_files)
    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # ── Write execution report ─────────────────────────────────────────────────
    report_content = _build_execution_report(
        task_id=task_id,
        be_result=be_result,
        fe_result=fe_result,
        migration_filename=migration_filename,
        timestamp=timestamp,
        installed_deps=installed_deps,
    )
    report_path = f"{_EXECUTION_DIR}/{task_id}_execution.md"
    write_file(report_path, report_content)
    logger.debug(f"Execution report written: {report_path}", extra={"role": "execution"})

    be_files = be_result.get("files_modified", []) if be_result else []
    fe_files = fe_result.get("files_modified", []) if fe_result else []

    # Concrete failure output from whichever engineer failed — surfaced up to the
    # orchestrator so error.md names the real error, not just file counts.
    failure_diagnostics = ""
    for result in (be_result, fe_result):
        if result and not result.get("success", True) and result.get("diagnostics"):
            failure_diagnostics = result["diagnostics"]

    summary = {
        "be_files": be_files,
        "fe_files": fe_files,
        "migration_filename": migration_filename,
        "be_success": be_result.get("success", True) if be_result else True,
        "fe_success": fe_result.get("success", True) if fe_result else True,
        "installed_deps": installed_deps,
        "failure_diagnostics": failure_diagnostics,
        "report_path": report_path,
    }

    return summary
