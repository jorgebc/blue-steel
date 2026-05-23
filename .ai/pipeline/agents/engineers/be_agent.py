"""Backend Engineer agent for Blue Steel execution pipeline.

Loads the BE Engineer persona from prompts/be_engineer.md and wraps it in a
CodeAgent equipped with filesystem and shell tools for the Java/Spring Boot backend.
"""

import importlib.resources
import os
import sys
import yaml
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from smolagents import CodeAgent, LiteLLMModel, LogLevel, tool

from config import get_llm, get_model_options
from logger import get_logger
from tools.filesystem import (
    read_file as _read_file,
    write_file as _write_file,
    list_files as _list_files,
    get_git_diff as _get_git_diff,
    reset_write_tracker,
)
from tools.shell_runner import (
    run_format_backend as _run_format,
    run_linter_backend as _run_linter,
    run_sonar_backend as _run_sonar,
    run_tests_backend as _run_tests,
)
from agents.engineers._result import normalize_result
from agents.engineers import _checks
from agents.engineers._context_grounding import build_grounding_block

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
        # Large context window + near-greedy sampling — see config.get_model_options.
        # Without an explicit num_ctx, Ollama truncates the persona/plan and the model
        # hallucinates APIs it can no longer see.
        **get_model_options(phase="execution"),
    )


def _build_prompt_templates(persona_content: str) -> dict:
    """Prepend the BE Engineer persona to the default CodeAgent system prompt."""
    default: dict = yaml.safe_load(
        importlib.resources.files("smolagents.prompts")
        .joinpath("code_agent.yaml")
        .read_text()
    )
    # Persona is static markdown that may contain literal braces (e.g. JSX
    # `style={{}}`). Wrap it in a Jinja2 raw block so smolagents' StrictUndefined
    # template renderer treats those braces as text, not template syntax.
    default["system_prompt"] = (
        "{% raw %}\n"
        f"{persona_content}\n"
        "{% endraw %}\n\n"
        "---\n\n"
        f"{default['system_prompt']}"
    )
    return default


@tool
def read_project_file(path: str) -> str:
    """Read a Blue Steel project file and return its content.

    Args:
        path: File path relative to the repo root, e.g.
              'apps/api/src/main/java/com/bluesteel/application/port/in/session/SubmitSessionUseCase.java'.

    Returns:
        File content as a UTF-8 string.
    """
    return _read_file(path)


@tool
def write_project_file(path: str, content: str) -> str:
    """Write content to a file in the Blue Steel backend, creating directories as needed.

    Protected paths (shadcn/ui components, existing Liquibase changelogs) will raise PermissionError.
    This tool is for backend files only — never use it for apps/web/ paths.

    Args:
        path: File path relative to the repo root (must be under apps/api/ or .ai/context/).
        content: Text content to write (UTF-8).

    Returns:
        Confirmation message with the written path.
    """
    if path.lower().startswith("apps/web/"):
        raise PermissionError(
            f"Backend engineer cannot write to frontend paths: {path}"
        )
    _write_file(path, content)
    return f"Written: {path}"


@tool
def list_project_files(directory: str, pattern: str = "**/*") -> list:
    """List files matching a glob pattern within a project directory.

    Args:
        directory: Directory path relative to the repo root, e.g.
                   'apps/api/src/main/java/com/bluesteel/application/service'.
        pattern: Glob pattern (default: '**/*'). Examples: '*.java', '*.xml'.

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
def run_linter_backend() -> dict:
    """Auto-format backend Java with Spotless, then verify (apply + check from apps/api/).

    This tool first runs `mvn spotless:apply` to fix google-java-format violations
    mechanically, then runs `mvn spotless:check` and returns the check result. You
    do NOT need to hand-format Java — formatting is deterministic and handled here.

    Returns:
        Dict with keys: stdout, stderr, returncode, success.
        success is True once formatting is clean. If success is still False, the
        failure is not cosmetic (e.g. mvn could not run) — read stderr/stdout.
    """
    _run_format()
    return _checks.record_and_log("run_linter_backend", _run_linter())


@tool
def run_tests_backend() -> dict:
    """Run backend unit tests and ArchUnit checks (mvn test from apps/api/).

    Returns:
        Dict with keys: stdout, stderr, returncode, success.
        A failing ArchUnit test means a layer violation in production code — fix the code, not the rule.
    """
    return _checks.record_and_log("run_tests_backend", _run_tests())


@tool
def run_sonar_backend() -> dict:
    """Run the local SonarQube quality gate (mvn sonar:sonar) and return only issues in changed files.

    Findings are pre-filtered to files you modified on the current branch —
    legacy issues in unmodified files are excluded by the tool. Do NOT attempt
    to fix anything outside the returned findings list. Requires a local
    sonarqube-local Podman container and $SONAR_TOKEN in the environment.

    Returns:
        Dict with keys: stdout, stderr, returncode, success.
        On clean: success=True, notes describes filter count.
        On issues: success=False, plus `findings` list of
        {file, line, rule, severity, message} entries — fix and re-run.
        On infra failure (missing token, podman, boot timeout): success=False
        with an actionable stderr — surface as BLOCKED in the execution report.
    """
    return _checks.record_and_log("run_sonar_backend", _run_sonar())


_CODE_FORMAT_GUIDANCE = """
---
CRITICAL OUTPUT FORMAT:

You must produce your final answer as Python code using this exact pattern:

```python
result = {
    "files_modified": ["apps/api/path/to/File.java", ...],
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
    persona = (_PROMPTS_DIR / "be_engineer.md").read_text(encoding="utf-8")
    return CodeAgent(
        tools=[
            read_project_file,
            write_project_file,
            list_project_files,
            get_git_diff,
            run_linter_backend,
            run_tests_backend,
            run_sonar_backend,
        ],
        model=_make_model(),
        prompt_templates=_build_prompt_templates(persona),
        max_steps=35,
        verbosity_level=LogLevel.OFF,  # ReAct trace -> silenced; story lives in the logger
        step_callbacks=[_checks.abort_step_callback],  # circuit breaker -> early stop
    )


def run(task_id: str, plan_content: str) -> dict:
    """Run the Backend Engineer agent to implement the backend portion of a plan.

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

    # Ground truth (layer guide + real exports/signatures of files the plan touches)
    # so the model copies real symbols instead of inventing them. "" when nothing
    # resolves — the section then collapses to a blank line.
    grounding = build_grounding_block(plan_content, scope="backend")
    grounding_section = f"\n{grounding}\n" if grounding else ""

    task_prompt = f"""
You are acting as the Backend Engineer for Blue Steel.
{_CODE_FORMAT_GUIDANCE}

## Task ID
{task_id}

## Implementation Plan
{plan_content}
{grounding_section}
## Your Job

Implement the **backend** portion of the plan above (Section 3 — files under apps/api/).

Steps:
1. Read Section 3 of the plan to identify which backend files to create or modify.
2. Read existing files that you will depend on or modify (use read_project_file).
3. List the relevant directories to understand what already exists (use list_project_files).
4. Write every backend file listed in the plan (use write_project_file).
5. If the plan requires a DB migration, write the new Liquibase changeset file in
   apps/api/src/main/resources/db/changelog/ (new file — never modify an existing one).
6. Run the linter (run_linter_backend) after writing Java files; fix any failures.
7. Run the tests (run_tests_backend) to verify ArchUnit and unit tests pass.
8. Run the Sonar quality gate (run_sonar_backend); if it returns findings in
   files you modified, fix them and re-run. Maximum 2 attempts. If the second
   attempt still returns findings, write `BLOCKED: Sonar gate failed after 2
   attempts — <summary>` in your notes and stop. Legacy issues in unmodified
   files are filtered out by the tool — do not act on anything outside the
   returned findings list.
9. Return your result dict with files_modified, success, and notes.

Constraints:
- Only implement backend files (apps/api/) — skip any frontend files listed in the plan.
- Never write to apps/web/ or to apps/web/src/components/ui/.
- Never modify an existing Liquibase changeset file.
- Never add Maven dependencies unless declared as `NEW DEPENDENCY (backend)` in the plan; for a
  declared one, add the `<dependency>` to pom.xml. Never use an undeclared, absent dependency.
- Every @Test method must have a @DisplayName.
- All Java code must follow google-java-format (Spotless enforces this).
"""

    logger = get_logger(task_id)
    logger.debug("Resolved model options: %s", get_model_options(phase="execution"), extra={"role": "be_engineer"})
    logger.debug("Agent prompt (truncated):\n%s", task_prompt[:800], extra={"role": "be_engineer"})
    reset_write_tracker()
    _checks.reset(task_id, role="be_engineer")
    try:
        raw = agent.run(task_prompt)
        # ascii() escapes non-ASCII chars (e.g. agent-emitted '→') for cp1252 console safety on Windows.
        logger.debug("Agent raw output: %s", ascii(raw)[:500], extra={"role": "be_engineer"})
    except Exception as exc:
        # The circuit breaker interrupts via agent.interrupt() (raises here), and a
        # model/runtime crash lands here too. Either way, turn it into a structured
        # failure carrying the concrete check output instead of an opaque traceback.
        logger.warning(
            "Agent run stopped early: %s: %s",
            type(exc).__name__,
            exc,
            extra={"role": "be_engineer"},
            exc_info=True,
        )
        raw = f"Agent run stopped early ({type(exc).__name__}: {exc})."

    # Files actually written are the source of truth; the LLM's final_answer is only
    # trusted for an explicit success flag (see _result.normalize_result).
    result = normalize_result(raw)

    # An engineer that ends on a red check did not finish, regardless of its claim.
    if _checks.last_check_failed():
        result["success"] = False
    diagnostics = _checks.last_failure_diagnostics()
    if diagnostics:
        result["diagnostics"] = diagnostics
    return result
