package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.ConflictDetectionPort;
import com.bluesteel.config.LlmCostLogger;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Real conflict-detection adapter using Spring AI {@link ChatClient} (LLM call 3, D-033). Active on
 * {@code llm-real} or {@code llm-ollama} profiles. Enforces a pre-call token budget (D-034) and
 * logs every call via {@link LlmCostLogger} (LOG-01). Warnings are non-blocking — the caller
 * surfaces them as review cards without blocking commit.
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiConflictDetectionAdapter implements ConflictDetectionPort {

  static final String SYSTEM_PROMPT =
      """
      You are a continuity assistant for tabletop RPG world state management.
      Given an extracted session summary and relevant existing world-state context,
      identify hard contradictions where the new session asserts facts that conflict
      with established world state (for example: a character described as dead now acts as alive,
      a destroyed location is revisited, a betrayal contradicts a stated alliance).
      Rules:
      - Only report genuine, clear contradictions — not ambiguity or minor inconsistencies.
      - Return an empty conflicts array if no hard contradictions are found.
      - Each conflict must name the entity and describe the contradiction precisely.
      Return a single valid JSON object with a "conflicts" array. Each element has
      "entityName" (string) and "description" (string). No extra text outside the JSON.
      """;

  /** Wrapper record for structured-output deserialization of the conflict list (D-033). */
  record ConflictDetectionResponse(List<ConflictWarning> conflicts) {}

  private final ChatClient chatClient;
  private final LlmCostLogger costLogger;
  private final int maxContextTokens;

  public SpringAiConflictDetectionAdapter(
      ChatClient chatClient,
      LlmCostLogger costLogger,
      @Value("${blue-steel.llm.conflict-max-tokens}") int maxContextTokens) {
    this.chatClient = chatClient;
    this.costLogger = costLogger;
    this.maxContextTokens = maxContextTokens;
  }

  @Override
  public List<ConflictWarning> detect(
      ExtractionResult extraction, List<EntityContext> relevantContext) {
    String userPrompt = buildUserPrompt(extraction, relevantContext);
    int estimated = TokenEstimator.estimate(userPrompt);
    if (estimated > maxContextTokens) {
      throw new TokenBudgetExceededException(estimated, maxContextTokens);
    }

    Instant start = Instant.now();

    CallResponseSpec callSpec = chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).call();

    ResponseEntity<ChatResponse, ConflictDetectionResponse> responseEntity =
        callSpec.responseEntity(ConflictDetectionResponse.class);

    ChatResponse chatResponse = responseEntity.getResponse();
    ConflictDetectionResponse response = responseEntity.getEntity();

    int tokensIn = estimated;
    int tokensOut = 0;
    if (chatResponse != null) {
      Usage usage = chatResponse.getMetadata().getUsage();
      Integer promptTokens = usage.getPromptTokens();
      Integer completionTokens = usage.getCompletionTokens();
      if (promptTokens != null) {
        tokensIn = promptTokens;
      }
      if (completionTokens != null) {
        tokensOut = completionTokens;
      }
    }

    costLogger.logLlmCall("conflict_detection", tokensIn, tokensOut, start);
    return (response != null && response.conflicts() != null) ? response.conflicts() : List.of();
  }

  private static String buildUserPrompt(
      ExtractionResult extraction, List<EntityContext> relevantContext) {
    StringBuilder sb = new StringBuilder();
    sb.append("Session narrative summary:\n")
        .append(extraction.narrativeSummaryHeader())
        .append("\n\n");

    sb.append("Extracted entities:\n");
    appendMentions(
        sb,
        "Actors",
        extraction.actors().stream().map(m -> m.name() + ": " + m.description()).toList());
    appendMentions(
        sb,
        "Spaces",
        extraction.spaces().stream().map(m -> m.name() + ": " + m.description()).toList());
    appendMentions(
        sb,
        "Events",
        extraction.events().stream().map(m -> m.name() + ": " + m.description()).toList());
    appendMentions(
        sb,
        "Relations",
        extraction.relations().stream().map(m -> m.name() + ": " + m.description()).toList());

    if (!relevantContext.isEmpty()) {
      sb.append("\nExisting world-state context:\n");
      for (EntityContext ctx : relevantContext) {
        sb.append("- ")
            .append(ctx.entityType())
            .append(" \"")
            .append(ctx.name())
            .append("\": ")
            .append(ctx.stateSnapshot())
            .append("\n");
      }
    }
    return sb.toString();
  }

  private static void appendMentions(StringBuilder sb, String label, List<String> items) {
    if (!items.isEmpty()) {
      sb.append(label).append(": ").append(String.join(", ", items)).append("\n");
    }
  }
}
