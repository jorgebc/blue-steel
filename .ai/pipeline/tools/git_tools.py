import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]


def _git(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def get_current_branch() -> str:
    result = _git("rev-parse", "--abbrev-ref", "HEAD")
    return result.stdout.strip()


def create_branch(name: str) -> bool:
    result = _git("checkout", "-b", name)
    return result.returncode == 0


def stage_files(files: list[str]) -> bool:
    result = _git("add", "--", *files)
    return result.returncode == 0


def commit(message: str) -> bool:
    result = _git("commit", "-m", message)
    return result.returncode == 0


def get_commit_log(n: int = 5) -> list[str]:
    result = _git("log", f"--max-count={n}", "--oneline")
    return [line for line in result.stdout.splitlines() if line]
