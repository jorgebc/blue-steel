import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]

PROTECTED_WRITE_PATHS = [
    "apps/web/src/components/ui/",
    "apps/api/src/main/resources/db/changelog/",
]

# Ground-truth record of files written during the current engineer run. The
# execution crew resets this before each engineer and reads it afterwards, so the
# reported file list reflects what was actually written — independent of how (or
# whether) the LLM phrases its final_answer. See engineers/_result.py.
_WRITE_TRACKER: list[str] = []


def reset_write_tracker() -> None:
    """Clear the write tracker. Call immediately before running an engineer agent."""
    _WRITE_TRACKER.clear()


def get_tracked_writes() -> list[str]:
    """Return repo-relative paths written since the last reset (de-duplicated, ordered)."""
    seen: set[str] = set()
    ordered: list[str] = []
    for path in _WRITE_TRACKER:
        if path not in seen:
            seen.add(path)
            ordered.append(path)
    return ordered


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

    Raises PermissionError for paths outside the repo root, for shadcn/ui
    auto-generated components, and for modifications to existing Liquibase
    changeset files (new changesets in db/changelog/ are allowed).
    """
    resolved = _resolve(path)
    try:
        rel = resolved.relative_to(REPO_ROOT).as_posix()
    except ValueError:
        raise PermissionError(
            f"Writing outside the repository root is forbidden: {resolved}"
        )

    rel_lower = rel.lower()
    for protected in PROTECTED_WRITE_PATHS:
        if rel_lower.startswith(protected.lower()):
            if protected == "apps/api/src/main/resources/db/changelog/":
                # Liquibase is append-only: creating new changesets is OK,
                # modifying existing files is forbidden.
                if resolved.exists():
                    raise PermissionError(
                        f"Modifying an existing Liquibase changeset is forbidden: {rel}"
                    )
            else:
                raise PermissionError(
                    f"Writing to '{protected}' is forbidden by project conventions: {rel}"
                )

    resolved.parent.mkdir(parents=True, exist_ok=True)
    resolved.write_text(content, encoding="utf-8")
    _WRITE_TRACKER.append(rel)
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
