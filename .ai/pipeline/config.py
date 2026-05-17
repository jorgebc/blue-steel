"""Pipeline LLM configuration — reads litellm_config.yaml and returns the correct model."""

import os
from pathlib import Path

import yaml

_CONFIG_PATH = Path(__file__).parent.parent / "litellm_config.yaml"


def _load_config() -> dict:
    with open(_CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def get_llm() -> dict:
    """Return the litellm model params for the active PIPELINE_MODE."""
    config = _load_config()
    mode = os.environ.get("PIPELINE_MODE", "cloud")

    if mode == "local":
        model_name = "pipeline-model-local"
    else:
        model_name = "pipeline-model"

    for entry in config["model_list"]:
        if entry["model_name"] == model_name:
            return entry["litellm_params"]

    raise ValueError(f"Model '{model_name}' not found in litellm_config.yaml")
