import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
API_ROOT = REPO_ROOT / "apps" / "api"
WEB_ROOT = REPO_ROOT / "apps" / "web"


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


def run_tests_frontend() -> dict:
    """Run frontend tests in CI mode (npm test from apps/web/)."""
    return run_command("npm test", cwd=str(WEB_ROOT), timeout=300)


def run_typecheck_frontend() -> dict:
    """Run TypeScript type-check without emitting output (npm run type-check)."""
    return run_command("npm run type-check", cwd=str(WEB_ROOT), timeout=120)


def run_security_audit_frontend() -> dict:
    """Audit production npm dependencies for high-severity CVEs."""
    return run_command(
        "npm audit --audit-level=high --production",
        cwd=str(WEB_ROOT),
        timeout=120,
    )
