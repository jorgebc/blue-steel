"""Robust normalization of an engineer agent's run into a result dict.

`smolagents.CodeAgent.run()` is expected to return the dict passed to
`final_answer(result)`. With local models (and on max_steps exhaustion) it often
returns a *string* instead — sometimes the literal `result = {...}` /
`final_answer(result)` source the model emitted. The brittle original handling
treated that as `{"files_modified": [], "success": False}`, which falsely BLOCKED
runs whose files were in fact written.

`normalize_result` instead trusts the ground-truth write tracker
(`tools.filesystem.get_tracked_writes`) for the file list, and only honors an
explicit self-reported failure when the result dict is actually recoverable. A
garbled final answer with files on disk proceeds to the quality phase — the real
arbiter of broken output.
"""

import ast
import re

from tools.filesystem import get_tracked_writes


def _parse_agent_result(raw: object) -> dict | None:
    """Recover the intended result dict from an agent run output, or None.

    Handles three shapes: an actual dict; a string containing a ```python fenced
    block with `result = {...}`; or a bare `result = {...}` / `{...}` literal.
    """
    if isinstance(raw, dict):
        return raw
    if not isinstance(raw, str):
        return None

    text = raw.strip()

    # Prefer a fenced code block if present.
    fenced = re.search(r"```(?:python)?\s*(.*?)```", text, re.DOTALL)
    if fenced:
        text = fenced.group(1)

    # Try `result = { ... }` first, then any top-level `{ ... }` mapping.
    for pattern in (
        r"result\s*=\s*(\{.*\})",
        r"(\{.*\})",
    ):
        match = re.search(pattern, text, re.DOTALL)
        if not match:
            continue
        try:
            value = ast.literal_eval(match.group(1))
        except (ValueError, SyntaxError):
            continue
        if isinstance(value, dict):
            return value
    return None


def normalize_result(raw: object) -> dict:
    """Build the engineer result dict from `raw` and the write tracker.

    - files_modified: files actually written this run; falls back to the parsed
      list only if nothing was tracked.
    - success: an explicit bool from a recovered dict wins (so honest self-reported
      failures still stop the pipeline); otherwise success == "wrote at least one
      file" and the quality phase decides whether the output is correct.
    - notes: the parsed notes if available, else the raw output for the report.
    """
    tracked = get_tracked_writes()
    parsed = _parse_agent_result(raw)

    if parsed is not None:
        files = tracked or list(parsed.get("files_modified") or [])
        explicit = parsed.get("success")
        success = bool(explicit) if isinstance(explicit, bool) else bool(tracked)
        notes = parsed.get("notes") or (str(raw) if not isinstance(raw, dict) else "")
    else:
        files = tracked
        success = bool(tracked)
        notes = str(raw)

    return {"files_modified": files, "success": success, "notes": notes}
