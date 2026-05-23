"""Wall-clock watchdog for LLM calls.

litellm's ``timeout`` does not reliably abort a stalled Ollama generation, and
smolagents only checks its interrupt switch *between* steps — so a model call that
wedges mid-step stalls the entire pipeline indefinitely (observed: a run hung >60 min
past a 30-min timeout). This module enforces a hard per-call wall-clock budget.

``build_chat_model(phase)`` is the single model factory every agent uses; it wraps the
normal ``LiteLLMModel`` in ``TimeoutLiteLLMModel``, whose ``generate`` aborts after
``config.LLM_CALL_TIMEOUT_S`` and raises :class:`AgentTimeout`. The agents (engineers)
already turn that into a structured failure, and the orchestrator routes it to a clean
``blocked`` stop instead of hanging.

The cap is per call (one agent step), not per agent, so legitimate multi-step runs are
unaffected. CodeAgent runs non-streaming (``stream_outputs=False``), so every step goes
through ``Model.generate`` — overriding it covers all calls.
"""

import os
import sys
import threading
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))  # .ai/pipeline/ on path

from smolagents import LiteLLMModel

from config import LLM_CALL_TIMEOUT_S, get_llm, get_model_options


class AgentTimeout(RuntimeError):
    """Raised when a single LLM call exceeds its wall-clock budget."""


def run_with_timeout(fn, timeout_s: float, label: str):
    """Run ``fn()`` on a daemon thread; raise AgentTimeout if it outlasts ``timeout_s``.

    A wedged Ollama socket cannot be force-killed from Python, so the worker thread is a
    daemon: on timeout we abandon it (it dies with the process) and raise, letting the
    caller fail fast. Exceptions raised by ``fn`` are re-raised on the calling thread.
    """
    box: dict = {}

    def _worker() -> None:
        try:
            box["value"] = fn()
        except BaseException as exc:  # noqa: BLE001 — propagated to the caller below
            box["error"] = exc

    thread = threading.Thread(target=_worker, daemon=True, name=f"llm-{label}")
    thread.start()
    thread.join(timeout_s)
    if thread.is_alive():
        raise AgentTimeout(
            f"LLM call '{label}' exceeded its {timeout_s:.0f}s wall-clock budget "
            f"(likely a stalled Ollama generation)."
        )
    if "error" in box:
        raise box["error"]
    return box.get("value")


class TimeoutLiteLLMModel(LiteLLMModel):
    """A ``LiteLLMModel`` whose every ``generate`` call is bounded by a wall-clock timeout."""

    def __init__(self, *args, call_timeout: float, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._call_timeout = call_timeout

    def generate(self, *args, **kwargs):
        # Bind super().generate here: zero-arg super() does not work inside the lambda.
        parent_generate = super().generate
        return run_with_timeout(
            lambda: parent_generate(*args, **kwargs),
            self._call_timeout,
            label=self.model_id,
        )


def build_chat_model(phase: str) -> TimeoutLiteLLMModel:
    """Central factory for every CodeAgent's model.

    Resolves the phase's litellm params (incl. ``os.environ/`` api-key notation), applies
    the shared sampling/context options, and enforces the per-call wall-clock budget.
    """
    llm_params = get_llm(phase=phase)
    model_id: str = llm_params["model"]
    api_key_raw: str = llm_params.get("api_key", "")
    api_base: str | None = llm_params.get("api_base")

    api_key: str | None = None
    if isinstance(api_key_raw, str) and api_key_raw.startswith("os.environ/"):
        env_var = api_key_raw.split("/", 1)[1]
        api_key = os.environ.get(env_var)
    elif api_key_raw:
        api_key = api_key_raw

    return TimeoutLiteLLMModel(
        model_id=model_id,
        api_key=api_key,
        api_base=api_base,
        timeout=1800,  # litellm's own (inner) timeout; the wall-clock guard below is the real bound
        call_timeout=LLM_CALL_TIMEOUT_S,
        # Large context window + low temperature — see config.get_model_options.
        **get_model_options(phase=phase),
    )
