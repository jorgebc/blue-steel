package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;

@DisplayName("AiConfig — llm-ollama profile beans")
class OllamaWiringTest {

  private final AiConfig aiConfig = new AiConfig();

  @Test
  @DisplayName("should create a non-null ChatClient from a mocked Ollama ChatModel")
  void ollamaChatClient_withMockModel_returnsNonNull() {
    ChatModel chatModel = mock(ChatModel.class);

    ChatClient chatClient = aiConfig.ollamaChatClient(chatModel);

    assertThat(chatClient).isNotNull();
  }

  @Test
  @DisplayName("should expose the provided OllamaEmbeddingModel as the primary EmbeddingModel")
  void ollamaEmbeddingModel_withMockModel_returnsSameInstance() {
    OllamaEmbeddingModel embeddingModel = mock(OllamaEmbeddingModel.class);

    EmbeddingModel result = aiConfig.ollamaEmbeddingModel(embeddingModel);

    assertThat(result).isSameAs(embeddingModel);
  }
}
