"""Pipeline state definition for the Blue Steel LangGraph orchestrator."""

import operator
from typing import Annotated, TypedDict


class PipelineState(TypedDict, total=False):
    task_id: str
    phase: int
    plan_path: str | None
    execution_summary: dict | None
    quality_passed: bool
    review_verdict: str | None
    final_review_error: str | None
    secops_verdict: str | None
    iteration_count: int
    blocked: bool
    blocked_reason: str | None
    blocked_detail: str | None
    completed: bool
    log: Annotated[list[str], operator.add]
