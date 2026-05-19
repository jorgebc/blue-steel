"""Execution crew for Blue Steel AI pipeline.

Reads the plan produced by the planning phase, determines whether it has
backend and/or frontend scope, runs the appropriate engineer agents, and
writes a structured execution report.

Output: .ai/context/tasks/{task_id}_execution.md
"""

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


def _build_execution_report(
    task_id: str,
    be_result: dict | None,
    fe_result: dict | None,
    migration_filename: str | None,
    timestamp: str,
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

    # Backend section
    if be_result is not None:
        be_success = be_result.get("success", False)
        be_files = be_result.get("files_modified", [])
        be_notes = be_result.get("notes", "")
        lines += [
            "## Backend Engineer",
            "",
            f"**Status:** {'SUCCESS' if be_success else 'FAILED'}",
            "",
            "### Files Created / Modified",
            "",
        ]
        if be_files:
            for f in be_files:
                lines.append(f"- `{f}`")
        else:
            lines.append("_(none)_")
        lines += [
            "",
            "### Notes",
            "",
            be_notes or "_(no notes)_",
            "",
        ]
    else:
        lines += [
            "## Backend Engineer",
            "",
            "**Status:** SKIPPED (no backend scope detected in plan section 3)",
            "",
        ]

    lines += ["---", ""]

    # Frontend section
    if fe_result is not None:
        fe_success = fe_result.get("success", False)
        fe_files = fe_result.get("files_modified", [])
        fe_notes = fe_result.get("notes", "")
        lines += [
            "## Frontend Engineer",
            "",
            f"**Status:** {'SUCCESS' if fe_success else 'FAILED'}",
            "",
            "### Files Created / Modified",
            "",
        ]
        if fe_files:
            for f in fe_files:
                lines.append(f"- `{f}`")
        else:
            lines.append("_(none)_")
        lines += [
            "",
            "### Notes",
            "",
            fe_notes or "_(no notes)_",
            "",
        ]
    else:
        lines += [
            "## Frontend Engineer",
            "",
            "**Status:** SKIPPED (no frontend scope detected in plan section 3)",
            "",
        ]

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
    print(f"\n{'='*60}")
    print(f"Blue Steel Execution Crew — Task: {task_id}")
    print(f"{'='*60}\n")

    # ── Load plan ─────────────────────────────────────────────────────────────
    plan_path = f"{_PLAN_DIR}/{task_id}_plan.md"
    print(f"[1/5] Loading plan: {plan_path}")
    plan_content = read_file(plan_path)
    print(f"      Plan loaded: {len(plan_content)} chars")

    # ── Determine scope from section 3 ────────────────────────────────────────
    section_3 = _extract_section_3(plan_content)
    has_backend, has_frontend = _determine_scope(section_3)
    print(f"\n[2/5] Scope detection:")
    print(f"      Backend: {has_backend}")
    print(f"      Frontend: {has_frontend}")

    be_result: dict | None = None
    fe_result: dict | None = None

    # ── Run backend engineer first ────────────────────────────────────────────
    if has_backend:
        print(f"\n[3/5] Running Backend Engineer for {task_id}...")
        be_result = be_agent.run(task_id=task_id, plan_content=plan_content)
        be_ok = be_result.get("success", False)
        be_files = be_result.get("files_modified", [])
        print(f"      BE result: {'OK' if be_ok else 'FAILED'}")
        print(f"      BE files: {len(be_files)} modified")
    else:
        print("\n[3/5] Backend Engineer: skipped (no backend scope)")

    # ── Run frontend engineer ─────────────────────────────────────────────────
    if has_frontend:
        print(f"\n[4/5] Running Frontend Engineer for {task_id}...")
        fe_result = fe_agent.run(task_id=task_id, plan_content=plan_content)
        fe_ok = fe_result.get("success", False)
        fe_files = fe_result.get("files_modified", [])
        print(f"      FE result: {'OK' if fe_ok else 'FAILED'}")
        print(f"      FE files: {len(fe_files)} modified")
    else:
        print("\n[4/5] Frontend Engineer: skipped (no frontend scope)")

    # ── Assemble summary ──────────────────────────────────────────────────────
    all_files = (
        (be_result.get("files_modified", []) if be_result else [])
        + (fe_result.get("files_modified", []) if fe_result else [])
    )
    migration_filename = _extract_migration_filename(all_files)
    timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # ── Write execution report ─────────────────────────────────────────────────
    print(f"\n[5/5] Writing execution report...")
    report_content = _build_execution_report(
        task_id=task_id,
        be_result=be_result,
        fe_result=fe_result,
        migration_filename=migration_filename,
        timestamp=timestamp,
    )
    report_path = f"{_EXECUTION_DIR}/{task_id}_execution.md"
    write_file(report_path, report_content)
    print(f"      Report written: {report_path}")

    be_files = be_result.get("files_modified", []) if be_result else []
    fe_files = fe_result.get("files_modified", []) if fe_result else []

    summary = {
        "be_files": be_files,
        "fe_files": fe_files,
        "migration_filename": migration_filename,
        "be_success": be_result.get("success", True) if be_result else True,
        "fe_success": fe_result.get("success", True) if fe_result else True,
        "report_path": report_path,
    }

    print(f"\nDone. Execution report: {report_path}")
    return summary
