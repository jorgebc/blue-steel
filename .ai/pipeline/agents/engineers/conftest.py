"""pytest path setup for the engineers test modules.

The engineer modules use bare imports (e.g. `import be_agent`, `from execution_crew
import ...`) that rely on both `.ai/pipeline/` and this directory being on sys.path —
the orchestrator and the run-as-script harness add them, but pytest's collector does
not. Adding them here lets `python -m pytest .ai/pipeline/agents/engineers/` collect
every test module without touching the modules themselves.
"""

import sys
from pathlib import Path

_ENGINEERS_DIR = Path(__file__).parent
_PIPELINE_DIR = _ENGINEERS_DIR.parents[1]
for _p in (str(_PIPELINE_DIR), str(_ENGINEERS_DIR)):
    if _p not in sys.path:
        sys.path.insert(0, _p)
