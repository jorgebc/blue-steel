package com.bluesteel.adapters.out.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration home for AI adapter beans (D-088).
 *
 * <p>Three-way provider profile split:
 *
 * <ul>
 *   <li><b>mock</b> ({@code !llm-real & !llm-ollama}): {@code Mock*} adapters — deterministic
 *       canned responses, zero API cost. Active by default in {@code local} dev and CI.
 *   <li><b>llm-real</b>: Google Gemini via Spring AI {@code ChatClient} + {@code EmbeddingModel}.
 *       One {@code GEMINI_API_KEY} serves both chat and embeddings (D-093).
 *   <li><b>llm-ollama</b>: Local Ollama models via Spring AI. {@code ChatClient} bean wiring
 *       deferred to F2.12.
 * </ul>
 *
 * <p>Exactly one {@link ChatClient} bean must be active per profile — the {@code llm-ollama} bean
 * is added in F2.12.
 */
@Configuration
public class AiConfig {

  /**
   * {@link ChatClient} for real LLM calls via Google Gemini. The {@code ChatModel} is
   * auto-configured from {@code spring.ai.google.genai.*} properties in {@code
   * application-llm-real.properties} (D-093).
   */
  @Bean
  @Profile("llm-real")
  public ChatClient chatClient(ChatModel chatModel) {
    return ChatClient.create(chatModel);
  }
}
