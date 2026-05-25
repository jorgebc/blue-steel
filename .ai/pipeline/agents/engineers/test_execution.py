"""Integration test for the Blue Steel execution crew.

Requires a {task_id}_plan.md to exist in .ai/context/tasks/ (produced by phase 3 planning crew).
Runs run_execution with that task_id and validates:

  1. {task_id}_execution.md is created
  2. No files were written inside apps/web/src/components/ui/
  3. No existing Liquibase migration was overwritten
  4. Prints the list of all modified files

Run from repo root:
    python .ai/pipeline/agents/engineers/test_execution.py [task_id]

If task_id is omitted, the first plan file found in .ai/context/tasks/ is used.
"""

import sys
from pathlib import Path

# Add .ai/pipeline/ to sys.path so local imports resolve
sys.path.insert(0, str(Path(__file__).parents[2]))

# Load .env.local from repo root if present
try:
    from dotenv import load_dotenv
    _repo_root = Path(__file__).parents[4]  # .ai/pipeline/agents/engineers/ -> repo root
    for _env_file in (".env.local", ".env"):
        _candidate = _repo_root / _env_file
        if _candidate.exists():
            load_dotenv(_candidate)
            break
except ImportError:
    pass

from tools.filesystem import REPO_ROOT
from execution_crew import run_execution

_PLAN_DIR = REPO_ROOT / ".ai" / "context" / "tasks"
_SHADCN_UI_PATH = "apps/web/src/components/ui/"
_CHANGELOG_DIR = "apps/api/src/main/resources/db/changelog/"


def _find_task_id() -> str:
    """Return the task_id from CLI args, or auto-detect from the first plan file."""
    if len(sys.argv) > 1:
        return sys.argv[1]

    plan_files = sorted(_PLAN_DIR.glob("*_plan.md"))
    if not plan_files:
        raise RuntimeError(
            "No plan files found in .ai/context/tasks/. "
            "Run the planning crew first (phase 3)."
        )
    # Return the task_id extracted from the first filename
    return plan_files[0].name.replace("_plan.md", "")


def _snapshot_changelog_files() -> set[str]:
    """Capture the set of existing Liquibase changeset filenames before execution."""
    changelog_dir = REPO_ROOT / "apps" / "api" / "src" / "main" / "resources" / "db" / "changelog"
    if not changelog_dir.exists():
        return set()
    return {f.name for f in changelog_dir.iterdir() if f.is_file()}


def _validate(
    task_id: str,
    summary: dict,
    changelog_before: set[str],
) -> list[str]:
    """Return a list of validation failures (empty = all passed)."""
    failures: list[str] = []
    all_files = summary.get("be_files", []) + summary.get("fe_files", [])

    # 1. Execution report exists
    report_path = REPO_ROOT / summary["report_path"]
    if not report_path.exists():
        failures.append(f"Execution report not created: {summary['report_path']}")

    # 2. No files written inside apps/web/src/components/ui/
    shadcn_violations = [f for f in all_files if _SHADCN_UI_PATH in f]
    if shadcn_violations:
        failures.append(
            f"Files written to protected shadcn/ui directory: {shadcn_violations}"
        )

    # 3. No existing Liquibase migration was overwritten
    changelog_dir = REPO_ROOT / "apps" / "api" / "src" / "main" / "resources" / "db" / "changelog"
    if changelog_dir.exists():
        changelog_after = {f.name for f in changelog_dir.iterdir() if f.is_file()}
        new_files = changelog_after - changelog_before
        overwritten = [
            f for f in all_files
            if _CHANGELOG_DIR in f and Path(f).name in changelog_before
        ]
        if overwritten:
            failures.append(
                f"Existing Liquibase changelogs were overwritten (append-only violation): {overwritten}"
            )
        # Report new migration files as informational (not a failure)
        if new_files:
            print(f"   [INFO] New migration files created: {sorted(new_files)}")

    return failures


def main() -> None:
    print("=" * 70)
    print("Blue Steel Execution Crew — Integration Test")
    print("=" * 70)

    # Step 1: Determine task ID
    print("\n[Step 1] Determining task ID...")
    task_id = _find_task_id()
    plan_path = _PLAN_DIR / f"{task_id}_plan.md"
    if not plan_path.exists():
        print(f"ERROR: Plan file not found: {plan_path}")
        print("Run the planning crew first to generate a plan.")
        sys.exit(1)
    print(f"         Task ID: {task_id}")
    print(f"         Plan: {plan_path}")

    # Step 2: Snapshot existing changelog files before execution
    print("\n[Step 2] Snapshotting existing Liquibase migrations...")
    changelog_before = _snapshot_changelog_files()
    print(f"         Existing migrations: {len(changelog_before)}")

    # Step 3: Run the execution crew
    print(f"\n[Step 3] Running execution crew for task {task_id}...")
    summary = run_execution(task_id=task_id)

    # Step 4: Validate
    print(f"\n[Step 4] Validating results...")
    failures = _validate(task_id, summary, changelog_before)

    # Step 5: Print modified files
    all_files = summary.get("be_files", []) + summary.get("fe_files", [])
    print(f"\n{'='*70}")
    print(f"Modified Files ({len(all_files)} total):")
    print(f"{'='*70}")
    if all_files:
        for f in sorted(all_files):
            print(f"  {f}")
    else:
        print("  (none — both engineers may have been skipped or found nothing to do)")

    if summary.get("migration_filename"):
        print(f"\nDB Migration: {summary['migration_filename']}")
    else:
        print("\nDB Migration: none")

    # Step 6: Report
    print(f"\n{'='*70}")
    if failures:
        print("VALIDATION FAILED:")
        for f in failures:
            print(f"   * {f}")
        print(
            "\nFix: Tighten the system prompts in .ai/pipeline/prompts/ "
            "so the engineer agents stay within their allowed scope."
        )
        sys.exit(1)
    else:
        be_ok = summary.get("be_success", True)
        fe_ok = summary.get("fe_success", True)
        print(f"Backend Engineer: {'OK' if be_ok else 'FAILED (check report)'}")
        print(f"Frontend Engineer: {'OK' if fe_ok else 'FAILED (check report)'}")
        print(f"Execution report: {summary['report_path']}")
        print(f"\nExecution crew test PASSED for task {task_id}")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
