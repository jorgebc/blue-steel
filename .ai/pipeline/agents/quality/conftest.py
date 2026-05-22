"""pytest path setup for the quality test modules.

The quality modules use bare imports (e.g. `import verification_agent`, `from
quality_pipeline import ...`) that rely on both `.ai/pipeline/` and this directory
being on sys.path — the run-as-script harness adds the script's own dir, but pytest's
collector does not. Adding them here lets `python -m pytest .ai/pipeline/agents/quality/`
collect every test module without touching the modules themselves.
"""

import sys
from pathlib import Path

_QUALITY_DIR = Path(__file__).parent
_PIPELINE_DIR = _QUALITY_DIR.parents[1]
for _p in (str(_PIPELINE_DIR), str(_QUALITY_DIR)):
    if _p not in sys.path:
        sys.path.insert(0, _p)
