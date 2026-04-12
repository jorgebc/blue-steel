---
name: query-pipeline
description: >
  Use this skill whenever you are working on the Query Mode pipeline in `apps/api` or `apps/web`.
  Triggers include: "query endpoint", "natural language query", "POST /queries", "vector search",
  "pgvector similarity", "citation", "context assembly", "QueryAnsweringPort", "QueryService",
  "embedding retrieval", or any task involving how user questions are answered from world state.
  This skill covers the full query pipeline: embed question → pgvector similarity search →
  context assembly → LLM answer → citation response.
---

# Query Pipeline — Natural Language Questions Against World State

Query Mode answers free-text questions about a campaign's world state. The pipeline transforms a
user's natural language question into a structured answer grounded in specific session evidence.
The system never invents — every claim in an answer must cite a specific session (D-003). The
query is synchronous; the client waits for a complete response (D-052).

## Context

**Pipeline stages (ARCHITECTURE.md §6.4):**

```
Query (free-text question)
  → Embed question ─────────── EmbeddingPort (OpenAI text-embedding-3-small)
  → pgvector similarity search ─ retrieve top-N relevant entity version snapshots
                                  from entity_embeddings, scoped to campaign
  → Context assembly ─────────── collect matched entity snapshots + session references
                                  enforce context token budget before LLM call
  → QueryAnsweringPort ────────── single LLM call (Anthropic ChatClient)
                                  system: world state context + citation rule
                                  user: original question
  → Response + citations ─────── answer text + citations[] mapping claims to session_ids
```

**Key decisions:**
- D-003: Every claim must be attributed to a specific `session_id` from provided context
- D-052: Synchronous execution, single LLM call, 504 on timeout
- D-058: No Q&A log in v1 — queries are stateless
- D-062: pgvector queries are native SQL — Spring AI `VectorStore` is never used
- D-063: Entity versions without embeddings are excluded from retrieval until generation completes
- D-034: Context is bounded by top-N pgvector results + token envelope

## Key Classes and Ports

| Class | Location | Responsibility |
|---|---|---|
| `QueryService` | `application/service/` | Orchestrates the full query pipeline |
| `QueryAnsweringPort` | `application/port/out/` | Driven port: LLM call with context, returns answer + citations |
| `EmbeddingPort` | `application/port/out/` | Driven port: embed a string to float[] vector |
| `EntityEmbeddingRepository` | `application/port/out/` | Driven port: native pgvector similarity search |
| `MockQueryAnsweringAdapter` | `adapters/out/ai/` | Local dev mock — returns canned answer, zero API cost |

## Workflow

### 1. Understand the retrieval unit

The retrieval unit for Query Mode is an **entity version snapshot** stored in `entity_embeddings`.
Each row corresponds to a specific version of a world state entity (actor, space, event, or relation)
after a commit. The `full_snapshot` JSONB from `actor_versions` (or equivalent) is the content
that was embedded.

Entity versions without a corresponding `entity_embeddings` row are **excluded from retrieval**
(D-063). This means very recently committed sessions may temporarily not be queryable until async
embedding generation completes (typically seconds).

```sql
-- entity_embeddings schema (ARCHITECTURE.md §5.5)
entity_embeddings
  id UUID PK
  entity_type TEXT            -- 'actor' | 'space' | 'event' | 'relation'
  entity_id UUID
  entity_version_id UUID FK → actor_versions.id (or equivalent)
  session_id UUID FK → sessions.id
  embedding vector(1536)      -- text-embedding-3-small dimensions
  content_hash TEXT           -- detect unchanged content, skip re-embedding
  created_at TIMESTAMP
```

### 2. Implement the pgvector similarity search

The similarity search query must scope results to the current campaign and filter to only entity
versions that have embeddings. This is a native SQL query — do not use Spring AI `VectorStore`
(ARCH-04, D-062).

```sql
-- Top-N entity version snapshots most similar to the query embedding
-- Scoped to campaign, filtered to entities with embeddings
SELECT
    ee.entity_type,
    ee.entity_id,
    ee.entity_version_id,
    ee.session_id,
    ee.embedding <=> :queryEmbedding::vector AS cosine_distance,
    av.full_snapshot
FROM entity_embeddings ee
JOIN actor_versions av ON av.id = ee.entity_version_id  -- adjust per entity_type
WHERE ee.session_id IN (
    SELECT id FROM sessions WHERE campaign_id = :campaignId AND status = 'committed'
)
ORDER BY cosine_distance ASC
LIMIT :topN
```

In practice, use a UNION across all entity types or a polymorphic join approach. The key
constraints are: scoped to `campaign_id`, limited to `committed` sessions, ordered by cosine
distance ascending (lower = more similar for `<=>` cosine operator), limited to top-N.

**Spring Data JPA with native query:**

```java
@Query(nativeQuery = true, value = """
    SELECT ee.entity_type, ee.entity_id, ee.session_id,
           ee.embedding <=> CAST(:queryEmbedding AS vector) AS distance,
           ev.full_snapshot
    FROM entity_embeddings ee
    JOIN ...
    WHERE ...
    ORDER BY distance ASC
    LIMIT :topN
    """)
List<EntitySnapshotProjection> findMostSimilarInCampaign(
    UUID campaignId,
    float[] queryEmbedding,
    int topN
);
```

### 3. Context assembly and token budgeting

After retrieval, assemble the context for the LLM call:

1. Collect the `full_snapshot` JSON for each retrieved entity version.
2. Collect the session references (`session_id` → `sequence_number`, session metadata) for
   citation grounding.
3. Estimate the token count of the assembled context.
4. If the context exceeds the configured token envelope, reduce `topN` and retry or truncate
   the least-relevant results. Never pass an oversized context to the LLM (D-034).
5. Construct the `QueryAnsweringPort` input: context string + original question.

### 4. Define and implement `QueryAnsweringPort`

```java
// application/port/out/QueryAnsweringPort.java
public interface QueryAnsweringPort {
    QueryResponse answer(String question, List<EntityContext> relevantContext);
}

// application/port/out/QueryResponse.java
public record QueryResponse(
    String answerText,
    List<Citation> citations  // each citation: session_id + optional claim reference
) {}
```

The real adapter (Spring AI `ChatClient`, `@Profile("llm-real")`):
- System prompt: provides the world state context in a structured format, instructs the LLM
  to attribute each factual claim to a specific `session_id` from the provided context, and
  instructs it to omit claims that cannot be grounded in context.
- User message: the original question.
- Response: parse structured JSON containing `answer_text` and `citations` array.
- Log: tokens in, tokens out, estimated cost, campaign_id, user_id, pipeline stage (LOG-01).

### 5. Handle timeout (D-052)

The query pipeline is synchronous. If it exceeds the configured timeout, return `504` with error
code `QUERY_TIMEOUT`. The Spring Boot controller can use a request timeout or the use-case service
can enforce a deadline via `CompletableFuture.get(timeout, TimeUnit.SECONDS)`.

```java
// In QueryService — enforce timeout around LLM call
try {
    CompletableFuture<QueryResponse> future = CompletableFuture.supplyAsync(
        () -> queryAnsweringPort.answer(question, context), executor);
    return future.get(configuredTimeoutSeconds, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    throw new QueryTimeoutException();
}
```

Map `QueryTimeoutException` → `504 QUERY_TIMEOUT` in `GlobalExceptionHandler`.

### 6. Citation grounding in the LLM prompt

The LLM must be instructed to produce citations. A reliable pattern is to provide context as a
numbered list of entity snapshots, each labelled with its session_id, then require the model to
reference these labels in its answer:

```
System prompt excerpt:
"Answer the user's question using only the world state context provided below.
For each factual claim in your answer, cite the session_id from which the claim is derived.
Do not state anything that cannot be traced to the provided context.

Context:
[session_id: abc-123, entity: Actor 'Aldric'] {...full snapshot...}
[session_id: def-456, entity: Event 'Battle of Thornwall'] {...full snapshot...}
...

Respond in JSON:
{ 'answer': '...', 'citations': [{ 'session_id': '...', 'claim': '...' }] }
"
```

Parse the JSON response into `QueryResponse`.

## Response Shape

```json
{
  "data": {
    "answer": "Aldric was present at the Battle of Thornwall, where he...",
    "citations": [
      { "session_id": "...", "sequence_number": 4, "claim": "Aldric was at Thornwall" },
      { "session_id": "...", "sequence_number": 7, "claim": "Aldric learned of the Conclave" }
    ]
  }
}
```

The frontend must render citations as navigable links to the referenced session.

## No Q&A History in v1

Queries are stateless in v1 (D-058). The `POST /api/v1/campaigns/{id}/queries` endpoint does not
persist the question or answer. Do not implement a query history table, log, or history panel
until v2. The response is ephemeral — if the user navigates away, the answer is gone.

## Running Locally with Mock Adapters

With the `local` Spring profile, `MockQueryAnsweringAdapter` returns a canned answer without
calling any external API. This is sufficient for testing the frontend Query Mode flow.

The mock should return an answer that includes at least one citation to a session in the
current campaign, so the citation rendering can be tested end-to-end.

## Common Pitfalls

- **Using Spring AI `VectorStore` for the similarity search.** `VectorStore` cannot express the
  domain-specific query shapes required (campaign scoping, entity_type filtering, join through
  entity_versions). Use native SQL (ARCH-04, D-062).

- **Asserting query results synchronously after a commit in tests.** Embedding generation is async
  (D-063). Entity versions without embeddings are excluded from retrieval. Mock `EmbeddingPort`
  or add a deliberate test wait if testing the full commit-then-query flow.

- **Allowing the LLM to synthesize beyond provided context.** The system prompt must explicitly
  instruct the model to omit claims it cannot ground in context. Test this with prompts asking
  about entities not in the retrieved context — the answer should acknowledge the gap, not invent.

- **Passing unbounded world state to the LLM.** Context assembly must enforce the token envelope.
  Never pass all entity versions for a campaign — only the top-N most similar.

- **Returning the citations array as empty when citations exist.** If the LLM response has
  citations but parsing fails, do not silently return an empty `citations` array. Log the parsing
  failure at ERROR and return a structured error to the client.

- **Not handling `504` in the frontend.** The client must surface `504 QUERY_TIMEOUT` as a
  user-friendly message with a suggestion to rephrase or narrow the question (D-052).

## References

- `ARCHITECTURE.md` §6.4 (query pipeline), §6.5 (cost governance), §5.5 (vector layer schema)
- `DECISIONS.md` D-003, D-052, D-058, D-062, D-063
- `apps/api/CLAUDE.md` §10 (async embedding gotcha)
- `session-ingestion-pipeline` skill (for how embeddings are generated post-commit)
- `database-migration` skill (for `entity_embeddings` schema)
