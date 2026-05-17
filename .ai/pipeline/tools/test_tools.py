import pytest
from pathlib import Path

from .filesystem import read_file, write_file, REPO_ROOT
from .shell_runner import run_command
from .git_tools import get_current_branch


def test_read_file():
    content = read_file("README.md")
    assert "Blue Steel" in content


def test_write_and_read():
    temp_path = ".ai/test_temp_write.txt"
    try:
        write_file(temp_path, "hello world")
        content = read_file(temp_path)
        assert content == "hello world"
    finally:
        temp_file = REPO_ROOT / temp_path
        if temp_file.exists():
            temp_file.unlink()


def test_protected_path():
    with pytest.raises(PermissionError):
        write_file("apps/web/src/components/ui/button.tsx", "// evil")


def test_run_command():
    result = run_command("echo hello")
    assert result["returncode"] == 0
    assert "hello" in result["stdout"]


def test_git_branch():
    branch = get_current_branch()
    assert branch == "feature/ai-pipeline-system"
