"""LangGraph callback handler for Blue Steel pipeline observability.

Renders the console *story*: a banner + live spinner when a real phase node
starts, and a timed done-line when it ends. LangGraph fires ``on_chain_start``
for the graph itself and internal channels too, so only nodes present in
``PHASE_META`` drive the spinner — everything else is ignored to avoid flicker.

Prompts, results and agent traces never come through here; they live in the
per-task ``.log`` file.
"""

from typing import Any, Dict, Union

from langchain_core.callbacks import BaseCallbackHandler

from logger import MARKER_FAIL, get_logger
from progress import PHASE_META, phase_begin, phase_end, spinner

# LangGraph passes either the node name or the raw function name.
_NODE_ALIASES: dict[str, str] = {
    "_planning_node":     "planning",
    "_execution_node":    "execution",
    "_quality_node":      "quality",
    "_final_review_node": "final_review",
}


def _resolve_node(name: str) -> str | None:
    """Map a chain name to a PHASE_META node, or None if it is not a real phase."""
    node = _NODE_ALIASES.get(name, name)
    return node if node in PHASE_META else None


class PipelineConsoleCallback(BaseCallbackHandler):
    """Drives the phase banner + spinner + timed done-line for each pipeline node."""

    def __init__(self, task_id: str) -> None:
        super().__init__()
        self.task_id = task_id
        self.logger  = get_logger(task_id)
        # run_id -> phase_begin() state dict
        self._phases: dict[str, dict] = {}

    # ── node start ──────────────────────────────────────────────────────────

    def on_chain_start(
        self,
        serialized: Dict[str, Any],
        inputs: Dict[str, Any],
        **kwargs: Any,
    ) -> None:
        name = (serialized or {}).get("name", "") or kwargs.get("name", "")
        node = _resolve_node(name)
        if node is None:
            return  # internal channel / graph wrapper — not a phase
        run_id = str(kwargs.get("run_id", ""))
        self._phases[run_id] = phase_begin(node, self.task_id)

    # ── node end ────────────────────────────────────────────────────────────

    def on_chain_end(self, outputs: Dict[str, Any], **kwargs: Any) -> None:
        run_id = str(kwargs.get("run_id", ""))
        begin = self._phases.pop(run_id, None)
        if begin is None:
            return
        # Nodes catch their own exceptions and return a ``blocked`` state instead
        # of raising, so a failed phase still arrives here. Treat it as failure:
        # stop the spinner, skip the "done" line, and don't record its duration.
        ok = not (isinstance(outputs, dict) and outputs.get("blocked"))
        phase_end(self.task_id, begin, ok=ok)

    # ── node error ──────────────────────────────────────────────────────────

    def on_chain_error(
        self,
        error: Union[Exception, KeyboardInterrupt],
        **kwargs: Any,
    ) -> None:
        run_id = str(kwargs.get("run_id", ""))
        begin = self._phases.pop(run_id, None)
        # Stop the spinner before the error is logged so the failure is never
        # overwritten by a spinner tick.
        spinner.stop()
        if begin is not None:
            phase_end(self.task_id, begin, ok=False)
        self.logger.error(
            f"{MARKER_FAIL} {type(error).__name__}: {error}",
            extra={"role": "pipeline"},
            exc_info=error if isinstance(error, BaseException) else None,
        )
