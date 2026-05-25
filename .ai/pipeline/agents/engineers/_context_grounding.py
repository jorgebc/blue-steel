"""Context grounding for the Blue Steel BE/FE engineers.

A 14B local model (qwen2.5-coder) cannot reliably *recall* the project's real
symbols, so it invents APIs (wrong framework, default-vs-named imports, store
methods that don't exist). This module turns that recall problem into a copy
problem: it builds a markdown block — the layer guide plus the actual
exports/signatures of the existing files the plan touches — that the engineer's
task prompt injects, so the model copies real names instead of guessing.

Public entry point: ``build_grounding_block(plan_content, scope)``.

Extraction is deliberately regex-based (no AST): it only needs to surface a
skeleton of declarations, and the per-file/whole-block caps keep it well within
the num_ctx budget alongside the persona and plan.
"""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[2]))  # adds .ai/pipeline/ to path

from tools.filesystem import REPO_ROOT, file_exists, list_files, read_file

# Layer guide per scope — referenced by the personas but never read in until now.
_LAYER_GUIDE = {
    "backend": "apps/api/CLAUDE.md",
    "frontend": "apps/web/CLAUDE.md",
}

# Where to resolve bare filenames (e.g. "authStore.ts") named in plan section 4.
_SRC_ROOTS = {
    "backend": ["apps/api/src/main/java"],
    "frontend": ["apps/web/src"],
}

_SCOPE_EXT = {
    "backend": (".java",),
    "frontend": (".ts", ".tsx"),
}

_MAX_FILES = 12
_MAX_SYMBOL_CHARS = 6000

# Full repo-relative paths anywhere in the plan text.
_FULL_PATH_RE = re.compile(r"apps/(?:api|web)/[\w./-]+\.(?:java|ts|tsx)")
# Bare filenames (no slashes) — resolved against the scope's src roots.
_BARE_NAME_RE = re.compile(r"\b[\w.-]+\.(?:java|ts|tsx)\b")

# ── Java symbol patterns ───────────────────────────────────────────────────────
_JAVA_TYPE_RE = re.compile(
    r"^\s*(?:public|protected)\s+(?:abstract\s+|final\s+|sealed\s+)*"
    r"(?:class|interface|enum|record)\s+\w+[^{;]*",
    re.MULTILINE,
)
# Method/constructor signatures, including bare interface methods (no modifier).
# Excludes control-flow keywords so statement lines aren't mistaken for methods.
_JAVA_METHOD_RE = re.compile(
    r"^\s*(?!(?:if|for|while|switch|catch|return|new|throw|else|do)\b)"
    r"[\w@<>,\s\[\].?]*?\b\w+\s*\([^;{]*\)\s*(?:throws[\w,\s.]*)?[;{]",
    re.MULTILINE,
)
_JAVA_ANNOT_RE = re.compile(
    r"^\s*@(?:RestController|Service|Repository|Component|Configuration|Entity|"
    r"RequestMapping|ConfigurationProperties)\b.*",
    re.MULTILINE,
)

# ── TypeScript symbol patterns ──────────────────────────────────────────────────
_TS_DECL_RE = re.compile(
    r"^\s*export\s+(?:default\s+)?(?:const|let|var|async\s+function|function|"
    r"abstract\s+class|class|interface|type|enum)\s+\w+.*",
    re.MULTILINE,
)
_TS_REEXPORT_RE = re.compile(r"^\s*export\s+(?:default\s+)?\{[^}]*\}.*", re.MULTILINE)
_TS_DEFAULT_RE = re.compile(r"^\s*export\s+default\s+\w+\s*;?.*", re.MULTILINE)


def build_grounding_block(plan_content: str, scope: str) -> str:
    """Build the ground-truth context block for an engineer, or "" if nothing resolved.

    Args:
        plan_content: Full implementation-plan markdown.
        scope: "backend" or "frontend".
    """
    guide = _read_layer_guide(scope)
    symbol_map = _build_symbol_map(plan_content, scope)
    if not guide and not symbol_map:
        return ""

    parts = ["## Project Context — Ground Truth", ""]
    if guide:
        parts += [
            f"### Layer Guide ({_LAYER_GUIDE[scope]})",
            "",
            guide,
            "",
        ]
    if symbol_map:
        parts += [
            "### Existing Symbols You May Reference",
            "",
            "These are the ACTUAL exports/signatures of existing files this plan "
            "touches. Use these exact names. If a symbol you need is not listed "
            "here, read its source with read_project_file before using it — never "
            "invent it.",
            "",
            symbol_map,
        ]
    return "\n".join(parts).rstrip() + "\n"


def _read_layer_guide(scope: str) -> str:
    path = _LAYER_GUIDE.get(scope)
    if not path:
        return ""
    try:
        return read_file(path).strip()
    except FileNotFoundError:
        return ""


def _build_symbol_map(plan_content: str, scope: str) -> str:
    """Render fenced symbol skeletons for the existing files the plan references."""
    lang = "java" if scope == "backend" else "typescript"
    blocks: list[str] = []
    total = 0
    for rel_path in _referenced_files(plan_content, scope):
        symbols = _extract_symbols(rel_path)
        if not symbols:
            continue
        block = f"#### {rel_path}\n```{lang}\n{symbols}\n```"
        if total + len(block) > _MAX_SYMBOL_CHARS:
            blocks.append("_(symbol map truncated to fit the context budget)_")
            break
        blocks.append(block)
        total += len(block)
    return "\n\n".join(blocks)


def _referenced_files(plan_content: str, scope: str) -> list[str]:
    """Repo-relative, existing, scope-matching files named in plan sections 3 & 4."""
    section_3 = _extract_section(plan_content, 3)
    section_4 = _extract_section(plan_content, 4)
    text = f"{section_3}\n{section_4}"
    exts = _SCOPE_EXT[scope]

    ordered: list[str] = []
    seen: set[str] = set()

    def _add(rel: str | None) -> None:
        if rel and rel not in seen and rel.endswith(exts):
            seen.add(rel)
            ordered.append(rel)

    # Explicit full paths first (these include the files-to-modify from section 3).
    for match in _FULL_PATH_RE.finditer(text):
        rel = match.group(0)
        if file_exists(rel):
            _add(rel)

    # Then bare dependency names from section 4, resolved by globbing the src roots.
    for match in _BARE_NAME_RE.finditer(section_4):
        name = match.group(0)
        if "/" in name or not name.endswith(exts):
            continue
        _add(_resolve_bare_name(name, scope))

    return ordered[:_MAX_FILES]


def _resolve_bare_name(name: str, scope: str) -> str | None:
    """Find a repo-relative path for a bare filename by globbing the scope's src roots."""
    for root in _SRC_ROOTS[scope]:
        matches = list_files(root, f"**/{name}")
        if matches:
            return Path(matches[0]).resolve().relative_to(REPO_ROOT).as_posix()
    return None


def _extract_section(plan: str, n: int) -> str:
    """Extract section ``## n.`` up to the next ``## <digit>.`` heading or EOF."""
    match = re.search(
        rf"(##\s*{n}\..*?)(?=\n##\s*\d+\.|\Z)",
        plan,
        re.DOTALL | re.IGNORECASE,
    )
    return match.group(1).strip() if match else ""


def _extract_symbols(rel_path: str) -> str:
    try:
        content = read_file(rel_path)
    except FileNotFoundError:
        return ""
    if rel_path.endswith(".java"):
        return _extract_java_symbols(content)
    return _extract_ts_symbols(content)


def _ordered_matches(content: str, *patterns: re.Pattern) -> str:
    """Collect matching lines in source order, de-duplicated, trimmed."""
    found: list[tuple[int, str]] = []
    seen: set[str] = set()
    for pattern in patterns:
        for m in pattern.finditer(content):
            line = m.group(0).strip().rstrip("{").strip()
            if line and line not in seen:
                seen.add(line)
                found.append((m.start(), line))
    found.sort(key=lambda pair: pair[0])
    return "\n".join(line for _, line in found)


def _extract_java_symbols(content: str) -> str:
    return _ordered_matches(content, _JAVA_ANNOT_RE, _JAVA_TYPE_RE, _JAVA_METHOD_RE)


def _extract_ts_symbols(content: str) -> str:
    return _ordered_matches(content, _TS_DECL_RE, _TS_REEXPORT_RE, _TS_DEFAULT_RE)
