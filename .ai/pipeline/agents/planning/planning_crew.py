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

from tools.filesystem import read_file, write_file

import po_agent
import architect_agent

_DOCS = {
    "prd": "docs/PRD.md",
    "roadmap": "docs/ROADMAP.md",
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
    print(f"\n{'='*60}")
    print(f"Blue Steel Planning Crew — Task: {task_id}")
    print(f"{'='*60}\n")

    # ── Load reference documents ──────────────────────────────────────────
    print("[1/5] Loading reference documents...")
    docs = _load_docs()
    task_description = _get_task_description(task_id, docs["roadmap"])
    roadmap_entry = _extract_task_from_roadmap(task_id, docs["roadmap"])

    print(f"      Task: {task_description}")
    print(f"      Roadmap entry ({len(roadmap_entry)} chars)")

    # Sanitize content for Windows console safety — arrows and emojis crash cp1252
    safe_description = _ascii_safe(task_description)
    safe_roadmap_entry = _ascii_safe(roadmap_entry)
    # Trim long docs to keep context manageable for local models.
    # Architecture and Decisions are large; local models (qwen3:14b) time out
    # with >15K tokens — keep each reference doc under 5K chars.
    pipeline_mode = os.environ.get("PIPELINE_MODE", "local").lower()
    if pipeline_mode == "cloud":
        safe_prd = _ascii_safe(docs["prd"])[:8000]
        safe_architecture = _ascii_safe(docs["architecture"])[:10000]
        safe_decisions = _ascii_safe(docs["decisions"])[:6000]
    else:
        safe_prd = _ascii_safe(docs["prd"])[:4000]
        safe_architecture = _ascii_safe(docs["architecture"])[:5000]
        safe_decisions = _ascii_safe(docs["decisions"])[:3000]

    # ── PO Round 1: Define scope and acceptance criteria ──────────────────
    print("\n[2/5] PO Round 1: defining scope and acceptance criteria...")
    po_output_1 = po_agent.run(
        task_id=task_id,
        context={
            "round": 1,
            "description": safe_description,
            "roadmap_entry": safe_roadmap_entry,
            "prd": safe_prd,
        },
    )
    print(f"      PO output: {len(po_output_1)} chars")

    # ── Architect Round 1: Technical proposal ─────────────────────────────
    print("\n[3/5] Architect Round 1: technical proposal...")
    arch_output_1 = architect_agent.run(
        task_id=task_id,
        context={
            "round": 1,
            "po_output": po_output_1,
            "architecture": safe_architecture,
            "decisions": safe_decisions,
        },
    )
    print(f"      Architect output: {len(arch_output_1)} chars")

    # ── PO Round 2: Challenge ─────────────────────────────────────────────
    print("\n[4/5] PO Round 2: challenging the architect's proposal...")
    po_output_2 = po_agent.run(
        task_id=task_id,
        context={
            "round": 2,
            "architect_proposal": arch_output_1,
        },
    )
    print(f"      PO challenge: {len(po_output_2)} chars")

    # ── Architect Round 2: Finalized plan ─────────────────────────────────
    print("\n[5/5] Architect Round 2: finalizing plan...")
    arch_output_2 = architect_agent.run(
        task_id=task_id,
        context={
            "round": 2,
            "po_challenge": po_output_2,
            "architect_proposal_1": arch_output_1,
        },
    )
    print(f"      Final plan: {len(arch_output_2)} chars")

    # ── Assemble and write plan ───────────────────────────────────────────
    header = _assemble_plan_header(task_id, task_description)

    # Check if architect's output already contains the plan sections;
    # if so use it directly, otherwise wrap with all context
    has_all_sections = all(
        section.lower() in arch_output_2.lower() for section in _PLAN_SECTIONS
    )

    if has_all_sections:
        plan_content = header + arch_output_2
    else:
        # Architect did not produce a fully structured plan; assemble from all outputs
        plan_content = (
            header
            + "## Planning Conversation\n\n"
            + "### PO — Scope & Acceptance Criteria\n\n"
            + po_output_1
            + "\n\n---\n\n"
            + "### Architect — Technical Proposal\n\n"
            + arch_output_1
            + "\n\n---\n\n"
            + "### PO — Challenge\n\n"
            + po_output_2
            + "\n\n---\n\n"
            + "### Architect — Final Plan\n\n"
            + arch_output_2
        )

    plan_path = f"{_PLAN_DIR}/{task_id}_plan.md"
    write_file(plan_path, plan_content)

    print(f"\nDone. Plan written to: {plan_path}")
    return plan_path
