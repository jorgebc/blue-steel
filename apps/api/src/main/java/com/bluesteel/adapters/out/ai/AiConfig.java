package com.bluesteel.adapters.out.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
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
 *       One {@code GEMINI_API_KEY} serves both chat and embeddings, supplied by two starters —
 *       {@code spring-ai-starter-model-google-genai} (chat) and {@code
 *       spring-ai-starter-model-google-genai-embedding} (embeddings), since 2.0.x ships them
 *       separately (D-093).
 *   <li><b>llm-ollama</b>: Local Ollama models — {@code ChatClient} from {@code OllamaChatModel};
 *       {@code EmbeddingModel} exposed from {@code OllamaEmbeddingModel}. Zero cost, offline
 *       (D-088).
 * </ul>
 *
 * <p>Exactly one {@link ChatClient} and one {@link EmbeddingModel} bean are active per profile.
 * Each profile excludes the other provider's auto-configuration classes via {@code
 * spring.autoconfigure.exclude} to prevent bean ambiguity when both providers are on the classpath:
 * {@code llm-real} excludes the Ollama auto-configurations; {@code llm-ollama} excludes the Google
 * GenAI chat + embedding auto-configurations.
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

  /**
   * {@link ChatClient} for local Ollama models. The {@code ChatModel} is auto-configured from
   * {@code spring.ai.ollama.*} properties in {@code application-llm-ollama.properties} (D-088).
   */
  @Bean
  @Profile("llm-ollama")
  public ChatClient ollamaChatClient(ChatModel chatModel) {
    return ChatClient.create(chatModel);
  }

  /**
   * Exposes the auto-configured {@link OllamaEmbeddingModel} under the primary {@link
   * EmbeddingModel} type, so injection points in provider-neutral adapters remain unqualified
   * (D-088). Gemini auto-config is disabled in this profile, so no ambiguity arises.
   */
  @Bean
  @Profile("llm-ollama")
  public EmbeddingModel ollamaEmbeddingModel(OllamaEmbeddingModel ollamaEmbeddingModel) {
    return ollamaEmbeddingModel;
  }
}
