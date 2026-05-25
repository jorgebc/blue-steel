"""Pure-function unit tests for the verification agent.

These need no LLM, no tools, and no network — they exercise the two pieces of
logic that let task F1.7 falsely pass:

  1. _is_tool_missing  — must not mistake a compiler's own diagnostics
                         ("Cannot find module ...") for a missing tool.
  2. _aggregate_verdict — a plain SKIPPED must NOT count as PASS; only an
                         explicitly intentional skip does.

Run from repo root:
    python .ai/pipeline/agents/quality/test_verification_unit.py
"""

import sys
from pathlib import Path

# Add .ai/pipeline/ to sys.path so `import verification_agent` resolves.
sys.path.insert(0, str(Path(__file__).parents[2]))

from verification_agent import _aggregate_verdict, _is_tool_missing


def _check(label: str, got, want) -> bool:
    ok = got == want
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}: got={got!r} want={want!r}")
    return ok


def _is_tool_missing_failures() -> list[str]:
    """tsc diagnostics on stdout are NOT a missing tool; shell launch errors are."""
    failures: list[str] = []

    # The exact regression: tsc emits "Cannot find module" / "Cannot find name"
    # as legitimate diagnostics on STDOUT. This must read as tool-present.
    if not _check(
        "tsc 'Cannot find module' on stdout -> not missing",
        _is_tool_missing(
            {
                "stdout": "src/api/client.ts(2,38): error TS2307: Cannot find module 'axios'.",
                "stderr": "",
            }
        ),
        False,
    ):
        failures.append("tsc Cannot find module misread as missing tool")

    if not _check(
        "tsc 'Cannot find name process' on stdout -> not missing",
        _is_tool_missing(
            {"stdout": "error TS2591: Cannot find name 'process'.", "stderr": ""}
        ),
        False,
    ):
        failures.append("tsc Cannot find name misread as missing tool")

    # npm reporting a failed script (exit 2) is NOT a missing tool.
    if not _check(
        "npm failed-script stderr -> not missing",
        _is_tool_missing(
            {"stdout": "...tsc errors...", "stderr": "npm error code 2\nnpm error command failed"}
        ),
        False,
    ):
        failures.append("npm failed-script misread as missing tool")

    # Genuine launch failures (on stderr) ARE a missing tool.
    if not _check(
        "Windows 'is not recognized' on stderr -> missing",
        _is_tool_missing(
            {
                "stdout": "",
                "stderr": "'npm' is not recognized as an internal or external command,",
            }
        ),
        True,
    ):
        failures.append("Windows missing-binary not detected")

    if not _check(
        "POSIX 'command not found' on stderr -> missing",
        _is_tool_missing({"stdout": "", "stderr": "npm: command not found"}),
        True,
    ):
        failures.append("POSIX missing-binary not detected")

    return failures


def _aggregate_verdict_failures() -> list[str]:
    """SKIPPED != PASS; only an intentional skip is pass-neutral."""
    failures: list[str] = []

    all_pass = {
        "spotless": {"status": "PASS"},
        "npm_typecheck": {"status": "PASS"},
    }
    if not _check("all PASS -> (passed, not blocked)", _aggregate_verdict(all_pass), (True, False)):
        failures.append("all-PASS verdict wrong")

    missing_tool = {
        "spotless": {"status": "PASS"},
        "npm_typecheck": {"status": "BLOCKED"},  # reclassified missing tool
    }
    if not _check("a BLOCKED check -> (not passed, blocked)", _aggregate_verdict(missing_tool), (False, True)):
        failures.append("BLOCKED verdict wrong")

    intentional_skip = {
        "npm_typecheck": {"status": "PASS"},
        "mvn_verify": {"status": "SKIPPED", "intentional": True},
    }
    if not _check("intentional skip -> (passed, not blocked)", _aggregate_verdict(intentional_skip), (True, False)):
        failures.append("intentional-skip verdict wrong")

    plain_skip = {
        "npm_typecheck": {"status": "PASS"},
        "mystery": {"status": "SKIPPED"},  # not intentional -> must not pass
    }
    if not _check("plain SKIPPED -> (not passed, not blocked)", _aggregate_verdict(plain_skip), (False, False)):
        failures.append("plain-SKIPPED counted as PASS (the original bug)")

    return failures


# pytest entrypoints — these must assert, not return, or pytest silently ignores the
# result and the checks never actually fail the suite.
def test_is_tool_missing() -> None:
    failures = _is_tool_missing_failures()
    assert not failures, f"_is_tool_missing failures: {failures}"


def test_aggregate_verdict() -> None:
    failures = _aggregate_verdict_failures()
    assert not failures, f"_aggregate_verdict failures: {failures}"


def main() -> int:
    print("test_is_tool_missing:")
    failures = _is_tool_missing_failures()
    print("test_aggregate_verdict:")
    failures += _aggregate_verdict_failures()

    print()
    if failures:
        print(f"FAILED ({len(failures)} assertion group(s)):")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("All verification unit tests passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
