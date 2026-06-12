# ROADMAP V2 — Blue Steel

> **Status: big picture — not yet decomposed.** This file sequences v2 at phase/epic level only.
> Before any implementation starts, each phase must be decomposed into `F<phase>.<n>` task entries
> (Goal / Scope in / Scope out / Dependencies / Skills / Decisions) via
> `.ai/roadmap-decomposition-prompt.md`, following the same conventions as
> [`ROADMAP_V1.md`](ROADMAP_V1.md). Tracking conventions and the status legend live in
> [`README.md`](README.md).
>
> **Phase numbering continues from v1** (Phases 0–4 = v1), so task IDs stay globally unique across
> roadmap files: v2 work is `F5.x`, `F6.x`, `F7.x`. Build sequence within v2 is **TBD** — to be
> recorded as a DECISIONS.md entry when v2 is sequenced.

---

## Phases

#### Summary

| # | Phase | Status |
|---|---|---|
| 5 | Proposal & Approval Pipeline | 🔲 |
| 6 | Input & Query Enhancements | 🔲 |
| 7 | Campaign Export | 🔲 |

---

### Phase 5 — Proposal & Approval Pipeline

**Purpose:** Give players a structured way to correct or extend world state without write access:
propose a change → players co-sign → GM approves or vetoes → approved deltas become new entity
versions. This activates the workflow that v1 deliberately shipped as data model only.

**Key decisions (already recorded):**
- D-012 — "Propose a change" affordance is present (disabled) on every entity/space/relation in v1; pipeline activates in v2
- D-016 — proposal/approval system designed into the data model in v1, ships in v2
- D-017 — approval rule: at least one player co-sign, then the GM decides
- D-018 — GM veto is unilateral
- D-019 — abandoned proposals expire (TTL semantics to be defined in v2)

**Major capabilities:**
- Player proposal submission UI + API over the existing `proposals` / `proposal_votes` schema (migrations `0018`/`0019`, already in place)
- Co-sign flow (D-017)
- GM approval / veto (D-018)
- Proposal expiry TTL enforcement (D-019)
- Applying an approved proposal delta as a new entity version (reusing the v1 two-table versioning write path)
- Conflict resolution between concurrent proposals targeting the same entity (PRD §7 "Out of scope (v1) → v2")

**Dependencies & risks:**
- Enabling the `ProposeChangeButton` stub (`apps/web/src/components/domain/ProposeChangeButton.tsx`) across all exploration profiles
- TTL semantics (D-019) and concurrent-proposal rules are undecided — need DECISIONS entries before decomposition
- Approval writes must respect world-state invariants (append-only history, session traceability)

---

### Phase 6 — Input & Query Enhancements

**Purpose:** Close the two workflow gaps v1 consciously deferred: manually adding entities the AI
missed during diff review, and persisting Query Mode history so the table can revisit past answers.

**Key decisions (already recorded):**
- D-053 — commit payload `add` action (manually introduce missed entities) deferred to v2; v1 rejects it with 422 `UNSUPPORTED_ACTION`
- D-058 — Q&A log deferred to v2; v1 queries are stateless
- D-052 — query execution is synchronous; streaming/SSE only if the latency target cannot be met

**Major capabilities:**
- Commit-payload `add` action: diff-review affordance to introduce a missed entity, validation in `CommitPayloadValidator`, persistence through the existing commit write path (D-053)
- Q&A log: persist questions + answers + citations per campaign, with a browsable history panel inside Query Mode (D-058)
- Query streaming / SSE — **contingent**: only if the synchronous model cannot meet the < 5s latency target (D-052)

**Dependencies & risks:**
- Q&A log requires new storage (schema migration) and a retention/cost decision (answers embed LLM output)
- `add` action touches the commit validation contract (DiffPayload/CommitPayload, D-076) — backend records and frontend types must stay mirrored
- Streaming would change the API contract (D-052 lists SSE as the revisit path) — evaluate latency data from v1 production first

---

### Phase 7 — Campaign Export

**Purpose:** Let a campaign's data outlive the platform: download the full campaign (actors, events,
spaces, relations, annotations, sessions) in an interactive/portable format — primarily so nothing
is lost before an admin deletes a campaign.

**Key decisions (already recorded):** none yet — format, scope, and authorization need DECISIONS
entries before decomposition.

**Major capabilities:**
- Export endpoint assembling the complete campaign dataset (world state + version history + narrative blocks + annotations)
- Portable/interactive output format (to be decided — e.g., structured JSON archive vs. static HTML bundle)
- Pre-deletion guard rail: surface export from the campaign danger zone before `DELETE /campaigns/{id}`

**Dependencies & risks:**
- Scope tension to resolve: PRD §7 lists "public sharing or export" as *post-v2*, while the v1 roadmap's v2 stub included this narrower pre-deletion export — confirm scope against the PRD (and update one of them) when sequencing v2
- Export volume vs. free-tier hosting limits (Render/Neon) — may need streaming/zip generation

---

## Versioning

Per D-090, `1.0.0` = v1 complete (Input + Query + Exploration). v2 phase milestones map to post-1.0
SemVer minors in completion order; the exact phase→version mapping will be recorded (DECISIONS
entry) when the v2 build sequence is decided.
