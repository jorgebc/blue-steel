package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

@DisplayName("AiConfig")
class AiConfigTest {

  @Test
  @DisplayName("should create a non-null ChatClient from a provided ChatModel")
  void chatClient_withMockModel_returnsNonNull() {
    AiConfig aiConfig = new AiConfig();
    ChatModel chatModel = mock(ChatModel.class);

    ChatClient chatClient = aiConfig.chatClient(chatModel);

    assertThat(chatClient).isNotNull();
  }
}
