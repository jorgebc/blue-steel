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