"""Planning crew for Blue Steel AI pipeline.

Orchestrates a PO ↔ Architect conversation to produce a project-specific
implementation plan for any Blue Steel roadmap task.

Conversation flow:
  1. PO Round 1 — scope + acceptance criteria
  2. Architect Round 1 — technical proposal with real file paths
  3. PO Round 2 — targeted challenge (scope, UX, acceptance criteria coverage)
  4. Architect Round 2 — final plan (all 8 sections)

Output: .ai/context/tasks/{task_id}_plan.md
"""

import os
import re
import sys
from pathlib import Path

# ── Windows Unicode fix ───────────────────────────────────────────────────────
# Rich's legacy Windows console renderer cannot handle Unicode characters
# (e.g., arrows → from LLM output) because it writes via cp1252. Fix: enable
# VT processing so Rich uses the modern console path, OR patch write_text to
# replace unencodable characters rather than crashing.
if sys.platform == "win32":
    try:
        # Try enabling VT processing (modern Windows Terminal supports this)
        import ctypes
        import ctypes.wintypes
        _ENABLE_VT = 0x0004
        _STD_OUT = -11
        _k32 = ctypes.windll.kernel32
        _handle = _k32.GetStdHandle(_ENABLE_VT)
        _mode = ctypes.wintypes.DWORD()
        _k32.GetConsoleMode(_handle, ctypes.byref(_mode))
        _k32.SetConsoleMode(_handle, _mode.value | _ENABLE_VT)
    except Exception:
        pass
    # Patch LegacyWindowsTerm.write_text to silently replace unencodable characters
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

from logger import MARKER_INFO, MARKER_OK, get_logger
from tools.filesystem import read_file, write_file

import po_agent
import architect_agent

_DOCS = {
    "prd": "docs/PRD.md",
    # Active roadmap file — on version rollover, update per docs/roadmap/README.md
    "roadmap": "docs/roadmap/ROADMAP_V2.md",
    "architecture": "docs/ARCHITECTURE.md",
    "decisions": "docs/DECISIONS.md",
}

_PLAN_DIR = ".ai/context/tasks"

_UNICODE_REPLACEMENTS = {
    "→": "->",   # →
    "←": "<-",   # ←
    "✔": "[x]",  # ✔
    "✓": "[x]",  # ✓
    "❌": "[!]",  # ❌
    "⛔": "[!]",  # ⛔
    "\U0001f7e5": "[ ]",   # 🟥
    "\U0001f7e6": "[ ]",   # 🟦
    "\U0001f7e7": "[ ]",   # 🟧
    "\U0001f7e8": "[ ]",   # 🟨
    "\U0001f7e9": "[x]",   # 🟩
    "\U0001f7ea": "[ ]",   # 🟪
    "\U0001f7eb": "[ ]",   # 🟫
    "\U0001f532": "[ ]",   # 🔲  (not-started)
    "\U0001f533": "[x]",   # 🔳  (done)
    "✅": "[x]",  # ✅
    "☑": "[x]",  # ☑
    "\U0001f504": "[~]",   # 🔄  (in-progress)
}


def _ascii_safe(text: str) -> str:
    """Replace non-ASCII characters that crash the Windows legacy console with ASCII equivalents."""
    for char, replacement in _UNICODE_REPLACEMENTS.items():
        text = text.replace(char, replacement)
    # Replace any remaining non-ASCII chars with '?'
    return text.encode("ascii", errors="replace").decode("ascii")


_PLAN_SECTIONS = [
    "## 1. Executive Summary",
    "## 2. Acceptance Criteria",
    "## 3. Proposed Technical Solution",
    "## 4. Dependencies on Existing Blue Steel Code",
    "## 5. New or Modified API Contracts",
    "## 6. DB Migration Required",
    "## 7. Identified Risks",
    "## 8. Explicitly Out of Scope",
]

# A numbered section heading: "## 3." / "##  3.  Proposed ...". The architect
# reliably emits the eight *numbers* but its title wording drifts (e.g. "## 4.
# Dependencies on Existing Code" instead of "...Existing Blue Steel Code"). We key
# structure detection on the number, not the title text, and rewrite titles to
# canonical so downstream parsers (_extract_section_3, _determine_scope) are stable.
_HEADING_RE = re.compile(r"^##\s*([1-8])\.[^\n]*", re.MULTILINE)


def _normalize_section_titles(text: str) -> str:
    """Rewrite every '## N. <anything>' heading to the canonical _PLAN_SECTIONS title."""
    return _HEADING_RE.sub(lambda m: _PLAN_SECTIONS[int(m.group(1)) - 1], text)


def _planning_conversation(arch_output_1: str, po_output_2: str) -> str:
    """The appendix capturing the PO <-> Architect exchange, appended to every plan."""
    return (
        "## Planning Conversation\n\n"
        "### Architect — Initial Proposal\n\n"
        f"{arch_output_1}\n\n---\n\n"
        "### PO — Challenge\n\n"
        f"{po_output_2}\n"
    )


def _load_docs() -> dict[str, str]:
    """Load all four reference documents into memory."""
    loaded: dict[str, str] = {}
    for key, path in _DOCS.items():
        try:
            loaded[key] = read_file(path)
        except FileNotFoundError:
            loaded[key] = f"[{path} not found]"
    return loaded


def _extract_task_from_roadmap(task_id: str, roadmap: str) -> str:
    """Extract the roadmap section for a specific task ID (e.g., 'F1.7').

    Searches for a heading like '#### F1.7 —' and returns everything up to
    the next heading of the same or higher level.
    """
    # Escape the task_id for use in a regex (dots become literal dots)
    escaped = re.escape(task_id)

    # Match from the task heading to the next heading of equal or higher level
    pattern = rf"(#{1,4} {escaped}[^\n]*\n.*?)(?=\n#{1,4} |\Z)"
    match = re.search(pattern, roadmap, re.DOTALL)
    if match:
        return match.group(1).strip()

    # Fallback: return anything that mentions this task ID
    lines = roadmap.splitlines()
    result_lines = []
    in_section = False
    for line in lines:
        if re.search(rf"\b{escaped}\b", line) and line.startswith("#"):
            in_section = True
        elif in_section and line.startswith("#") and not re.search(rf"\b{escaped}\b", line):
            break
        if in_section:
            result_lines.append(line)

    return "\n".join(result_lines) if result_lines else f"Task {task_id} not found in roadmap."


def _get_task_description(task_id: str, roadmap: str) -> str:
    """Return a one-line description of the task from the roadmap summary table."""
    pattern = rf"\|\s*{re.escape(task_id)}\s*\|([^|]+)\|"
    match = re.search(pattern, roadmap)
    if match:
        return match.group(1).strip()
    return task_id


def _assemble_plan_header(task_id: str, description: str) -> str:
    return (
        f"# Implementation Plan: {task_id}\n\n"
        f"**Task:** {description}\n\n"
        f"**Generated by:** Blue Steel AI Planning Crew (PO + Architect)\n\n"
        "---\n\n"
    )


def run_planning(task_id: str) -> str:
    """Run the full PO ↔ Architect planning conversation for a Blue Steel task.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7' or 'F2.3'.

    Returns:
        Path to the generated plan file (.ai/context/tasks/{task_id}_plan.md).

    Raises:
        ValueError: If the generated plan is missing required sections.
    """
    logger = get_logger(task_id)
    logger.info(f"{MARKER_INFO} Blue Steel Planning Crew — Task: {task_id}", extra={"role": "planning"})

    # ── Load reference documents ──────────────────────────────────────────
    logger.info(f"{MARKER_INFO} Loading reference documents...", extra={"role": "planning"})
    docs = _load_docs()
    task_description = _get_task_description(task_id, docs["roadmap"])
    roadmap_entry = _extract_task_from_roadmap(task_id, docs["roadmap"])

    logger.info(f"{MARKER_INFO} Task: {task_description}", extra={"role": "planning"})
    logger.info(f"{MARKER_INFO} Roadmap entry: {len(roadmap_entry)} chars", extra={"role": "planning"})

    # Sanitize content for Windows console safety — arrows and emojis crash cp1252
    safe_description = _ascii_safe(task_description)
    safe_roadmap_entry = _ascii_safe(roadmap_entry)
    # Trim long docs to keep context manageable. With the 16K num_ctx now set for
    # local runs (config.get_model_options), the architect can plan against fuller
    # docs and name real symbols in section 4 — so local matches cloud caps. If
    # qwen3:14b planning starts timing out, fall back to 6K/7K/4K.
    pipeline_mode = os.environ.get("PIPELINE_MODE", "local").lower()
    if pipeline_mode == "cloud":
        safe_prd = _ascii_safe(docs["prd"])[:8000]
        safe_architecture = _ascii_safe(docs["architecture"])[:10000]
        safe_decisions = _ascii_safe(docs["decisions"])[:6000]
    else:
        safe_prd = _ascii_safe(docs["prd"])[:8000]
        safe_architecture = _ascii_safe(docs["architecture"])[:10000]
        safe_decisions = _ascii_safe(docs["decisions"])[:6000]

    _TRUNCATION_NOTICE = (
        '\n\n[TRUNCATED — this is a partial snapshot. '
        'Call read_project_file("docs/DECISIONS.md") to access the full decision log '
        'before citing any D-number.]'
    )
    raw_decisions = _ascii_safe(docs["decisions"])
    raw_architecture = _ascii_safe(docs["architecture"])
    decisions_limit = 6000
    architecture_limit = 10000
    if len(raw_decisions) > decisions_limit:
        safe_decisions += _TRUNCATION_NOTICE
    if len(raw_architecture) > architecture_limit:
        safe_architecture += _TRUNCATION_NOTICE

    # ── PO Round 1: Define scope and acceptance criteria ──────────────────
    logger.info(f"{MARKER_INFO} PO Round 1: defining scope and acceptance criteria...", extra={"role": "po"})
    po_output_1 = po_agent.run(
        task_id=task_id,
        context={
            "round": 1,
            "description": safe_description,
            "roadmap_entry": safe_roadmap_entry,
            "prd": safe_prd,
        },
    )
    logger.info(f"{MARKER_OK} PO Round 1 complete — {len(po_output_1)} chars", extra={"role": "po"})

    # ── Architect Round 1: Technical proposal ─────────────────────────────
    logger.info(f"{MARKER_INFO} Architect Round 1: technical proposal...", extra={"role": "architect"})
    arch_output_1 = architect_agent.run(
        task_id=task_id,
        context={
            "round": 1,
            "po_output": po_output_1,
            "architecture": safe_architecture,
            "decisions": safe_decisions,
        },
    )
    logger.info(f"{MARKER_OK} Architect Round 1 complete — {len(arch_output_1)} chars", extra={"role": "architect"})

    # ── PO Round 2: Challenge ─────────────────────────────────────────────
    logger.info(f"{MARKER_INFO} PO Round 2: challenging the architect's proposal...", extra={"role": "po"})
    po_output_2 = po_agent.run(
        task_id=task_id,
        context={
            "round": 2,
            "architect_proposal": arch_output_1,
        },
    )
    logger.info(f"{MARKER_OK} PO Round 2 complete — {len(po_output_2)} chars", extra={"role": "po"})

    # ── Architect Round 2: Finalized plan ─────────────────────────────────
    logger.info(f"{MARKER_INFO} Architect Round 2: finalizing plan...", extra={"role": "architect"})
    arch_output_2 = architect_agent.run(
        task_id=task_id,
        context={
            "round": 2,
            "po_challenge": po_output_2,
            "architect_proposal_1": arch_output_1,
        },
    )
    logger.info(f"{MARKER_OK} Architect Round 2 complete — {len(arch_output_2)} chars", extra={"role": "architect"})

    # ── Assemble and write plan ───────────────────────────────────────────
    header = _assemble_plan_header(task_id, task_description)

    # The architect's round-2 output is normally a full 8-section plan. Detect that
    # by heading *number* (titles drift) and use it directly, normalizing titles to
    # canonical. Wrapping it under a "## 3." heading is only safe when it has no
    # numbered headings of its own — otherwise we'd nest "## 1." inside "## 3." and
    # _extract_section_3 would capture an empty Section 3, silently zeroing out scope
    # detection (the F1.7 failure). So any numbered output is used as-is.
    if _HEADING_RE.search(arch_output_2):
        plan_content = (
            header
            + _normalize_section_titles(arch_output_2)
            + "\n\n"
            + _planning_conversation(arch_output_1, po_output_2)
        )
    else:
        # Unstructured prose only — safe to place under "## 3. Proposed Technical
        # Solution" so downstream parsers find the BE/FE indicators there.
        plan_content = (
            header
            + "## 1. Executive Summary\n\n"
            + po_output_1
            + "\n\n"
            + "## 2. Acceptance Criteria\n\n"
            + "_See PO scope above._\n\n"
            + "## 3. Proposed Technical Solution\n\n"
            + arch_output_2
            + "\n\n"
            + _planning_conversation(arch_output_1, po_output_2)
        )

    if len(plan_content.strip()) <= 200:
        raise ValueError("Plan is empty or malformed")

    plan_path = f"{_PLAN_DIR}/{task_id}_plan.md"
    write_file(plan_path, plan_content)

    logger.info(f"{MARKER_OK} Plan written: {plan_path}", extra={"role": "planning"})
    return plan_path
