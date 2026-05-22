"""pytest path setup for the planning test modules.

The planning modules use bare imports (e.g. `from planning_crew import ...`) that
rely on both `.ai/pipeline/` and this directory being on sys.path — the run-as-script
harness adds the script's own dir, but pytest's collector does not. Adding them here
lets `python -m pytest .ai/pipeline/agents/planning/` collect every test module
without touching the modules themselves.
"""

import sys
from pathlib import Path

_PLANNING_DIR = Path(__file__).parent
_PIPELINE_DIR = _PLANNING_DIR.parents[1]
for _p in (str(_PIPELINE_DIR), str(_PLANNING_DIR)):
    if _p not in sys.path:
        sys.path.insert(0, _p)
