"""LangGraph callback handler for Blue Steel pipeline observability.

Fires on each graph node (phase) to log start/end with timing and to
print phase separators on the console.
"""

import time
from typing import Any, Dict, Union

from colorama import Fore, Style
from langchain_core.callbacks import BaseCallbackHandler

from logger import (
    MARKER_FAIL,
    MARKER_OK,
    MARKER_START,
    get_logger,
    print_separator,
)

# Maps LangGraph node names (and raw function names) to pipeline roles.
_NODE_TO_ROLE: dict[str, str] = {
    "planning":           "planning",
    "execution":          "execution",
    "quality":            "quality",
    "final_review":       "final",
    "done":               "pipeline",
    "error":              "pipeline",
    "_planning_node":     "planning",
    "_execution_node":    "execution",
    "_quality_node":      "quality",
    "_final_review_node": "final",
    "_done_node":         "pipeline",
    "_error_node":        "pipeline",
}


class PipelineConsoleCallback(BaseCallbackHandler):
    """Callback handler that logs phase start/end and prints console separators."""

    def __init__(self, task_id: str) -> None:
        super().__init__()
        self.task_id   = task_id
        self.logger    = get_logger(task_id)
        # run_id -> (role, monotonic start time)
        self._phases: dict[str, tuple[str, float]] = {}
        self._first_phase = True

    # ── node start ────────────────────────────────────────────────────────────

    def on_chain_start(
        self,
        serialized: Dict[str, Any],
        inputs: Dict[str, Any],
        **kwargs: Any,
    ) -> None:
        run_id = str(kwargs.get("run_id", ""))
        name   = (serialized or {}).get("name", "") or kwargs.get("name", "")
        role   = _NODE_TO_ROLE.get(name, "pipeline")
        self._phases[run_id] = (role, time.monotonic())

        if not self._first_phase:
            print_separator()
        self._first_phase = False

        self.logger.info(f"{MARKER_START} Phase started", extra={"role": role})

    # ── node end ──────────────────────────────────────────────────────────────

    def on_chain_end(self, outputs: Dict[str, Any], **kwargs: Any) -> None:
        run_id          = str(kwargs.get("run_id", ""))
        role, t0        = self._phases.pop(run_id, ("pipeline", time.monotonic()))
        elapsed_s       = time.monotonic() - t0
        mins            = int(elapsed_s // 60)
        secs            = int(elapsed_s % 60)
        elapsed_str     = f"{mins}m {secs:02d}s" if mins > 0 else f"{secs}s"
        self.logger.info(
            f"{MARKER_OK} Phase completed ({elapsed_str})", extra={"role": role}
        )
        print_separator()

    # ── node error ────────────────────────────────────────────────────────────

    def on_chain_error(
        self,
        error: Union[Exception, KeyboardInterrupt],
        **kwargs: Any,
    ) -> None:
        run_id = str(kwargs.get("run_id", ""))
        self._phases.pop(run_id, None)
        msg = str(error)
        print(f"{Fore.RED}{MARKER_FAIL} {msg}{Style.RESET_ALL}")
        self.logger.error(f"{MARKER_FAIL} {msg}", extra={"role": "pipeline"})
