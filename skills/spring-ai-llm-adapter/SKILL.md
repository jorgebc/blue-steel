---
name: spring-ai-llm-adapter
description: >
  Use this skill whenever you are implementing or modifying a Spring AI adapter in `apps/api`:
  `NarrativeExtractionPort`, `EntityResolutionPort`, `ConflictDetectionPort`,
  `QueryAnsweringPort`, or `EmbeddingPort`. Triggers include: "Spring AI", "ChatClient",
  "EmbeddingModel", "LLM adapter", "Anthropic", "OpenAI embedding", "mock adapter",
  "llm-real profile", "AiConfig", "structured output", "prompt engineering", or any task
  touching `adapters/out/ai/`. This skill covers the project's non-standard Spring AI usage:
  ChatClient + EmbeddingModel only; VectorStore never used; mock vs real profile wiring;
  cost logging; structured output; token budgeting.
---

# Spring AI — LLM Adapter Implementation

Blue Steel uses Spring AI for two concerns only: `ChatClient` (text generation via Anthropic) and
`EmbeddingModel` (vector generation via OpenAI). Spring AI's `VectorStore` is **never used**
(ARCH-04, D-062) — all pgvector queries are native SQL. Both real and mock adapters implement the
same driven port interfaces.

## Context

**Key decisions:**
- D-049: Local `spring.profiles = local` activates mock adapters (zero cost). `llm-real` profile activates real adapters.
- D-034: Every LLM call must bound its context. Token budget check before every call.
- D-062: Spring AI `VectorStore` not used — native pgvector SQL only.
- ARCH-04: `VectorStore` cannot express domain-specific retrieval (campaign scoping, entity_type filtering, version joins).
- LOG-01: Every LLM call logged at INFO with `tokens_in`, `tokens_out`, `cost_usd`, `session_id`, `user_id`, `stage`.

**Ports defined in `application/port/out/`:**
- `NarrativeExtractionPort` — extract mentions from raw text
- `EntityResolutionPort` — resolve mentions to MATCH / NEW / UNCERTAIN
- `ConflictDetectionPort` — detect contradictions vs world state
- `QueryAnsweringPort` — answer question with grounded citations
- `EmbeddingPort` — embed a string to `float[]`

---

## Profile-Based Wiring (D-049)

Mock and real adapters both implement the same port interface. `@Profile` annotations control
which bean is active. No domain or application code changes when switching.

```java
// adapters/out/ai/AiConfig.java
@Configuration
public class AiConfig {

    // EmbeddingModel bean — Spring AI auto-configures via spring.ai.openai.api-key property
    // ChatClient bean — Spring AI auto-configures via spring.ai.anthropic.api-key property

    // Real adapters are registered as @Component @Profile("llm-real")
    // Mock adapters are registered as @Component @Profile("local")
}
```

```java
// Real adapter (activated by llm-real profile)
@Component
@Profile("llm-real")
public class AnthropicNarrativeExtractionAdapter implements NarrativeExtractionPort {
    private final ChatClient chatClient;
    // ...
}

// Mock adapter (activated by local profile — default dev + all CI tests)
@Component
@Profile("local")
public class MockNarrativeExtractionAdapter implements NarrativeExtractionPort {
    @Override
    public ExtractionResult extract(NarrativeBlock block) {
        // Return deterministic canned response — no external API call
        return ExtractionResult.builder()
            .narrativeSummary("Mock: session introduced two actors.")
            .actors(List.of(MockData.actorMention("Aldric"), MockData.actorMention("Seraphine")))
            .events(List.of(MockData.eventMention("Conclave meeting")))
            .build();
    }
}
```

**Integration tests always use mock adapters.** Never activate `llm-real` in CI. The test Spring
context uses `@ActiveProfiles("local")` via `BaseIntegrationTest`.

---

## ChatClient — Text Generation Pattern

Use Spring AI's `ChatClient` for all text generation. Request structured Java objects directly
using `.entity(ClassName.class)` — Spring AI handles the JSON schema instruction and parsing.

```java
// adapters/out/ai/AnthropicNarrativeExtractionAdapter.java
@Component
@Profile("llm-real")
public class AnthropicNarrativeExtractionAdapter implements NarrativeExtractionPort {

    private final ChatClient chatClient;
    private final LlmCostLogger costLogger;
    private final int maxContextTokens;

    @Override
    public ExtractionResult extract(NarrativeBlock block) {
        // Token budget check — reject before calling if oversized (D-034)
        int estimatedTokens = TokenEstimator.estimate(block.rawSummaryText());
        if (estimatedTokens > maxContextTokens) {
            throw new TokenBudgetExceededException(estimatedTokens, maxContextTokens);
        }

        Instant start = Instant.now();

        ExtractionResult result = chatClient.prompt()
            .system("""
                You are a knowledge extraction assistant for tabletop RPG session summaries.
                Extract all actors, spaces, events, and relations from the provided text.
                Also generate a 1-3 sentence narrative summary header.
                Return structured JSON matching the schema provided.
                """)
            .user(block.rawSummaryText())
            .call()
            .entity(ExtractionResult.class);  // Spring AI maps to Java Record

        // LOG-01: log tokens, cost, session, stage
        costLogger.logLlmCall(
            "extraction",
            block.sessionId(),
            block.ownerId(),
            estimatedTokens,
            TokenEstimator.estimateResponse(result),
            start
        );

        return result;
    }
}
```

**Never log raw LLM response content at INFO** (LOG-01) — it may contain sensitive narrative data
(character backstories, player secrets). Log only tokens, cost, and stage.

---

## EmbeddingModel — Vector Generation Pattern

```java
// adapters/out/ai/OpenAiEmbeddingAdapter.java
@Component
@Profile("llm-real")
public class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String content) {
        // EmbeddingModel.embed() returns EmbeddingResponse
        return embeddingModel.embed(content);
        // text-embedding-3-small: 1536 dimensions (D-040)
        // Cost: ~$0.02 per 1M tokens — negligible per entity version
    }
}

// Mock embedding adapter — returns zero vector (local profile)
@Component
@Profile("local")
public class MockEmbeddingAdapter implements EmbeddingPort {
    @Override
    public float[] embed(String content) {
        return new float[1536];  // zero vector — sufficient for local dev
    }
}
```

**Embedding generation is async post-commit** (D-063). The `EmbeddingPort` is called inside
a `@Async` `@EventListener` handler, not in the commit endpoint itself.

---

## Structured Output with Spring AI

Spring AI can deserialize LLM responses into Java Records when using `.entity()`. Define the
target Record as a plain Java class (no Jackson annotations needed for simple cases):

```java
// application/model/ExtractionResult.java (Record in application layer)
public record ExtractionResult(
    String narrativeSummary,
    List<ExtractedMention> actors,
    List<ExtractedMention> spaces,
    List<ExtractedMention> events,
    List<ExtractedMention> relations
) {}

public record ExtractedMention(
    String name,
    String description,
    String rawText  // original mention text from summary — used for UNCERTAIN cards
) {}
```

Spring AI generates a JSON schema from the Record and includes it in the system prompt. If
the LLM response does not parse cleanly, `chatClient.call().entity()` throws — the adapter must
catch this and transition the session to `failed` with `failure_reason = 'EXTRACTION_FAILED'`.

---

## Token Budget Enforcement (D-034)

Before every LLM call, estimate the token count and reject if it exceeds the configured envelope:

```java
// Cross-cutting utility — not LLM provider specific
public class TokenEstimator {
    // Conservative approximation: 1 token ≈ 4 characters for English text
    public static int estimate(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }
}
```

Configure envelopes in `application.yml`:
```yaml
blue-steel:
  llm:
    extraction-max-tokens: 4000     # input token budget for extraction
    resolution-max-tokens: 2000     # per entity resolution call
    conflict-max-tokens: 3000       # conflict detection call
    query-max-context-tokens: 6000  # query context assembly budget
```

---

## Cost Logging (LOG-01)

Every LLM call must be logged via a shared `LlmCostLogger` component. This is a cross-cutting
concern — wire it into `ApplicationConfig.java`.

```java
// config/LlmCostLogger.java
@Component
public class LlmCostLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmCostLogger.class);

    public void logLlmCall(String stage, UUID sessionId, UUID userId,
                           int tokensIn, int tokensOut, Instant startTime) {
        // MDC carries session_id and user_id automatically; add structured fields
        log.atInfo()
            .addKeyValue("stage", stage)
            .addKeyValue("tokens_in", tokensIn)
            .addKeyValue("tokens_out", tokensOut)
            .addKeyValue("cost_usd", estimateCostUsd(stage, tokensIn, tokensOut))
            .addKeyValue("duration_ms", Duration.between(startTime, Instant.now()).toMillis())
            .log("LLM call completed: {}", stage);
    }

    private double estimateCostUsd(String stage, int in, int out) {
        // Claude pricing: ~$3/MTok input, ~$15/MTok output (update when pricing changes)
        return (in * 3.0 + out * 15.0) / 1_000_000.0;
    }
}
```

---

## Common Mistakes to Avoid

- **Using `VectorStore`.** Blue Steel never uses Spring AI `VectorStore`. All pgvector similarity
  queries are native SQL (D-062). If you see `VectorStore` in an import, delete it.

- **Activating `llm-real` in integration tests.** Real API calls in CI incur cost and flakiness.
  Integration tests use `@ActiveProfiles("local")` via `BaseIntegrationTest` — mock adapters always.

- **Not checking token budget before LLM call.** `TokenBudgetExceededException` must be thrown
  before any `chatClient.prompt()` call if the estimated input exceeds the configured envelope.

- **Logging raw LLM response content at INFO.** Session summaries may contain sensitive narrative.
  Log only tokens, cost, stage — never the text (LOG-01).

- **Forgetting `failure_reason` on LLM errors.** If a ChatClient call throws, catch it in
  `SessionIngestionService`, transition the session to `FAILED`, set `failure_reason = 'EXTRACTION_FAILED'`
  or `'INTERNAL_ERROR'`, and log at ERROR with full context.

- **Calling `EmbeddingModel` inside the commit transaction.** Embedding generation is `@Async`,
  triggered by `SessionCommittedEvent` after the commit returns `200`. Never await it (D-063).

## References

- `ARCHITECTURE.md` §6.1–6.2 (Spring AI scope, port definitions)
- `ARCHITECTURE.md` §6.3–6.4 (extraction and query pipelines)
- `apps/api/CLAUDE.md` §7 LOG-01, ARCH-04
- `DECISIONS.md` D-034, D-049, D-062, D-063
- `session-ingestion-pipeline` skill (pipeline orchestration)
- `query-pipeline` skill (query pipeline orchestration)
