"""Console timeline + spinner for the Blue Steel pipeline.

Single source of truth for how a phase is rendered on the console:

    >>> Planning starting (est 2m 00s)
    | Planning     elapsed 0m 12s / est 2m 00s     <- live spinner line (TTY only)
    [OK] Planning done in 1m 58s (est 2m 00s)

The spinner runs on a background daemon thread that writes carriage-return updates
to ``sys.stdout`` — the same stream the logger's console handler uses, so a single
shared lock (``console_lock``) serializes the two writers and the spinner-aware
handler can clear the spinner line before printing a log record.

Estimates are learned from the previous run (``.ai/logs/_phase_durations.json``),
falling back to the constants in ``PHASE_META`` on the first run.

Only stdlib (threading/json/os/contextlib) + the project logger are used here.
"""

import json
import os
import sys
import threading
import time
from contextlib import contextmanager
from pathlib import Path

from logger import MARKER_OK, MARKER_START, get_logger, is_tty

# node name -> (display label, default estimate seconds, log role)
PHASE_META: dict[str, tuple[str, int, str]] = {
    "planning":     ("Planning",     120, "planning"),
    "execution":    ("Execution",    480, "execution"),
    "quality":      ("Quality",      300, "quality"),
    "final_review": ("Final Review",  60, "final"),
}

_FRAMES = ("|", "/", "-", "\\")  # ASCII-safe for Windows cp1252 consoles
_TICK_SECONDS = 0.2

_LOGS_DIR = Path(__file__).parent.parent / "logs"
_DURATIONS_PATH = _LOGS_DIR / "_phase_durations.json"

# The one lock shared between the spinner thread and the logger console handler.
console_lock = threading.RLock()


def _fmt(seconds: float) -> str:
    seconds = max(0, int(seconds))
    m, s = divmod(seconds, 60)
    return f"{m}m {s:02d}s" if m else f"{s}s"


# ── Estimate persistence ────────────────────────────────────────────────────


def load_estimate(node: str) -> int:
    """Return the estimated duration (seconds) for *node*.

    Uses the previous run's recorded duration when available, else the
    ``PHASE_META`` constant, else 60s.
    """
    _, default, _ = PHASE_META.get(node, (node, 60, "pipeline"))
    try:
        data = json.loads(_DURATIONS_PATH.read_text(encoding="utf-8"))
        return int(data.get(node, default))
    except (OSError, ValueError, TypeError):
        return default


def read_durations() -> dict[str, float]:
    """Return the recorded per-node durations (seconds), or an empty dict."""
    try:
        return json.loads(_DURATIONS_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def fmt_duration(seconds: float) -> str:
    """Public formatter for ``Mm SSs`` / ``SSs`` strings."""
    return _fmt(seconds)


def record_duration(node: str, seconds: float) -> None:
    """Persist *node*'s actual duration for the next run's estimate (atomic write).

    Only call on phase success — a fast failure must not poison the estimate.
    """
    try:
        _LOGS_DIR.mkdir(parents=True, exist_ok=True)
        data: dict = {}
        if _DURATIONS_PATH.exists():
            try:
                data = json.loads(_DURATIONS_PATH.read_text(encoding="utf-8"))
            except ValueError:
                data = {}
        data[node] = round(float(seconds), 1)
        tmp = _DURATIONS_PATH.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(data, indent=2), encoding="utf-8")
        os.replace(tmp, _DURATIONS_PATH)
    except OSError:
        pass  # advisory only — never break the pipeline over an estimate file


# ── Spinner ─────────────────────────────────────────────────────────────────


class _Spinner:
    """A single background spinner, toggled per phase via start()/stop().

    The thread is created lazily on first start and lives (sleeping) for the
    rest of the process; it never joins, so the console handler can deactivate
    it while holding ``console_lock`` without any risk of a join deadlock.
    """

    def __init__(self) -> None:
        self._thread: threading.Thread | None = None
        self._terminate = threading.Event()
        self._active = False
        self._label = ""
        self._est = 0
        self._t0 = 0.0
        self._frame = 0
        self._last_len = 0

    # -- public API (acquire the lock themselves) --

    def start(self, label: str, est_seconds: int) -> None:
        if not is_tty():
            return
        with console_lock:
            self._label = label
            self._est = est_seconds
            self._t0 = time.monotonic()
            self._frame = 0
            self._active = True
            if self._thread is None:
                self._thread = threading.Thread(target=self._run, daemon=True)
                self._thread.start()

    def stop(self) -> None:
        with console_lock:
            self.deactivate_locked()

    # -- lock-held helpers (caller already owns console_lock) --

    def deactivate_locked(self) -> None:
        was_active = self._active
        self._active = False
        if was_active:
            self.clear_line_locked()

    def clear_line_locked(self) -> None:
        if self._last_len and is_tty():
            sys.stdout.write("\r" + " " * self._last_len + "\r")
            sys.stdout.flush()
            self._last_len = 0

    # -- background loop --

    def _run(self) -> None:
        while not self._terminate.is_set():
            with console_lock:
                if self._active:
                    self._draw_locked()
            time.sleep(_TICK_SECONDS)

    def _draw_locked(self) -> None:
        frame = _FRAMES[self._frame % len(_FRAMES)]
        self._frame += 1
        elapsed = time.monotonic() - self._t0
        line = (
            f"{frame} {self._label.ljust(12)} "
            f"elapsed {_fmt(elapsed)} / est {_fmt(self._est)}"
        )
        sys.stdout.write("\r" + line + " " * max(0, self._last_len - len(line)))
        sys.stdout.flush()
        self._last_len = len(line)


spinner = _Spinner()


# ── Phase rendering (used by callbacks.py and run_task.py) ────────────────────


def _role(node: str) -> str:
    _, _, role = PHASE_META.get(node, (node, 60, "pipeline"))
    return role


def phase_begin(node: str, task_id: str) -> dict:
    """Log the phase banner, start the spinner, return state for ``phase_end``."""
    label, _, role = PHASE_META.get(node, (node, 60, "pipeline"))
    est = load_estimate(node)
    get_logger(task_id).info(
        f"{MARKER_START} {label} starting (est {_fmt(est)})", extra={"role": role}
    )
    spinner.start(label, est)
    return {"node": node, "label": label, "role": role, "est": est, "t0": time.monotonic()}


def phase_end(task_id: str, begin: dict, ok: bool = True) -> None:
    """Stop the spinner and, on success, log the done line + record the duration."""
    spinner.stop()
    if not ok:
        return  # the failing caller logs the error; do not record a poisoned duration
    elapsed = time.monotonic() - begin["t0"]
    get_logger(task_id).info(
        f"{MARKER_OK} {begin['label']} done in {_fmt(elapsed)} (est {_fmt(begin['est'])})",
        extra={"role": begin["role"]},
    )
    record_duration(begin["node"], elapsed)


@contextmanager
def phase_progress(node: str, task_id: str):
    """Context manager wrapping a single phase: banner + spinner + done line.

    On exception the spinner is stopped and no duration is recorded; the
    exception propagates so the caller's error handling still runs.
    """
    begin = phase_begin(node, task_id)
    ok = True
    try:
        yield
    except BaseException:
        ok = False
        raise
    finally:
        phase_end(task_id, begin, ok=ok)
