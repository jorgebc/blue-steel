"""Unit tests for the F1.7 fixes: check capture, circuit breaker, and the
autonomous new-dependency path. No LLM or network — pure deterministic logic.

Run from repo root:
    python -m pytest .ai/pipeline/agents/engineers/
"""

import sys
from pathlib import Path

# Mirror the path setup the orchestrator/test_execution use so bare and package
# imports both resolve under pytest.
_ENGINEERS_DIR = Path(__file__).parent
_PIPELINE_DIR = _ENGINEERS_DIR.parents[1]
for _p in (str(_PIPELINE_DIR), str(_ENGINEERS_DIR)):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from agents.engineers import _checks
import execution_crew
from tools.shell_runner import _is_safe_pkg_spec


def _fail(tool="run_typecheck_frontend", stderr="error TS9999: boom", stdout=""):
    return {"returncode": 1, "success": False, "stderr": stderr, "stdout": stdout}


def _ok(tool="run_typecheck_frontend"):
    return {"returncode": 0, "success": True, "stderr": "", "stdout": "ok"}


# ── _checks: circuit breaker ────────────────────────────────────────────────


def test_should_abort_trips_on_unrecoverable_signature():
    _checks.reset("", "fe_engineer")  # empty id -> no log file written
    _checks.record_and_log(
        "run_typecheck_frontend",
        _fail(stderr="src/api/client.ts(1,1): error TS2307: Cannot find module 'axios'."),
    )
    abort, reason = _checks.should_abort()
    assert abort is True
    assert "axios" in reason or "cannot find module" in reason.lower()


def test_should_abort_trips_on_repeated_identical_failure():
    _checks.reset("", "fe_engineer")
    same = "error TS2345: argument type mismatch"
    _checks.record_and_log("run_typecheck_frontend", _fail(stderr=same))
    abort, _ = _checks.should_abort()
    assert abort is False  # one failure is not yet a loop
    _checks.record_and_log("run_typecheck_frontend", _fail(stderr=same))
    abort, reason = _checks.should_abort()
    assert abort is True
    assert "twice" in reason


def test_should_abort_false_for_single_recoverable_failure():
    _checks.reset("", "fe_engineer")
    _checks.record_and_log("run_lint_frontend", _fail(stderr="eslint: 'x' is assigned but never used"))
    abort, _ = _checks.should_abort()
    assert abort is False


def test_last_check_failed_reflects_most_recent():
    _checks.reset("", "fe_engineer")
    _checks.record_and_log("run_typecheck_frontend", _fail())
    assert _checks.last_check_failed() is True
    _checks.record_and_log("run_typecheck_frontend", _ok())
    assert _checks.last_check_failed() is False


def test_last_failure_diagnostics_renders_concrete_output():
    _checks.reset("", "fe_engineer")
    _checks.record_and_log(
        "run_typecheck_frontend", _fail(stderr="error TS2307: Cannot find module 'axios'.")
    )
    diag = _checks.last_failure_diagnostics()
    assert "run_typecheck_frontend" in diag
    assert "returncode 1" in diag
    assert "Cannot find module 'axios'" in diag


def test_last_failure_diagnostics_empty_when_all_pass():
    _checks.reset("", "fe_engineer")
    _checks.record_and_log("run_typecheck_frontend", _ok())
    assert _checks.last_failure_diagnostics() == ""


# ── execution_crew: new-dependency parsing ──────────────────────────────────


def test_parse_new_dependencies_groups_by_scope():
    plan = (
        "## 3. Proposed Technical Solution\n"
        "- apps/web/src/api/client.ts\n"
        "NEW DEPENDENCY (frontend): axios — typed HTTP client with interceptors\n"
        "NEW DEPENDENCY (backend): org.foo:bar — pdf rendering\n"
    )
    deps = execution_crew._parse_new_dependencies(plan)
    assert deps["frontend"] == ["axios"]
    assert deps["backend"] == ["org.foo:bar"]


def test_parse_new_dependencies_empty_when_none_declared():
    deps = execution_crew._parse_new_dependencies("nothing here")
    assert deps == {"frontend": [], "backend": []}


def test_pkg_name_strips_version_ranges():
    assert execution_crew._pkg_name("react@^19.0.0") == "react"
    assert execution_crew._pkg_name("axios") == "axios"
    assert execution_crew._pkg_name("@scope/x@1.2.3") == "@scope/x"
    assert execution_crew._pkg_name("@scope/x") == "@scope/x"


def test_missing_frontend_deps_excludes_installed(monkeypatch):
    pkg_json = '{"dependencies": {"react": "^19"}, "devDependencies": {"vitest": "^4"}}'
    monkeypatch.setattr(execution_crew, "read_file", lambda _p: pkg_json)
    missing = execution_crew._missing_frontend_deps(["axios", "react@^19", "@scope/x"])
    assert missing == ["axios", "@scope/x"]


def test_install_declared_frontend_deps_invokes_scoped_install(monkeypatch):
    pkg_json = '{"dependencies": {}, "devDependencies": {}}'
    monkeypatch.setattr(execution_crew, "read_file", lambda _p: pkg_json)
    captured = {}

    def fake_install(packages):
        captured["packages"] = packages
        return {"success": True, "installed": packages, "returncode": 0, "stdout": "", "stderr": ""}

    monkeypatch.setattr(execution_crew, "install_frontend_dependencies", fake_install)

    class _StubLogger:
        def info(self, *a, **k): ...
        def error(self, *a, **k): ...
        def warning(self, *a, **k): ...

    installed = execution_crew._install_declared_frontend_deps(["axios"], _StubLogger())
    assert installed == ["axios"]
    assert captured["packages"] == ["axios"]


def test_install_declared_frontend_deps_noop_when_already_present(monkeypatch):
    pkg_json = '{"dependencies": {"axios": "^1"}, "devDependencies": {}}'
    monkeypatch.setattr(execution_crew, "read_file", lambda _p: pkg_json)

    def fail_if_called(_packages):  # pragma: no cover - must not run
        raise AssertionError("install should not be called when dep already present")

    monkeypatch.setattr(execution_crew, "install_frontend_dependencies", fail_if_called)

    class _StubLogger:
        def info(self, *a, **k): ...
        def error(self, *a, **k): ...

    assert execution_crew._install_declared_frontend_deps(["axios"], _StubLogger()) == []


# ── execution_crew: report rendering ────────────────────────────────────────


def test_engineer_section_includes_failure_diagnostics_when_failed():
    section = "\n".join(
        execution_crew._engineer_section(
            "Frontend Engineer",
            "fe_engineer",
            {
                "success": False,
                "files_modified": ["apps/web/src/api/client.ts"],
                "notes": "could not type-check",
                "diagnostics": "Check `run_typecheck_frontend` failed.\nCannot find module 'axios'.",
            },
            "SKIPPED",
        )
    )
    assert "**Status:** FAILED" in section
    assert "### Failure Diagnostics" in section
    assert "Cannot find module 'axios'" in section


def test_engineer_section_omits_diagnostics_on_success():
    section = "\n".join(
        execution_crew._engineer_section(
            "Frontend Engineer",
            "fe_engineer",
            {"success": True, "files_modified": ["a.tsx"], "notes": "ok", "diagnostics": "x"},
            "SKIPPED",
        )
    )
    assert "**Status:** SUCCESS" in section
    assert "Failure Diagnostics" not in section


def test_build_report_lists_installed_dependencies():
    report = execution_crew._build_execution_report(
        task_id="F1.7",
        be_result=None,
        fe_result={"success": True, "files_modified": ["a.tsx"], "notes": "ok"},
        migration_filename=None,
        timestamp="2026-05-22T00:00:00Z",
        installed_deps=["axios"],
    )
    assert "## Installed Dependencies" in report
    assert "`axios`" in report


# ── shell_runner: package-spec safety ───────────────────────────────────────


def test_is_safe_pkg_spec_accepts_valid_specs():
    assert _is_safe_pkg_spec("axios")
    assert _is_safe_pkg_spec("@tanstack/react-query")
    assert _is_safe_pkg_spec("react@^19.0.0")


def test_is_safe_pkg_spec_rejects_injection():
    assert not _is_safe_pkg_spec("foo; rm -rf /")
    assert not _is_safe_pkg_spec("foo && bar")
    assert not _is_safe_pkg_spec("$(evil)")
    assert not _is_safe_pkg_spec("a b")
