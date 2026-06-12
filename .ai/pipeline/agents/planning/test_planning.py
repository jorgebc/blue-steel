"""Integration test for the Blue Steel planning crew.

Picks the first pending task from the active roadmap (docs/roadmap/ROADMAP_V2.md), runs the full
PO <-> Architect planning conversation, and validates that the output plan:

  1. Exists on disk
  2. Contains all 8 required sections
  3. References real Blue Steel paths (apps/api or apps/web) -- not placeholders
  4. Is not generic (mentions at least one Blue Steel domain term)

Run from repo root:
    python .ai/pipeline/agents/planning/test_planning.py

Requires ANTHROPIC_API_KEY set in environment or in .env.local at the repo root.
"""

import os
import re
import sys
from pathlib import Path

# Add .ai/pipeline/ to sys.path so local imports work
sys.path.insert(0, str(Path(__file__).parents[2]))

# Load .env.local from repo root if it exists (contains ANTHROPIC_API_KEY etc.)
try:
    from dotenv import load_dotenv
    _repo_root = Path(__file__).parents[4]  # .ai/pipeline/agents/planning/ -> repo root
    for env_file in (".env.local", ".env"):
        candidate = _repo_root / env_file
        if candidate.exists():
            load_dotenv(candidate)
            break
except ImportError:
    pass  # python-dotenv not installed; rely on environment variables

from tools.filesystem import read_file, REPO_ROOT
from planning_crew import run_planning, _PLAN_SECTIONS, _DOCS

ROADMAP_PATH = _DOCS["roadmap"]  # active roadmap — see docs/roadmap/README.md

# ── Validation constants ────────────────────────────────────────────────────

_BLUE_STEEL_PATHS = [
    "apps/api/",
    "apps/web/",
    "com.bluesteel.",
    "features/",
    "db/changelog/",
]

_DOMAIN_TERMS = [
    "Session",
    "DiffCard",
    "DiffPayload",
    "CommitPayload",
    "NarrativeBlock",
    "WorldState",
    "Actor",
    "EntityContext",
    "UNCERTAIN",
    "campaign",
    "pgvector",
    "Liquibase",
    "Spring Boot",
    "CodeAgent",
    "TanStack",
    "Zustand",
    "shadcn",
    "React Flow",
    "CampaignMembershipPort",
]


def _find_first_pending_task(roadmap: str) -> tuple[str, str]:
    """Return (task_id, description) for the first 🔲 task in the roadmap.

    Searches the summary tables for lines containing '🔲'.
    """
    for line in roadmap.splitlines():
        if "🔲" in line:
            # Summary table rows look like: | F1.7 | Frontend: walking skeleton... | 🔲 |
            parts = [p.strip() for p in line.split("|") if p.strip()]
            if len(parts) >= 2:
                task_id = parts[0].strip()
                description = parts[1].strip() if len(parts) > 1 else ""
                # Validate it looks like a task ID (e.g. F1.7, F2.3)
                if re.match(r"F\d+\.\d+", task_id):
                    return task_id, description

    raise RuntimeError(f"No pending (🔲) tasks found in {ROADMAP_PATH}")


def _validate_plan(plan_path: str, task_id: str) -> list[str]:
    """Return a list of validation failures (empty = all passed)."""
    failures: list[str] = []
    full_path = REPO_ROOT / plan_path

    # 1. File exists
    if not full_path.exists():
        failures.append(f"Plan file does not exist: {plan_path}")
        return failures  # Cannot validate further

    content = full_path.read_text(encoding="utf-8")

    # 2. All 8 sections present
    for section in _PLAN_SECTIONS:
        # Match case-insensitively; section numbers must be present
        section_num = section.split(".")[0].replace("## ", "").strip()
        section_title_pattern = rf"##\s*{re.escape(section_num)}\."
        if not re.search(section_title_pattern, content, re.IGNORECASE):
            failures.append(f"Missing section: {section}")

    # 3. References real Blue Steel paths (not placeholders)
    has_real_path = any(path in content for path in _BLUE_STEEL_PATHS)
    if not has_real_path:
        failures.append(
            f"Plan contains no real Blue Steel paths. "
            f"Expected at least one of: {_BLUE_STEEL_PATHS}"
        )

    # 4. Placeholder check — must NOT contain generic example paths
    placeholder_patterns = [
        r"src/your[-_]module",
        r"your[-_]package",
        r"/path/to/",
        r"<your[-_ ]",
        r"example\.com",
    ]
    for pattern in placeholder_patterns:
        if re.search(pattern, content, re.IGNORECASE):
            failures.append(f"Plan contains placeholder path matching: {pattern}")

    # 5. Domain vocabulary check — must mention Blue Steel-specific terms
    found_terms = [term for term in _DOMAIN_TERMS if term.lower() in content.lower()]
    if len(found_terms) < 3:
        failures.append(
            f"Plan mentions only {len(found_terms)} Blue Steel domain terms (need ≥3). "
            f"Found: {found_terms}"
        )

    # 6. Task ID present
    if task_id not in content:
        failures.append(f"Plan does not reference the task ID: {task_id}")

    return failures


def main() -> None:
    print("=" * 70)
    print("Blue Steel Planning Crew — Integration Test")
    print("=" * 70)

    # Step 1: Find first pending task
    print(f"\n[Step 1] Reading {ROADMAP_PATH}...")
    roadmap = read_file(ROADMAP_PATH)
    task_id, description = _find_first_pending_task(roadmap)
    print(f"         First pending task: {task_id} — {description}")

    # Step 2: Run planning
    print(f"\n[Step 2] Running planning crew for task {task_id}...")
    plan_path = run_planning(task_id)

    # Step 3: Validate
    print(f"\n[Step 3] Validating plan at {plan_path}...")
    failures = _validate_plan(plan_path, task_id)

    if failures:
        print("\n❌ VALIDATION FAILED:")
        for f in failures:
            print(f"   • {f}")
        print(
            "\nFix: Tighten the system prompts in .ai/pipeline/prompts/ "
            "with more Blue Steel-specific examples."
        )
        sys.exit(1)
    else:
        print("   ✓ All 8 sections present")
        print("   ✓ Real Blue Steel paths found")
        print("   ✓ No placeholder paths detected")
        print("   ✓ Blue Steel domain vocabulary confirmed")
        print(f"   ✓ Task ID {task_id} referenced")

    # Step 4: Print the plan
    plan_content = (REPO_ROOT / plan_path).read_text(encoding="utf-8")
    print(f"\n{'='*70}")
    print(f"GENERATED PLAN: {plan_path}")
    print(f"{'='*70}\n")
    print(plan_content)
    print(f"\n{'='*70}")
    print(f"✓ Planning crew test PASSED for task {task_id}")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
