"""Structured logger for the Blue Steel AI pipeline.

Usage:
    logger = get_logger(task_id)
    logger.info("--- Analyzing PRD...", extra={"role": "po"})
    logger.info("[OK] Phase complete", extra={"role": "planning"})

Console output is colored by role; file output is plain text with no ANSI codes.
"""

import logging
import re
import sys
from datetime import datetime
from pathlib import Path

from colorama import Fore, Style

ROLE_COLORS: dict[str, str] = {
    "pipeline":     Fore.WHITE,
    "planning":     Fore.CYAN,
    "po":           Fore.BLUE,
    "architect":    Fore.MAGENTA,
    "execution":    Fore.GREEN,
    "be_engineer":  Fore.GREEN,
    "fe_engineer":  Fore.LIGHTGREEN_EX,
    "quality":      Fore.YELLOW,
    "verification": Fore.YELLOW,
    "review":       Fore.LIGHTYELLOW_EX,
    "secops":       Fore.RED,
    "final":        Fore.CYAN,
}

MARKER_START   = ">>>"
MARKER_OK      = "[OK]"
MARKER_FAIL    = "[FAIL]"
MARKER_BLOCKED = "[BLOCKED]"
MARKER_INFO    = "---"

_ANSI_ESCAPE = re.compile(
    # SGR (color/style):           \x1b[...m
    # OSC (window title, etc.):    \x1b]...\x07
    # Cursor visibility / DECSET:  \x1b[?...h  or  \x1b[?...l
    # Misc CSI ending in a letter: \x1b[...<letter>
    r"\x1b\[[0-9;]*m"
    r"|\x1b\][^\x07]*\x07"
    r"|\x1b\[\?[0-9;]*[hl]"
    r"|\x1b\[[0-9;]*[A-Za-z]"
)

# Patterns that should never land in a log file in plaintext. Each pattern keeps
# enough context for debugging while masking the credential body.
_SECRET_PATTERNS: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"sk-ant-[A-Za-z0-9_\-]{6,}"),            "sk-ant-***REDACTED***"),
    (re.compile(r"sk-[A-Za-z0-9]{20,}"),                  "sk-***REDACTED***"),
    (re.compile(r"(?i)bearer\s+[A-Za-z0-9._\-]+"),        "Bearer ***REDACTED***"),
    (re.compile(r"(?i)(password|passwd|pwd)\s*=\s*\S+"),  r"\1=***REDACTED***"),
    (re.compile(r"(?i)(api[_-]?key)\s*=\s*\S+"),          r"\1=***REDACTED***"),
)


def _scrub_secrets(text: str) -> str:
    for pattern, replacement in _SECRET_PATTERNS:
        text = pattern.sub(replacement, text)
    return text


_SEPARATOR   = "-" * 58
_LOGS_DIR    = Path(__file__).parent.parent / "logs"

_LOGGERS: dict[str, logging.Logger] = {}


class _ColoredConsoleFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        role  = getattr(record, "role", "pipeline")
        color = ROLE_COLORS.get(role, Fore.WHITE)
        ts    = datetime.fromtimestamp(record.created).strftime("%H:%M:%S")
        col   = role.ljust(12)
        msg   = record.getMessage()
        return f"{color}{ts}  {col} {msg}{Style.RESET_ALL}"


class _PlainFileFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        role  = getattr(record, "role", "pipeline")
        ts    = datetime.fromtimestamp(record.created).strftime("%Y-%m-%d %H:%M:%S")
        level = record.levelname
        msg   = _ANSI_ESCAPE.sub("", record.getMessage())
        msg   = _scrub_secrets(msg)
        return f"{ts} [{level}] {role} - {msg}"


def print_separator() -> None:
    """Print a phase separator to stdout only (never to the log file)."""
    print(_SEPARATOR)


def get_logger(task_id: str) -> logging.Logger:
    """Return (or create) the structured logger for *task_id*.

    Calling this multiple times with the same task_id is safe — the same
    Logger instance is returned without adding duplicate handlers.
    """
    if task_id in _LOGGERS:
        return _LOGGERS[task_id]

    logger = logging.getLogger(f"pipeline.{task_id}")
    logger.setLevel(logging.DEBUG)
    logger.propagate = False
    logger.handlers.clear()

    console = logging.StreamHandler(sys.stdout)
    console.setLevel(logging.DEBUG)
    console.setFormatter(_ColoredConsoleFormatter())
    logger.addHandler(console)

    _LOGS_DIR.mkdir(parents=True, exist_ok=True)
    fh = logging.FileHandler(
        str(_LOGS_DIR / f"{task_id}.log"), mode="w", encoding="utf-8"
    )
    fh.setLevel(logging.DEBUG)
    fh.setFormatter(_PlainFileFormatter())
    logger.addHandler(fh)

    _LOGGERS[task_id] = logger
    return logger
