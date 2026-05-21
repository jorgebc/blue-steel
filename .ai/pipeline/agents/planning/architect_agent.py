"""Architect agent for Blue Steel planning pipeline.

Loads the Architect persona from prompts/architect.md and wraps it in a
CodeAgent equipped with read_file, write_file, and list_files tools.
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
from tools.filesystem import (
    read_file as _read_file,
    write_file as _write_file,
    list_files as _list_files,
)

_PROMPTS_DIR = Path(__file__).parents[2] / "prompts"


def _make_model() -> LiteLLMModel:
    """Build a LiteLLMModel from pipeline config, resolving litellm env-var notation."""
    llm_params = get_llm(phase="planning")
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
        timeout=1800,  # 30 min — local models (qwen3:14b) can take 5-10 min per step
    )


def _build_prompt_templates(persona_content: str) -> dict:
    """Prepend the Blue Steel Architect persona to the default CodeAgent system prompt."""
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
              'apps/api/src/main/java/com/bluesteel/application/port/in/session/SubmitSessionUseCase.java'
              or 'apps/web/src/features/input/DiffReviewPage.tsx'.

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


@tool
def list_project_files(directory: str, pattern: str = "**/*") -> list:
    """List files matching a glob pattern within a project directory.

    Use this to explore the existing codebase structure before proposing new files.

    Args:
        directory: Directory path relative to the repo root, e.g.
                   'apps/api/src/main/java/com/bluesteel/application/service'.
        pattern: Glob pattern (default: '**/*' for all files recursively).
                 Examples: '*.java', '**/*.tsx', '*.xml'.

    Returns:
        Sorted list of absolute file paths matching the pattern.
    """
    return _list_files(directory, pattern)


_CODE_FORMAT_GUIDANCE = """
---
CRITICAL INSTRUCTION — READ BEFORE WRITING ANY CODE:

Your output MUST be produced as Python code using this EXACT pattern.
Do NOT write analysis text as raw Python code — ALL text must be inside string literals.

You may call read_project_file("path") or list_project_files("dir") to inspect existing code first.
Then build your answer string and call final_answer(answer).

COPY THIS TEMPLATE EXACTLY and fill in the [...] sections:

```python
answer = (
    "## 1. Technical Approach Summary\\n"
    "[2-3 sentences describing the solution]\\n"
    "\\n"
    "## 2. New Files to Create\\n"
    "- apps/api/src/main/java/com/bluesteel/application/service/auth/[ClassName].java\\n"
    "  Layer: application/service | Responsibility: [one sentence]\\n"
    "\\n"
    "## 3. Existing Files to Modify\\n"
    "- apps/api/src/main/java/com/bluesteel/adapters/in/security/SecurityConfig.java\\n"
    "  Change: [what changes and why]\\n"
    "\\n"
    "## 4. DB Migration Assessment\\n"
    "Required: Yes/No\\n"
    "File: [e.g., 0020_create_refresh_tokens.xml or N/A]\\n"
    "\\n"
    "## 5. API Contracts\\n"
    "POST /api/v1/auth/login -> 200 {data: {accessToken, forcePasswordChange}}\\n"
    "\\n"
    "## 6. Architecture Decision Compliance\\n"
    "D-043: [compliance status]\\n"
    "D-059: [compliance status]\\n"
    "\\n"
    "## 7. Dependencies on Existing Code\\n"
    "[Named existing classes this plan builds on]\\n"
    "\\n"
    "## 8. Identified Risks\\n"
    "[Technical risks and hard-to-test invariants]\\n"
)
final_answer(answer)
```

MANDATORY RULES — failure to follow these causes syntax errors:
1. ALL content goes inside string literals (between the quotes)
2. Use two backslashes then n (\\\\n written in source = \\n in string = newline)
3. Decision numbers: write the letter D, hyphen, digits: D-042 D-059 (never bare 042 or 059)
4. No triple-quoted strings
5. No raw text outside of string literals
6. Call final_answer(answer) as the LAST statement
"""


def _create_agent() -> CodeAgent:
    persona = (_PROMPTS_DIR / "architect.md").read_text(encoding="utf-8")
    return CodeAgent(
        tools=[read_project_file, write_project_file, list_project_files],
        model=_make_model(),
        prompt_templates=_build_prompt_templates(persona),
        max_steps=8,
        verbosity_level=LogLevel.OFF,  # ReAct trace -> silenced; story lives in the logger
    )


def run(task_id: str, context: dict) -> str:
    """Run the Architect agent for a given Blue Steel task.

    Args:
        task_id: Roadmap task identifier, e.g. 'F1.7'.
        context: Dict with optional keys:
            - 'po_output': Product Owner's round-1 output (scope + acceptance criteria)
            - 'architecture': ARCHITECTURE.md content
            - 'decisions': DECISIONS.md content
            - 'round': 1 (initial proposal) or 2 (final, after PO challenge)
            - 'po_challenge': Product Owner's round-2 challenge output

    Returns:
        The Architect's output: technical proposal (round 1) or finalized plan (round 2).
    """
    agent = _create_agent()
    round_num = context.get("round", 1)

    if round_num == 1:
        task_prompt = f"""
You are acting as the Software Architect for Blue Steel.
{_CODE_FORMAT_GUIDANCE}

## Task
Task ID: {task_id}

## Product Owner Requirements
{context.get('po_output', '')}

## Architecture Reference
{context.get('architecture', '')}

## Recorded Decisions
{context.get('decisions', '')}

## Your Output (Round 1 — Technical Proposal)

Produce a concrete technical proposal for task {task_id} with the following sections:

### 1. Technical Approach Summary
High-level description of the solution. What changes and why.

### 2. New Files to Create
For each new file, provide:
- Exact path from repo root (e.g., `apps/api/src/main/java/com/bluesteel/application/service/campaign/CreateCampaignService.java`)
- Which architectural layer it belongs to and why
- Its primary responsibility (one sentence)

### 3. Existing Files to Modify
For each file, provide:
- Exact path
- What changes are needed and why

### 4. DB Migration Assessment
Is a Liquibase migration required?
- If YES: provide the exact filename (e.g., `0020_create_query_log.xml`) and the tables/columns/indexes it adds
- If NO: explain why no schema change is needed

### 5. API Contracts (if applicable)
For each new or modified endpoint:
- Method + path
- Request body shape
- Response envelope shape (always `{{ "data": {{}}, "meta": {{}}, "errors": [] }}`)
- HTTP status codes used and when

### 6. Architecture Decision Compliance
List each D-NNN decision relevant to this task and confirm compliance or flag a violation.

### 7. Dependencies on Existing Blue Steel Code
Name specific existing classes/components/ports this plan depends on.

### 8. Identified Risks
What could go wrong? What invariants are hardest to test?

Be specific. Use real paths only. No placeholder paths like `src/your-module`.
Use Blue Steel domain vocabulary. Propose nothing that requires a decision violation.
"""
    else:
        task_prompt = f"""
You are acting as the Software Architect for Blue Steel responding to a PO challenge.
{_CODE_FORMAT_GUIDANCE}

## Task
Task ID: {task_id}

## PO Challenge
{context.get('po_challenge', '')}

## Your Previous Proposal (Round 1)
{context.get('architect_proposal_1', '')}

## Your Output (Round 2 — Finalized Plan)

Address every concern raised by the PO. Then produce the COMPLETE finalized plan with all 8 sections:

---

# Plan: {task_id}

## 1. Executive Summary
2-4 sentences: what this task delivers, who benefits, and what Blue Steel capability it enables.

## 2. Acceptance Criteria (Given/When/Then)
List all Given/When/Then scenarios from the PO (updated if any were revised). Mark each one with the
technical element that satisfies it.

## 3. Proposed Technical Solution
Complete list of files to create and modify, each with exact paths. Organized by layer:
- Domain (if any changes)
- Application model (if any new value types)
- Application ports/services (driving and driven)
- Adapters/in (REST controllers, DTOs)
- Adapters/out (persistence, AI)
- Frontend (feature components, API client, types)

## 4. Dependencies on Existing Blue Steel Code
Named existing classes, interfaces, components this plan builds on.

## 5. New or Modified API Contracts
Method + path, request shape, response envelope, HTTP status codes.
State "No API contract changes" if not applicable.

## 6. DB Migration Required
Yes / No. If Yes: exact filename and what it creates.
If No: one sentence explaining why no schema change is needed.

## 7. Identified Risks
Specific technical risks — invariants that are hard to test, async timing concerns,
pgvector query correctness, etc.

## 8. Explicitly Out of Scope for This Task
Itemized list of what this task deliberately does NOT implement (cite D-numbers where relevant).

---

Do not add anything beyond these 8 sections. Every file path must be real. Every D-number citation
must reference a real decision from DECISIONS.md.
"""

    logger = get_logger(task_id)
    logger.debug("Agent prompt (truncated):\n%s", task_prompt[:800], extra={"role": "architect"})
    raw = agent.run(task_prompt)
    # ascii() escapes non-ASCII chars (e.g. agent-emitted '→') for cp1252 console safety on Windows.
    logger.debug("Agent raw output: %s", ascii(raw)[:500], extra={"role": "architect"})
    return raw
