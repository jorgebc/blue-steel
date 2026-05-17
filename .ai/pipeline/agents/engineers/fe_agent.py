"""Frontend Engineer agent for Blue Steel execution pipeline.

Loads the FE Engineer persona from prompts/fe_engineer.md and wraps it in a
CodeAgent equipped with filesystem and shell tools for the React/TypeScript frontend.
"""

import importlib.resources
import os
import sys
import yaml
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from smolagents import CodeAgent, LiteLLMModel, tool

from config import get_llm
from tools.filesystem import (
    read_file as _read_file,
    write_file as _write_file,
    list_files as _list_files,
    get_git_diff as _get_git_diff,
)
from tools.shell_runner import run_tests_frontend as _run_tests, run_typecheck_frontend as _run_typecheck

_PROMPTS_DIR = Path(__file__).parents[2] / "prompts"


def _make_model() -> LiteLLMModel:
    """Build a LiteLLMModel from pipeline config for the execution phase."""
    llm_params = get_llm(phase="execution")
    model_id: str = llm_params["model"]
    api_key_raw: str = llm_params.get("api_key", "")
    api_base: str | None = llm_params.get("api_base")

    api_key: str | None = None
    if isinstance(api_key_raw, str) and api_key_raw.startswith("os.environ/"):
        env_var = api_key_raw.split("/", 1)[1]
        api_key = os.environ.get(env_var)
    elif api_key_raw:
        api_key = api_key_raw

    return LiteLLMModel(
        model_id=model_id,
        api_key=api_key,
        api_base=api_base,
        timeout=1800,  # 30 min — local models can be slow
    )


def _build_prompt_templates(persona_content: str) -> dict:
    """Prepend the FE Engineer persona to the default CodeAgent system prompt."""
    default: dict = yaml.safe_load(
        importlib.resources.files("smolagents.prompts")
        .joinpath("code_agent.yaml")
        .read_text()
    )
    default["system_prompt"] = (
        f"{persona_content}\n\n"
        "---\n\n"
        f"{default['system_prompt']}"
    )
    return default


@tool
def read_project_file(path: str) -> str:
    """Read a Blue Steel project file and return its content.

    Args:
        path: File path relative to the repo root, e.g.
              'apps/web/src/features/input/DiffReviewPage.tsx'
              or 'apps/web/src/api/sessions.ts'.

    Returns:
        File content as a UTF-8 string.
    """
    return _read_file(path)


@tool
def write_project_file(path: str, content: str) -> str:
    """Write content to a file in the Blue Steel frontend, creating directories as needed.

    Protected paths (shadcn/ui components) will raise PermissionError.
    This tool is for frontend files only — never use it for apps/api/ paths.

    Args:
        path: File path relative to the repo root (must be under apps/web/ or .ai/context/).
        content: Text content to write (UTF-8).

    Returns:
        Confirmation message with the written path.
    """
    if path.startswith("apps/api/"):
        raise PermissionError(
            f"Frontend engineer cannot write to backend paths: {path}"
        )
    # Double-check the shadcn/ui protected path (filesystem.py also enforces it)
    if "apps/web/src/components/ui/" in path:
        raise PermissionError(
            f"apps/web/src/components/ui/ is auto-generated and must never be edited: {path}"
        )
    _write_file(path, content)
    return f"Written: {path}"


@tool
def list_project_files(directory: str, pattern: str = "**/*") -> list:
    """List files matching a glob pattern within a project directory.

    Args:
        directory: Directory path relative to the repo root, e.g.
                   'apps/web/src/features/input'.
        pattern: Glob pattern (default: '**/*'). Examples: '*.tsx', '*.ts'.

    Returns:
        Sorted list of absolute file paths matching the pattern.
    """
    return _list_files(directory, pattern)


@tool
def get_git_diff(base: str = "main") -> str:
    """Return the full diff of the current branch against base.

    Args:
        base: Base branch to diff against (default: 'main').

    Returns:
        Git diff output as a string.
    """
    return _get_git_diff(base)


@tool
def run_typecheck_frontend() -> dict:
    """Run TypeScript type-check without emitting output (npm run type-check from apps/web/).

    Returns:
        Dict with keys: stdout, stderr, returncode, success.
        If success is False, fix type errors and rewrite the affected files.
    """
    return _run_typecheck()


@tool
def run_tests_frontend() -> dict:
    """Run frontend tests in CI mode (npm test from apps/web/).

    Returns:
        Dict with keys: stdout, stderr, returncode, success.
    """
    return _run_tests()


_CODE_FORMAT_GUIDANCE = """
---
CRITICAL OUTPUT FORMAT:

You must produce your final answer as Python code using this exact pattern:

```python
result = {
    "files_modified": ["apps/web/src/path/to/Component.tsx", ...],
    "success": True,
    "notes": "Brief description of what was implemented and any issues encountered.",
}
final_answer(result)
```

RULES:
1. `files_modified` is a list of all file paths (relative to repo root) you created or modified.
2. `success` is True if you implemented the plan without errors, False otherwise.
3. `notes` is a plain-English string: what you did, any skipped items, any failures.
4. Call final_answer(result) as the LAST statement.
5. Do NOT return a string — return the dict.
"""


def _create_agent() -> CodeAgent:
    persona = (_PROMPTS_DIR / "fe_engineer.md").read_text(encoding="utf-8")
    return CodeAgent(
        tools=[
            read_project_file,
            write_project_file,
            list_project_files,
            get_git_diff,
            run_typecheck_frontend,
            run_tests_frontend,
        ],
        model=_make_model(),
        prompt_templates=_build_prompt_templates(persona),
        max_steps=20,
    )


def run(task_id: str, plan_content: str) -> dict:
    """Run the Frontend Engineer agent to implement the frontend portion of a plan.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.
        plan_content: Full content of the implementation plan markdown file.

    Returns:
        Dict with keys:
            - files_modified: list of file paths created or modified
            - success: bool
            - notes: plain-English summary of what was done
    """
    agent = _create_agent()

    task_prompt = f"""
You are acting as the Frontend Engineer for Blue Steel.
{_CODE_FORMAT_GUIDANCE}

## Task ID
{task_id}

## Implementation Plan
{plan_content}

## Your Job

Implement the **frontend** portion of the plan above (Section 3 — files under apps/web/).

Steps:
1. Read Section 3 of the plan to identify which frontend files to create or modify.
2. Read existing files that you will depend on or modify (use read_project_file).
3. List the relevant directories to understand what already exists (use list_project_files).
4. Write every frontend file listed in the plan (use write_project_file).
5. Run the type-checker (run_typecheck_frontend) after writing TypeScript files; fix all errors.
6. Run the tests (run_tests_frontend) to verify Vitest tests pass.
7. Return your result dict with files_modified, success, and notes.

Constraints:
- Only implement frontend files (apps/web/) — skip any backend files listed in the plan.
- NEVER write to apps/web/src/components/ui/ — that directory is auto-generated by shadcn/ui.
  Wrap UI primitives in components/domain/ instead.
- Never write to apps/api/.
- Never use `any` types. Never use type assertions without explicit justification.
- No modals (D-082): use FocusedOverlay from components/domain/.
- No toasts (D-083): use InlineBanner from components/domain/.
- No spinners in primary content (D-086): use skeletons derived from TypeScript DTOs.
- Never install npm packages not listed in the plan.
- Never use `dangerouslySetInnerHTML` for LLM output.
- Import React Flow from @xyflow/react, NOT reactflow.
"""

    raw = agent.run(task_prompt)

    # Normalize: if the agent returned a string instead of a dict, wrap it
    if isinstance(raw, dict):
        return raw
    return {
        "files_modified": [],
        "success": False,
        "notes": str(raw),
    }
