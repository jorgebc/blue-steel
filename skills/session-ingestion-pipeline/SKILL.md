---
name: session-ingestion-pipeline
description: >
  Use this skill whenever you are working on any part of the session ingestion flow in `apps/api`:
  summary intake, knowledge extraction, entity resolution, conflict detection, diff generation,
  draft state management, or the commit endpoint. Triggers include: "session ingestion", "extraction
  pipeline", "entity resolution", "UNCERTAIN entity", "conflict detection", "diff generation",
  "commit endpoint", "processing pipeline", "mock LLM adapter", or any reference to
  `SessionIngestionService`, `CommitService`, `NarrativeExtractionPort`, `EntityResolutionPort`,
  or `ConflictDetectionPort`. This is the most complex backend workflow — read this before
  touching any part of it.
---

# Backend — Session Ingestion Pipeline

The session ingestion pipeline is the core of Blue Steel's value. It transforms raw narrative text
into structured world state through five stages, enforces strict business rules at the commit
boundary, and controls LLM costs through bounded context and provider-level caps. Every design
decision in this pipeline has a documented rationale.

## Context

**Pipeline stages (ARCHITECTURE.md §6.3):**

```
Session Summary (raw text)
  → Token budget check ─────── REJECT if oversized (400 SUMMARY_TOO_LARGE)
  → Knowledge Extraction ────── LLM call 1 (Anthropic ChatClient)
      generates: actors, spaces, events, relations as raw mentions
      co-generates: narrative summary header (D-005)
  → Entity Resolution ──────── two-stage
      Stage 1: pgvector similarity search per mention
               score below floor → NEW immediately (no LLM call)
               score above floor → forward to Stage 2
      Stage 2: LLM call 2 (bounded — only high-score candidates)
               → MATCH | NEW | UNCERTAIN
  → Conflict Detection ─────── pgvector retrieval → LLM call 3 (bounded)
  → Diff Generation ─────────── structured diff payload stored in sessions.diff_payload
  → User Review (frontend)
  → Commit ──────────────────── world state written; HTTP 200 returned immediately
  → (async) Embedding generation (OpenAI EmbeddingModel, post-commit fire-and-forget)
```

**Maximum 3 LLM calls per session ingestion.** Call 2 is conditional on similarity scores.
Sessions where all mentions score below the floor require only 2 calls (D-034).

**Key decisions:**
- D-002: No auto-commit — user confirmation required before world state mutation
- D-033: Conflict detection is non-blocking (warning cards, not blockers)
- D-034: LLM cost governance — bounded pipeline + provider spend cap
- D-041: Entity resolution is two-stage (pgvector + LLM)
- D-042: UNCERTAIN entities require explicit user resolution; commit rejected with 422 if present
- D-049: Local dev uses mock adapters (zero API cost); real LLM via `llm-real` profile
- D-054: Single active draft per campaign; new submission rejected 409 if draft/processing exists
- D-063: Embedding generation is async post-commit; commit returns 200 immediately

## Key Classes

| Class | Location | Responsibility |
|---|---|---|
| `SessionIngestionService` | `application/service/` | Orchestrates stages 1–5 (intake through diff generation) |
| `CommitService` | `application/service/` | Validates commit payload, writes world state, fires embedding event |
| `NarrativeExtractionPort` | `application/port/out/` | Driven port: extract actors/spaces/events/relations from text |
| `EntityResolutionPort` | `application/port/out/` | Driven port: resolve extracted mentions to existing/new/uncertain |
| `ConflictDetectionPort` | `application/port/out/` | Driven port: detect hard contradictions vs. existing world state |
| `EmbeddingPort` | `application/port/out/` | Driven port: generate float[] vector for a content string |
| `MockNarrativeExtractionAdapter` | `adapters/out/ai/` | Local dev mock — canned response, zero API cost |

## Workflow: Adding/Modifying a Pipeline Stage

### 1. Work at the port level first

All pipeline stages are behind driven port interfaces. When modifying a stage, start by
updating the port interface signature (if needed) in `application/port/out/`. The application
service depends only on ports — never on adapter implementations.

### 2. Update the use-case service orchestration

`SessionIngestionService` is the single orchestrator. It calls ports in order, handles stage
failures, and builds the diff payload. Modifications to the pipeline logic go here.

The service must handle these failure modes:
- Token budget exceeded at intake → reject with `400 SUMMARY_TOO_LARGE` before any LLM call
- LLM extraction failure → transition session to `FAILED`, populate `failure_reason`
- Partial extraction (some entities resolved, others not) → surface `UNCERTAIN` cards, proceed

### 3. Update or create the mock adapter

Every LLM port has a mock adapter in `adapters/out/ai/` activated on the `local` Spring profile
(D-049). Mock adapters return deterministic, canned responses — they do not call any external API.

```java
// adapters/out/ai/MockNarrativeExtractionAdapter.java
@Component
@Profile("local")
public class MockNarrativeExtractionAdapter implements NarrativeExtractionPort {
    @Override
    public ExtractionResult extract(NarrativeBlock block) {
        // Return a fixed extraction with known actors/events for predictable local testing
        return ExtractionResult.builder()
            .narrativeSummary("Mock: session introduced two new actors.")
            .actors(List.of(MockData.actorMention("Aldric"), MockData.actorMention("Seraphine")))
            .events(List.of(MockData.eventMention("Conclave meeting")))
            .build();
    }
}
```

### 4. Update the real AI adapter

The real adapter in `adapters/out/ai/` implements the same port using Spring AI `ChatClient`.

- Use `@Profile("llm-real")` to activate the real adapter (D-049).
- Log every LLM call at INFO: tokens in, tokens out, estimated cost, session_id, user_id,
  pipeline stage (LOG-01).
- Estimate token count before every call; reject if it exceeds the configured envelope (D-034).
- Prompt engineering for extraction: include schema hints for structured output. Spring AI can
  return structured Java objects — use this to get typed `ExtractionResult` responses.
- Do not log raw LLM response content at INFO — it may contain sensitive narrative data (LOG-01).

### 5. Entity resolution: understand the two-stage logic

Stage 1 (pgvector, no LLM):
- Embed each extracted mention name/description using `EmbeddingPort`.
- Query `entity_embeddings` for the top-N most similar existing entities within the same campaign.
- If max cosine similarity < configured floor → classify as `NEW`, skip Stage 2 for this mention.
- If max cosine similarity ≥ floor → forward mention + top candidates to Stage 2.

Stage 2 (LLM, bounded):
- Pass each high-score mention + its candidate entities to `EntityResolutionPort`.
- Possible outcomes: `MATCH` (attach to existing entity), `NEW` (create new entity),
  `UNCERTAIN` (surface for user resolution).

```java
// application/port/out/EntityResolutionPort.java
public interface EntityResolutionPort {
    List<ResolvedEntity> resolve(
        List<ExtractedMention> mentions,       // only high-score candidates
        List<EntityContext> candidateContext   // existing entities for comparison
    );
    // ResolvedEntity.outcome: MATCH | NEW | UNCERTAIN
}
```

### 6. UNCERTAIN entities: mandatory resolution before commit

UNCERTAIN entities surface as dedicated diff cards requiring the user to choose:
- `match` — the mention is the same entity as the candidate
- `new` — the mention is a genuinely different entity

This choice is included in the commit payload as `resolved_entities`. The commit endpoint
validates that all `UNCERTAIN` entities from the diff have been resolved. If any remain:
`422 UNCERTAIN_ENTITIES_PRESENT` (D-042).

`CommitService` must:
1. Check `resolved_entities` in the payload covers all `UNCERTAIN` entities from the stored diff.
2. Reject with `422` if any remain — this is a defence-in-depth check, not reliance on the UI.

### 7. Conflict detection: non-blocking warning cards

`ConflictDetectionPort` receives the extraction result and relevant world state context
(retrieved via pgvector similarity search). It returns a list of `ConflictWarning` objects for
hard contradictions (e.g., an event asserts a character is dead who is currently alive).

These become warning cards in the diff — non-blocking (D-033). The commit payload must include
`acknowledged_conflicts` with entries for each detected conflict. An empty array is valid only
when no conflicts were detected. The backend rejects with `422 CONFLICTS_NOT_ACKNOWLEDGED` if
conflicts were detected and the array is absent or empty.

### 8. Commit: world state write + async embedding

`CommitService.commit()` performs these steps in order:

1. Validate commit payload (UNCERTAIN check, conflict acknowledgment check).
2. Apply each accepted/edited entity to world state — append new version rows.
3. Transition session status to `COMMITTED`, clear `diff_payload`.
4. Return HTTP 200 immediately.
5. Publish `SessionCommittedEvent` via `ApplicationEventPublisher`.
6. An `@EventListener @Async` handler in `adapters/out/ai/` picks up the event and generates
   embeddings for each committed entity version via `EmbeddingPort` (D-063).

**Do not await embedding completion in the commit endpoint.** It is fire-and-forget.

## Commit Payload Structure

```json
{
  "actors":    [{ "id": "...", "action": "accept|edit|delete", "data": { } }],
  "spaces":    [{ "id": "...", "action": "accept|edit|delete", "data": { } }],
  "events":    [{ "id": "...", "action": "accept|edit|delete", "data": { } }],
  "relations": [{ "id": "...", "action": "accept|edit|delete", "data": { } }],
  "resolved_entities": [
    { "mention_id": "...", "resolution": "match|new", "matched_entity_id": "..." }
  ],
  "acknowledged_conflicts": [
    { "conflict_id": "...", "accepted": true }
  ]
}
```

Note: `add` action is deferred to v2 (D-053). Any `add` action in a v1 commit payload
should return `422 UNSUPPORTED_ACTION`.

## Draft Session Policy (D-054)

At most one session per campaign may be in `processing` or `draft` state simultaneously.

- `POST /api/v1/campaigns/{id}/sessions` returns `409` if another session is already in
  `processing` or `draft`.
- The GM may explicitly discard a draft via `DELETE /sessions/{id}` (sets `status = DISCARDED`,
  clears `diff_payload`).
- A discarded session cannot be reactivated — terminal status.
- Tests for the ingestion flow must start from a clean session state per test campaign.

## Running Locally with Mock Adapters

```bash
# Start infrastructure
docker compose up -d

# Start backend with mock LLM adapters (default — zero API cost)
# Profile: local
# [Command TBD before Phase 1]
```

With mock adapters active (`local` profile), submitting any session summary returns a fixed
extraction result. This is sufficient for testing the full ingestion flow, diff review, and
commit without incurring Anthropic API costs.

To test with real LLMs:
```bash
# Profile: local,llm-real (requires ANTHROPIC_API_KEY and OPENAI_API_KEY in .env.local)
# [Command TBD before Phase 1]
```

## Common Pitfalls

- **Passing unbounded world state to the LLM.** Every LLM call must bound its context. The
  entity resolution Stage 2 only receives high-score candidates — not all world state entities.
  The conflict detection port only receives pgvector-retrieved relevant context.

- **Committing without validating the payload.** `CommitService` performs independent server-side
  validation of UNCERTAIN entities and conflict acknowledgments. The frontend's UI guards are
  primary; the backend's `422` responses are defence in depth. Both must exist.

- **Awaiting embedding generation before returning from commit.** The commit endpoint returns
  HTTP 200 before embeddings are generated. `@Async` embedding is by design (D-063).

- **Activating the real LLM adapter in tests.** Integration tests must mock `EmbeddingPort`
  and the extraction/resolution/conflict ports. Real API calls in tests incur cost and introduce
  flakiness.

- **Forgetting the single-draft constraint in test setup (D-054).** Each test campaign must be
  created fresh (or its sessions cleaned up) before testing the ingestion flow. A shared campaign
  with a lingering draft will cause `409` failures.

- **Using Spring AI `VectorStore` for entity resolution Stage 1.** All pgvector queries are native
  SQL — `VectorStore` is never used in this codebase (ARCH-04, D-062).

## References

- `apps/api/CLAUDE.md` §6 (domain concept definitions), §9 (pipeline workflows), §10 (gotchas)
- `ARCHITECTURE.md` §6 (full LLM integration architecture), §6.3 (extraction pipeline)
- `DECISIONS.md` D-002, D-033, D-034, D-041, D-042, D-049, D-054, D-063
- `backend-endpoint` skill (for adding the commit endpoint controller)
- `database-migration` skill (for entity_embeddings schema)
- `query-pipeline` skill (for how embeddings are consumed post-commit)
