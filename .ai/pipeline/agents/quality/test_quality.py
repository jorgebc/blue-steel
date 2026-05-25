"""Integration test for the Blue Steel quality pipeline.

Requires {task_id}_execution.md from phase 4 (execution crew).
Runs run_quality and verifies:
  1. All three reports are generated with non-empty content
  2. No report contains "TODO" or "placeholder"
Prints the verdict of each phase.

Run from repo root:
    python .ai/pipeline/agents/quality/test_quality.py [task_id]

If task_id is omitted, the first execution report found in
.ai/context/tasks/ is used.
"""

import sys
from pathlib import Path

# Add .ai/pipeline/ to sys.path so local imports resolve
sys.path.insert(0, str(Path(__file__).parents[2]))

# Load .env.local from repo root if present
try:
    from dotenv import load_dotenv

    _repo_root = Path(__file__).parents[4]  # quality/ -> agents/ -> pipeline/ -> .ai/ -> repo root
    for _env_file in (".env.local", ".env"):
        _candidate = _repo_root / _env_file
        if _candidate.exists():
            load_dotenv(_candidate)
            break
except ImportError:
    pass

from tools.filesystem import REPO_ROOT
from quality_pipeline import run_quality

_CONTEXT_DIR = REPO_ROOT / ".ai" / "context" / "tasks"

_FORBIDDEN_STRINGS = ("TODO", "placeholder")

_REPORT_SUFFIXES = (
    "_verification.md",
    "_review.md",
    "_secops.md",
)


def _find_task_id() -> str:
    """Return task_id from CLI args, or auto-detect from the first execution report."""
    if len(sys.argv) > 1:
        return sys.argv[1]

    execution_files = sorted(_CONTEXT_DIR.glob("*_execution.md"))
    if not execution_files:
        raise RuntimeError(
            "No execution reports found in .ai/context/tasks/. "
            "Run the execution crew first (phase 4)."
        )
    return execution_files[0].name.replace("_execution.md", "")


def _validate_reports(task_id: str) -> list[str]:
    """Return a list of validation failures (empty = all passed)."""
    failures: list[str] = []

    for suffix in _REPORT_SUFFIXES:
        report_path = _CONTEXT_DIR / f"{task_id}{suffix}"

        # 1. Report file must exist
        if not report_path.exists():
            failures.append(f"Report not created: {report_path.name}")
            continue

        content = report_path.read_text(encoding="utf-8", errors="replace")

        # 2. Report must have non-empty content
        if len(content.strip()) < 50:
            failures.append(
                f"Report has insufficient content (<50 chars): {report_path.name}"
            )
            continue

        # 3. Report must not contain forbidden placeholder strings
        content_lower = content.lower()
        for forbidden in _FORBIDDEN_STRINGS:
            if forbidden.lower() in content_lower:
                failures.append(
                    f"Report contains '{forbidden}' (incomplete content): {report_path.name}"
                )

    return failures


def main() -> None:
    print("=" * 70)
    print("Blue Steel Quality Pipeline — Integration Test")
    print("=" * 70)

    # Step 1: Determine task ID
    print("\n[Step 1] Determining task ID...")
    task_id = _find_task_id()

    execution_path = _CONTEXT_DIR / f"{task_id}_execution.md"
    if not execution_path.exists():
        print(
            f"ERROR: Execution report not found: {execution_path}\n"
            "Phase 4 (execution crew) must run before this test."
        )
        sys.exit(1)

    print(f"         Task ID: {task_id}")
    print(f"         Execution report: {execution_path.name} (found)")

    # Step 2: Run the quality pipeline
    print(f"\n[Step 2] Running quality pipeline for task {task_id}...")
    summary = run_quality(task_id=task_id)

    # Step 3: Print per-phase verdicts
    print(f"\n{'='*70}")
    print("Phase Verdicts:")
    print(f"{'='*70}")
    print(f"  Verification : {summary.get('verification_verdict', 'NOT_RUN')}")
    print(f"  Review       : {summary.get('review_verdict', 'NOT_RUN')}")
    print(f"  SecOps       : {summary.get('secops_verdict', 'NOT_RUN')}")
    if summary.get("stopped_at"):
        print(f"  Stopped at   : {summary['stopped_at']}")
    print(f"  Blocked      : {summary.get('blocked', False)}")
    print(f"  Overall      : {'PASSED' if summary.get('passed') else 'FAILED/BLOCKED'}")

    # Step 4: Validate report files
    print(f"\n[Step 3] Validating generated reports...")
    failures = _validate_reports(task_id)

    # Step 5: Print report paths
    print(f"\n{'='*70}")
    print("Generated Reports:")
    print(f"{'='*70}")
    for suffix in _REPORT_SUFFIXES:
        report_path = _CONTEXT_DIR / f"{task_id}{suffix}"
        status = "OK" if report_path.exists() else "MISSING"
        size = report_path.stat().st_size if report_path.exists() else 0
        print(f"  [{status}] {report_path.name} ({size} bytes)")

    # Step 6: Report validation results
    print(f"\n{'='*70}")
    if failures:
        print("VALIDATION FAILED:")
        for f in failures:
            print(f"   * {f}")
        print(
            "\nFix: Review the agent prompts in .ai/pipeline/prompts/ and ensure "
            "agents produce complete, non-placeholder output."
        )
        sys.exit(1)
    else:
        print("All reports generated with valid content.")
        print(
            f"\nQuality pipeline test PASSED for task {task_id}"
            if summary.get("passed")
            else f"\nQuality pipeline ran for task {task_id} "
            f"(stopped at: {summary.get('stopped_at', 'unknown')} — "
            f"see blocker report for details)"
        )
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
