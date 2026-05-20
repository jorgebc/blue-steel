"""Product Owner agent for Blue Steel planning pipeline.

Loads the PO persona from prompts/product_owner.md and wraps it in a
CodeAgent equipped with read_file and write_file tools.
"""

import importlib.resources
import os
import sys
import yaml
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from smolagents import CodeAgent, LiteLLMModel, LogLevel, tool

from config import get_llm
from logger import get_logger
from tools.filesystem import read_file as _read_file, write_file as _write_file

_PROMPTS_DIR = Path(__file__).parents[2] / "prompts"


def _make_model() -> LiteLLMModel:
    """Build a LiteLLMModel from pipeline config, resolving litellm env-var notation."""
    llm_params = get_llm(phase="planning")
    model_id: str = llm_params["model"]
    api_key_raw: str = llm_params.get("api_key", "")
    api_base: str | None = llm_params.get("api_base")

    # litellm uses "os.environ/VAR_NAME" as a config-file convention
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
        timeout=1800,  # 30 min — local models (qwen3:14b) can take 5-10 min per step
    )


def _build_prompt_templates(persona_content: str) -> dict:
    """Prepend the Blue Steel PO persona to the default CodeAgent system prompt."""
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
        path: File path relative to the repo root, e.g. 'docs/PRD.md' or
              'apps/api/src/main/java/com/bluesteel/domain/session/Session.java'.

    Returns:
        File content as a UTF-8 string.
    """
    return _read_file(path)


@tool
def write_project_file(path: str, content: str) -> str:
    """Write content to a file in the Blue Steel project, creating directories as needed.

    Protected paths (shadcn/ui components, Liquibase changelogs) will raise PermissionError.

    Args:
        path: File path relative to the repo root.
        content: Text content to write (UTF-8).

    Returns:
        Confirmation message with the written path.
    """
    _write_file(path, content)
    return f"Written: {path}"


_CODE_FORMAT_GUIDANCE = """
---
CRITICAL INSTRUCTION — READ BEFORE WRITING ANY CODE:

Your output MUST be produced as Python code using this EXACT pattern.
Do NOT write analysis text as raw Python code — ALL text must be inside string literals.

COPY THIS TEMPLATE EXACTLY and fill in the [...] sections:

```python
answer = (
    "## 1. Scope -- In\\n"
    "[What this task must deliver, one item per line ending with \\\\n]\\n"
    "\\n"
    "## 2. Scope -- Out\\n"
    "[What is excluded. Reference decisions as text: D-042, D-054, etc.]\\n"
    "\\n"
    "## 3. Acceptance Criteria\\n"
    "Scenario 1: [Name]\\n"
    "Given: [precondition]\\n"
    "When: [action]\\n"
    "Then: [expected result]\\n"
    "\\n"
    "Scenario 2: [Name]\\n"
    "Given: [precondition]\\n"
    "When: [action]\\n"
    "Then: [expected result]\\n"
    "\\n"
    "## 4. User Impact\\n"
    "[Which roles are affected and how]\\n"
    "\\n"
    "## 5. UX Requirements\\n"
    "[List UX_CONSTITUTION.md rules that apply, e.g. no modals D-082]\\n"
    "\\n"
    "## 6. Open Questions for the Architect\\n"
    "[Questions about implementation details]\\n"
)
final_answer(answer)
```

MANDATORY RULES — failure to follow these causes syntax errors:
1. ALL content goes inside string literals (between the quotes after each opening paren line)
2. Use two backslashes then n (\\\\n written in source = \\n in the string = newline)
3. Decision numbers use the letter D, a hyphen, and digits: D-042 D-053 D-082 (never bare 042)
4. No triple-quoted strings
5. No raw text outside of string literals
6. Call final_answer(answer) as the LAST statement
"""


def _create_agent() -> CodeAgent:
    persona = (_PROMPTS_DIR / "product_owner.md").read_text(encoding="utf-8")
    return CodeAgent(
        tools=[read_project_file, write_project_file],
        model=_make_model(),
        prompt_templates=_build_prompt_templates(persona),
        max_steps=6,
        verbosity_level=LogLevel.OFF,  # ReAct trace -> silenced; story lives in the logger
    )


def run(task_id: str, context: dict) -> str:
    """Run the PO agent for a given Blue Steel task.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.
        context: Dict with optional keys:
            - 'description': human-readable task description
            - 'roadmap_entry': full roadmap section text
            - 'prd': PRD.md content
            - 'round': 1 (initial scope) or 2 (challenge mode)
            - 'architect_proposal': architect's first-round output (for round 2)

    Returns:
        The PO's output: scope definition + acceptance criteria in round 1, or
        a targeted challenge in round 2.
    """
    agent = _create_agent()
    round_num = context.get("round", 1)

    if round_num == 1:
        task_prompt = f"""
You are acting as the Product Owner for Blue Steel.
{_CODE_FORMAT_GUIDANCE}

## Task
Task ID: {task_id}
Description: {context.get('description', 'See roadmap entry below')}

## Roadmap Entry
{context.get('roadmap_entry', '')}

## PRD Reference
{context.get('prd', '')}

## Your Output (Round 1 — Define Scope)

Produce the following for task {task_id}:

1. **Scope — In** (what this task MUST deliver)
2. **Scope — Out** (what this task MUST NOT include; cite D-numbers where applicable)
3. **Acceptance Criteria** — minimum 5 Given/When/Then scenarios covering:
   - The primary happy path
   - At least one rejection/error path (409, 422, or 400 response)
   - At least one Blue Steel-specific invariant (UNCERTAIN cards, single active draft, role enforcement, etc.)
   - At least one UX rule if this task has a frontend component
4. **User Impact** — which roles are affected and what changes from their perspective
5. **UX Requirements** — list any UX_CONSTITUTION.md rules that must be enforced (if frontend)
6. **Open Questions for the Architect** — anything unclear from a product perspective

Be specific to Blue Steel. Use the domain vocabulary (Session, DiffCard, NarrativeBlock, etc.).
Do NOT propose file paths or implementation details — that is the architect's job.
"""
    else:
        task_prompt = f"""
You are acting as the Product Owner for Blue Steel in challenge mode.
{_CODE_FORMAT_GUIDANCE}

## Task
Task ID: {task_id}

## Architect's Proposal
{context.get('architect_proposal', '')}

## Your Output (Round 2 — Challenge)

Review the architect's proposal and challenge it on product grounds:

1. **Scope Compliance** — does the proposal stay within scope? Has anything crept in that's out of scope?
2. **Acceptance Criteria Coverage** — is every Given/When/Then scenario covered by a specific named element?
3. **UX Rule Compliance** — does the frontend proposal respect D-082 (no modals), D-083 (no toasts), D-086 (no spinners)?
4. **Role Authorization** — is role enforcement applied at the use-case level (not just the controller)?
5. **Missing Elements** — what is NOT addressed that should be?
6. **Specific Requests** — name specific changes you require before you will approve this plan

Be direct and precise. Cite D-numbers when challenging decisions. Do not accept generic or placeholder answers.
"""

    logger = get_logger(task_id)
    logger.debug("Agent prompt (truncated):\n%s", task_prompt[:800], extra={"role": "po"})
    raw = agent.run(task_prompt)
    # ascii() escapes non-ASCII chars (e.g. agent-emitted '→') for cp1252 console safety on Windows.
    logger.debug("Agent raw output: %s", ascii(raw)[:500], extra={"role": "po"})
    return raw
