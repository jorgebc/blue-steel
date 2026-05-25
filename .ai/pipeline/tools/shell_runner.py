import base64
import json
import os
import re
import subprocess
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from .filesystem import get_modified_files

# A plausible npm package spec: optional @scope/, name, optional @version-range.
# Deliberately strict — these names originate in an LLM-authored plan and are
# interpolated into a shell command, so anything containing whitespace or shell
# metacharacters ($, ;, &, |, backticks, ...) is rejected to prevent injection.
_NPM_PKG_SPEC = re.compile(
    r"^@?[a-z0-9][a-z0-9._-]*(/[a-z0-9._-]+)?(@[A-Za-z0-9.^~*><=\-]+)?$"
)


def _is_safe_pkg_spec(spec: str) -> bool:
    return bool(_NPM_PKG_SPEC.match(spec))

REPO_ROOT = Path(__file__).resolve().parents[3]
API_ROOT = REPO_ROOT / "apps" / "api"
WEB_ROOT = REPO_ROOT / "apps" / "web"

SONAR_HOST = "http://localhost:9000"
SONAR_PROJECT_KEY = "blue-steel-api"
SONAR_CONTAINER = "sonarqube-local"
SONAR_BOOT_TIMEOUT_S = 120
SONAR_STDOUT_TAIL_CHARS = 4000


def run_command(cmd: str, cwd: str = None, timeout: int = 120) -> dict:
    """Run a shell command and return stdout, stderr, returncode, and success flag."""
    working_dir = cwd or str(REPO_ROOT)
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=working_dir,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
        return {
            "stdout": result.stdout,
            "stderr": result.stderr,
            "returncode": result.returncode,
            "success": result.returncode == 0,
        }
    except subprocess.TimeoutExpired:
        return {
            "stdout": "",
            "stderr": f"Command timed out after {timeout}s: {cmd}",
            "returncode": -1,
            "success": False,
        }


def run_tests_backend() -> dict:
    """Run backend unit tests and ArchUnit checks (mvn test from apps/api/)."""
    return run_command("mvn test", cwd=str(API_ROOT), timeout=300)


def run_tests_integration_backend() -> dict:
    """Run backend integration tests via Testcontainers (mvn verify from apps/api/).

    Requires a running Podman or Docker daemon.
    """
    return run_command("mvn verify", cwd=str(API_ROOT), timeout=600)


def run_linter_backend() -> dict:
    """Check backend formatting with Spotless/google-java-format (mvn spotless:check)."""
    return run_command("mvn spotless:check", cwd=str(API_ROOT), timeout=120)


def run_format_backend() -> dict:
    """Auto-format backend Java with Spotless/google-java-format (mvn spotless:apply).

    Deterministic counterpart to `run_linter_backend`: rewrites source files in
    place to satisfy the formatter. Intended to run before the check so a purely
    cosmetic violation is fixed mechanically instead of by an LLM hand-edit.
    """
    return run_command("mvn spotless:apply", cwd=str(API_ROOT), timeout=120)


def run_tests_frontend() -> dict:
    """Run frontend tests in CI mode (npm test from apps/web/)."""
    return run_command("npm test", cwd=str(WEB_ROOT), timeout=300)


def run_typecheck_frontend() -> dict:
    """Run TypeScript type-check without emitting output (npm run type-check)."""
    return run_command("npm run type-check", cwd=str(WEB_ROOT), timeout=120)


def run_lint_frontend() -> dict:
    """Run ESLint on the frontend source (npm run lint from apps/web/)."""
    return run_command("npm run lint", cwd=str(WEB_ROOT), timeout=120)


def install_frontend_dependencies(packages: list[str]) -> dict:
    """Install the given npm packages into apps/web (`npm install <pkg> ...`).

    Intended for the execution pre-flight: `packages` must be the allowlist of
    `NEW DEPENDENCY (frontend)` specs declared in the plan — never arbitrary input.
    Each spec is validated against `_NPM_PKG_SPEC`; unsafe specs are dropped and
    reported in `stderr` rather than executed.

    Returns the standard `{stdout, stderr, returncode, success}` dict, extended
    with `installed` (the specs actually passed to npm).
    """
    safe = [p for p in packages if _is_safe_pkg_spec(p)]
    rejected = [p for p in packages if not _is_safe_pkg_spec(p)]
    if not safe:
        return {
            "stdout": "",
            "stderr": (
                f"No valid package specs to install (rejected: {rejected})."
                if rejected
                else "No packages requested."
            ),
            "returncode": 0 if not packages else 1,
            "success": not packages,
            "installed": [],
        }
    result = run_command(
        "npm install " + " ".join(safe), cwd=str(WEB_ROOT), timeout=300
    )
    result["installed"] = safe
    if rejected:
        result["stderr"] = (
            f"{result.get('stderr', '')}\nRejected unsafe package specs: {rejected}"
        ).strip()
    return result


def run_security_audit_frontend() -> dict:
    """Audit production npm dependencies for high-severity CVEs."""
    return run_command(
        "npm audit --audit-level=high --production",
        cwd=str(WEB_ROOT),
        timeout=120,
    )


def _sonar_failure(returncode: int, stderr: str, stdout: str = "") -> dict:
    return {
        "stdout": stdout,
        "stderr": stderr,
        "returncode": returncode,
        "success": False,
    }


def _ensure_sonar_container_running() -> dict | None:
    probe = run_command(
        f'podman ps --filter name={SONAR_CONTAINER} --format "{{{{.Names}}}}"',
        timeout=10,
    )
    if not probe["success"]:
        return _sonar_failure(
            3,
            f"Failed to query podman for '{SONAR_CONTAINER}'. "
            f"Is podman installed? stderr: {probe['stderr'].strip()}",
        )
    if SONAR_CONTAINER in probe["stdout"]:
        return None
    start = run_command(f"podman start {SONAR_CONTAINER}", timeout=30)
    if not start["success"]:
        return _sonar_failure(
            3,
            f"Failed to start podman container '{SONAR_CONTAINER}'. "
            f"Is it created? Run: podman ps -a | grep sonarqube. "
            f"stderr: {start['stderr'].strip()}",
        )
    return None


def _wait_for_sonar_up() -> dict | None:
    deadline = time.monotonic() + SONAR_BOOT_TIMEOUT_S
    last_err = ""
    while time.monotonic() < deadline:
        try:
            with urlopen(f"{SONAR_HOST}/api/system/status", timeout=5) as resp:
                body = json.loads(resp.read().decode("utf-8"))
                if body.get("status") == "UP":
                    return None
                last_err = f"status={body.get('status')!r}"
        except (URLError, HTTPError, json.JSONDecodeError, TimeoutError) as exc:
            last_err = repr(exc)
        time.sleep(3)
    return _sonar_failure(
        4,
        f"SonarQube at {SONAR_HOST} did not reach status=UP within "
        f"{SONAR_BOOT_TIMEOUT_S}s. Last probe: {last_err}",
    )


def _fetch_sonar_issues(token: str) -> tuple[list[dict] | None, dict | None]:
    auth = base64.b64encode(f"{token}:".encode()).decode()
    url = (
        f"{SONAR_HOST}/api/issues/search"
        f"?componentKeys={SONAR_PROJECT_KEY}&resolved=false&ps=500"
    )
    req = Request(url, headers={"Authorization": f"Basic {auth}"})
    try:
        with urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        return payload.get("issues", []), None
    except HTTPError as exc:
        return None, _sonar_failure(
            5, f"SonarQube /api/issues/search returned HTTP {exc.code}: {exc.reason}"
        )
    except (URLError, json.JSONDecodeError, TimeoutError) as exc:
        return None, _sonar_failure(5, f"SonarQube /api/issues/search failed: {exc!r}")


def _component_to_repo_path(component: str) -> str:
    """Strip the `projectKey:` prefix and normalize to a repo-relative POSIX path.

    SonarQube components look like `blue-steel-api:path/to/File.java`. When the
    scanner runs from `apps/api/`, the path-after-colon is module-relative
    (e.g. `src/main/java/...`). `get_modified_files()` returns repo-relative
    paths (e.g. `apps/api/src/main/java/...`), so prepend `apps/api/` if the
    component path does not already start with it. The first real run should
    DEBUG-log a raw component to confirm this assumption.
    """
    _, _, path = component.partition(":")
    if not path:
        return component
    return path if path.startswith("apps/api/") else f"apps/api/{path}"


def run_sonar_backend() -> dict:
    """Run the local SonarQube quality gate against the backend, filtered to changed files.

    Local-only: requires the `sonarqube-local` Podman container and a $SONAR_TOKEN
    env var (loaded from .env.local — never commit per D-050). Skips nothing
    silently; missing token or unreachable server is a hard failure.

    Important: relies on surefire reports from a prior `run_tests_backend()` call
    (`mvn test`, not `clean test`). Running `clean test` upstream would wipe them.

    Returns:
        Standard `{stdout, stderr, returncode, success}` dict, extended with:
        - `findings`: list[dict] of {file, line, rule, severity, message} for
          issues in files modified on this branch (only present when issues remain).
        - `notes`: human-readable summary.
        Returncodes: 0 clean, 1 issues, 2 missing token, 3 podman, 4 boot timeout,
        5 HTTP error from Sonar API, 6 mvn invocation failed.
    """
    token = os.environ.get("SONAR_TOKEN")
    if not token:
        return _sonar_failure(
            2,
            "SONAR_TOKEN env var is unset — required for SonarQube scan "
            "(D-050: never hardcode tokens). Export it from .env.local before running.",
        )

    container_err = _ensure_sonar_container_running()
    if container_err is not None:
        return container_err

    boot_err = _wait_for_sonar_up()
    if boot_err is not None:
        return boot_err

    scan = run_command(
        f"mvn sonar:sonar "
        f"-Dsonar.host.url={SONAR_HOST} "
        f"-Dsonar.token={token} "
        f"-Dsonar.projectKey={SONAR_PROJECT_KEY} "
        f"-Dsonar.qualitygate.wait=false",
        cwd=str(API_ROOT),
        timeout=600,
    )
    scan_tail = scan["stdout"][-SONAR_STDOUT_TAIL_CHARS:]
    if not scan["success"]:
        return {
            "stdout": scan_tail,
            "stderr": scan["stderr"],
            "returncode": 6,
            "success": False,
            "notes": (
                "mvn sonar:sonar failed. Confirm sonar-maven-plugin is in pom.xml "
                "and that `run_tests_backend` ran first so surefire reports exist."
            ),
        }

    issues, fetch_err = _fetch_sonar_issues(token)
    if fetch_err is not None:
        return fetch_err

    modified = set(get_modified_files(base="main"))
    findings: list[dict] = []
    for issue in issues:
        path = _component_to_repo_path(issue.get("component", ""))
        if path in modified:
            findings.append(
                {
                    "file": path,
                    "line": issue.get("line"),
                    "rule": issue.get("rule"),
                    "severity": issue.get("severity"),
                    "message": issue.get("message"),
                }
            )

    total = len(issues)
    if not findings:
        return {
            "stdout": scan_tail,
            "stderr": "",
            "returncode": 0,
            "success": True,
            "notes": (
                f"All modified files clean ({total} total Sonar issues; "
                "none in files you modified)."
            ),
        }

    return {
        "stdout": scan_tail,
        "stderr": "",
        "returncode": 1,
        "success": False,
        "findings": findings,
        "notes": (
            f"{len(findings)} Sonar issue(s) in files you modified "
            f"(of {total} total) — fix and re-run run_sonar_backend."
        ),
    }
