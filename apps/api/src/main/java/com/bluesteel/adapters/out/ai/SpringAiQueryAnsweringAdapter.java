package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import com.bluesteel.config.LlmCostLogger;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Real query-answering adapter using Spring AI {@link ChatClient} (D-003). Active on {@code
 * llm-real} or {@code llm-ollama} profiles; the active {@code ChatClient} bean is provider-neutral
 * and selected per profile in {@link AiConfig} (D-088).
 *
 * <p>Answers strictly from the retrieved context — the system prompt and grounding rules are built
 * by {@link QueryPromptAssembler}, which also enforces the input token envelope (D-034); the LLM
 * JSON is mapped to grounded citations by {@link QueryResponseParser}. Every call is logged via
 * {@link LlmCostLogger} (LOG-01).
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiQueryAnsweringAdapter implements QueryAnsweringPort {

  private final ChatClient chatClient;
  private final QueryPromptAssembler promptAssembler;
  private final QueryResponseParser responseParser;
  private final LlmCostLogger costLogger;

  public SpringAiQueryAnsweringAdapter(
      ChatClient chatClient,
      QueryPromptAssembler promptAssembler,
      QueryResponseParser responseParser,
      LlmCostLogger costLogger) {
    this.chatClient = chatClient;
    this.promptAssembler = promptAssembler;
    this.responseParser = responseParser;
    this.costLogger = costLogger;
  }

  @Override
  public QueryResponse answer(
      String question, List<EntityContext> relevantContext, String contentLanguage) {
    String systemPrompt = promptAssembler.assemble(question, relevantContext, contentLanguage);

    Instant start = Instant.now();
    ChatResponse chatResponse =
        chatClient.prompt().system(systemPrompt).user(question).call().chatResponse();

    String content = extractContent(chatResponse);
    QueryResponse response = responseParser.parse(content);

    int tokensIn = TokenEstimator.estimate(systemPrompt) + TokenEstimator.estimate(question);
    int tokensOut = TokenEstimator.estimate(content);
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

    costLogger.logLlmCall("query_answering", tokensIn, tokensOut, start);
    return response;
  }

  private static String extractContent(ChatResponse chatResponse) {
    if (chatResponse == null || chatResponse.getResult() == null) {
      return "";
    }
    String text = chatResponse.getResult().getOutput().getText();
    return text == null ? "" : text;
  }
}
