"""Pipeline state definition for the Blue Steel LangGraph orchestrator."""

import operator
from typing import Annotated, TypedDict


class PipelineState(TypedDict):
    task_id: str
    phase: int
    plan_path: str | None
    execution_summary: dict | None
    verification_passed: bool
    review_verdict: str | None
    secops_verdict: str | None
    iteration_count: int
    blocked: bool
    blocked_reason: str | None
    completed: bool
    log: Annotated[list[str], operator.add]
