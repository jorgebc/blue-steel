import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]

PROTECTED_WRITE_PATHS = [
    "apps/web/src/components/ui/",
    "apps/api/src/main/resources/db/changelog/",
]


def _resolve(path: str) -> Path:
    p = Path(path)
    if not p.is_absolute():
        p = REPO_ROOT / p
    return p.resolve()


def read_file(path: str) -> str:
    """Read a file and return its content as a string."""
    resolved = _resolve(path)
    if not resolved.exists():
        raise FileNotFoundError(f"File not found: {resolved}")
    return resolved.read_text(encoding="utf-8")


def write_file(path: str, content: str) -> bool:
    """Write content to a file, creating intermediate directories as needed.

    Raises PermissionError for paths protected by project conventions
    (shadcn/ui auto-generated components, Liquibase changelogs).
    """
    resolved = _resolve(path)
    try:
        rel = resolved.relative_to(REPO_ROOT).as_posix()
        for protected in PROTECTED_WRITE_PATHS:
            if rel.startswith(protected):
                raise PermissionError(
                    f"Writing to '{protected}' is forbidden by project conventions: {rel}"
                )
    except ValueError:
        pass  # path is outside REPO_ROOT — no restriction applies
    resolved.parent.mkdir(parents=True, exist_ok=True)
    resolved.write_text(content, encoding="utf-8")
    return True


def list_files(directory: str, pattern: str = "*") -> list[str]:
    """List files matching a glob pattern within a directory."""
    resolved = _resolve(directory)
    return [str(p) for p in sorted(resolved.glob(pattern)) if p.is_file()]


def file_exists(path: str) -> bool:
    return _resolve(path).exists()


def get_git_diff(base: str = "main") -> str:
    """Return the full diff of the current branch against base."""
    result = subprocess.run(
        ["git", "diff", f"{base}...HEAD"],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return result.stdout


def get_modified_files(base: str = "main") -> list[str]:
    """List files modified on the current branch relative to base."""
    result = subprocess.run(
        ["git", "diff", "--name-only", f"{base}...HEAD"],
        cwd=str(REPO_ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return [f for f in result.stdout.splitlines() if f]
