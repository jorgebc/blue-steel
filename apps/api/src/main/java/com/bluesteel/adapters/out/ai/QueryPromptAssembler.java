package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the Query Mode system prompt: the citation-grounding rules (D-003) followed by a numbered
 * list of retrieved {@link EntityContext} snapshots, each labelled with its {@code session_id} so
 * the LLM can cite it. Enforces the input token envelope (D-034) via {@link TokenEstimator},
 * throwing {@link TokenBudgetExceededException} when the assembled prompt plus the question exceeds
 * the configured budget.
 */
@Component
public class QueryPromptAssembler {

  private static final String INSTRUCTIONS =
      """
      You are a lore assistant for a tabletop RPG campaign. Answer the user's question using ONLY \
      the world-state context provided below — never use outside knowledge or invent facts. For \
      every factual claim in your answer, cite the session it comes from using that context \
      entry's session_id. Omit any claim you cannot ground in the provided context. If the context \
      does not contain enough information to answer, say so plainly and return an empty citations \
      array.

      Respond with a single valid JSON object and no other text, in exactly this shape:
      {
        "answer": "<your prose answer>",
        "citations": [
          { "session_id": "<uuid copied from a context entry>", "sequence_number": <integer>, \
      "snippet": "<short supporting quote or paraphrase from that entry>" }
        ]
      }

      World-state context:
      """;

  private final int maxTokens;

  public QueryPromptAssembler(
      @Value("${blue-steel.llm.query-answering-max-tokens:6000}") int maxTokens) {
    this.maxTokens = maxTokens;
  }

  /**
   * Assembles the system prompt for {@code question} over the retrieved {@code context}. The
   * question itself is sent as the user message; it is included here only in the token-budget
   * accounting.
   *
   * @throws TokenBudgetExceededException if the prompt plus question exceeds the configured budget
   */
  public String assemble(String question, List<EntityContext> context) {
    StringBuilder sb = new StringBuilder(INSTRUCTIONS);
    if (context.isEmpty()) {
      sb.append("(no world-state context is available)\n");
    } else {
      for (int i = 0; i < context.size(); i++) {
        EntityContext c = context.get(i);
        sb.append('[')
            .append(i + 1)
            .append("] (session_id: ")
            .append(c.sessionId())
            .append(", sequence_number: ")
            .append(c.versionNumber())
            .append(") ")
            .append(c.entityType())
            .append(" \"")
            .append(c.name())
            .append("\": ")
            .append(c.stateSnapshot())
            .append('\n');
      }
    }
    String systemPrompt = sb.toString();

    int estimated = TokenEstimator.estimate(systemPrompt) + TokenEstimator.estimate(question);
    if (estimated > maxTokens) {
      throw new TokenBudgetExceededException(estimated, maxTokens);
    }
    return systemPrompt;
  }
}
