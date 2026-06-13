# Roadmap — Blue Steel

> Index and tracking conventions for all roadmap files. One file per product version; this README is
> the single source for the status legend and the rules both humans and the `.ai/` pipeline follow.

---

## Roadmap files

| Version | File | Phases | Status |
|---|---|---|---|
| v1 | [`ROADMAP_V1.md`](ROADMAP_V1.md) | 0–4 (Input, Query, Exploration) | ✅ Complete — archived |
| v2 | [`ROADMAP_V2.md`](ROADMAP_V2.md) | 5–9 (Proposals, Input/Query enhancements, Export, User Settings & i18n, Per-campaign language) | 🔲 Decomposed to feature level — not started |

## Active roadmap

**`docs/roadmap/ROADMAP_V2.md`** — all new work is tracked there.

The `.ai/` pipeline hardcodes the active roadmap path. When rolling over to a new version file,
update the path in:

- `.ai/pipeline/orchestrator.py` (`ROADMAP_PATH` constant — read + ✅-marking write)
- `.ai/pipeline/agents/planning/planning_crew.py` (`_DOCS["roadmap"]`)
- `.ai/pipeline/agents/planning/test_planning.py`
- `.ai/pipeline/prompts/product_owner.md`, `.ai/implement-task-prompt.md`, `.ai/roadmap-decomposition-prompt.md`, `.ai/AGENTS.md` (prompt/doc references)

## Status legend

| Symbol | Meaning |
|---|---|
| 🔲 | Not started |
| 🔄 | In progress |
| ✅ | Done |
| ⛔ | Blocked |
| 👤 | Human step (manual task) |

## Tracking conventions

1. **Phase numbers continue across versions.** Phases 0–4 belong to v1, Phases 5+ to v2 (and so on).
   A phase number is never reused, so task IDs are globally unique across all roadmap files.
2. **Task IDs are stable.** Format `F<phase>.<n>` with optional sub-tasks `F<phase>.<n>.<m>`
   (e.g., `F5.2.3`). Once assigned, an ID never changes or moves — DECISIONS.md, commits, and the
   pipeline reference tasks by ID.
3. **Big picture → decomposition → implementation.** A new version's roadmap starts at phase/epic
   level (Purpose / Key decisions / Major capabilities / Dependencies & risks). Before
   implementation, each phase is decomposed into F-task entries (Goal / Scope in / Scope out /
   Dependencies / Skills / Decisions + phase summary table) using
   `.ai/roadmap-decomposition-prompt.md`.
4. **Marking done.** A task is flipped to ✅ in its phase summary table only after a validated
   `APPROVED` final review (see `.ai/AGENTS.md`); the pipeline does this automatically via
   `_mark_task_done_in_roadmap`. Unresolved work stays 🔄/⛔ with the status recorded.
5. **Version bumps on phase milestones.** Completing a roadmap phase milestone triggers the
   repo-wide SemVer bump per D-090 (`apps/web/package.json` + `apps/api/pom.xml` together, annotated
   tag on `main` after merge). Per-task work never bumps the version.
6. **Roadmap edits are append-only in spirit.** Completed phases/files are historical records — fix
   typos, but never rewrite scope or status retroactively. New scope goes into the active roadmap
   (or a new version file) with new decision entries in `docs/DECISIONS.md`.
