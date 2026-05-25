"""Pipeline LLM configuration — reads litellm_config.yaml and returns the correct model."""

import os
from pathlib import Path

import yaml

_CONFIG_PATH = Path(__file__).parent.parent / "litellm_config.yaml"

_PHASE_MODEL_MAP = {
    "planning":  "local-reasoning",
    "execution": "local-coding",
    "review":    "local-reasoning",
    "secops":    "local-reasoning",
    "final":     "local-reasoning",
}


def _load_config() -> dict:
    with open(_CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def should_stream() -> bool:
    """Return True when running in local mode — streams tokens to the console."""
    return os.environ.get("PIPELINE_MODE", "cloud") == "local"


# Ollama's default context window (commonly 2048–4096 tokens) is far smaller than
# our prompts — persona + smolagents scaffold + full plan run ~6–10k tokens — so the
# window silently drops the oldest tokens (the persona and plan), a major driver of
# hallucinated APIs. We pass an explicit large num_ctx for local runs. 16384 fits a
# 14B Q4 model (~9 GB) plus KV cache inside a 12 GB GPU; drop to 8192 if VRAM is tight.
_LOCAL_NUM_CTX = 16384

# Hard wall-clock budget for a single LLM call (one agent step), enforced by
# llm_timeout.TimeoutLiteLLMModel. litellm's own ``timeout`` is unreliable against a
# stalled Ollama generation (a run was observed hung >60 min past a 30-min timeout),
# and smolagents' interrupt only fires between steps — so without this, one wedged
# call stalls the whole pipeline forever. A legit local step is ~1–10 min (plus
# model-swap overhead), so 20 min is generous headroom while still bounding a hang.
LLM_CALL_TIMEOUT_S = 1200


def get_model_options(phase: str = "planning") -> dict:
    """Return generation/runtime options (sampling + context window) for a phase.

    Code generation wants near-greedy decoding, so the execution phase uses the
    lowest temperature; reasoning phases use a slightly higher (still low) value.
    For local (Ollama) runs we also send an explicit large ``num_ctx`` so the model
    actually sees the persona and plan instead of a silently truncated window.

    The returned dict is spread into ``LiteLLMModel(...)`` (forwarded to
    ``litellm.completion``) or merged into a direct ``litellm.completion`` call.
    ``litellm_settings.drop_params: true`` makes any provider-unsupported key
    (e.g. ``num_ctx`` on Anthropic) a safe no-op.
    """
    mode = os.environ.get("PIPELINE_MODE", "cloud")
    options: dict = {"temperature": 0.1 if phase == "execution" else 0.2}
    if mode == "local":
        options["top_p"] = 0.9
        options["num_ctx"] = _LOCAL_NUM_CTX
    return options


def get_llm(phase: str = "planning") -> dict:
    """Return the litellm model params for the active PIPELINE_MODE and phase.

    In cloud mode, all phases use the single cloud model.
    In local mode, each phase uses the most appropriate local model.
    """
    config = _load_config()
    mode = os.environ.get("PIPELINE_MODE", "cloud")

    if mode == "local":
        model_name = _PHASE_MODEL_MAP.get(phase, "local-reasoning")
    else:
        model_name = "cloud-model"

    for entry in config["model_list"]:
        if entry["model_name"] == model_name:
            return entry["litellm_params"]

    raise ValueError(f"Model '{model_name}' not found in litellm_config.yaml")