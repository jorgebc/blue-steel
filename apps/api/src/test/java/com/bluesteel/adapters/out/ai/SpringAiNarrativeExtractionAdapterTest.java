package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.ingestion.ExtractedEvent;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.config.LlmCostLogger;
import com.bluesteel.domain.exception.TokenBudgetExceededException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringAiNarrativeExtractionAdapter")
class SpringAiNarrativeExtractionAdapterTest {

  @Mock private ChatClient chatClient;
  @Mock private LlmCostLogger costLogger;

  private SpringAiNarrativeExtractionAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SpringAiNarrativeExtractionAdapter(chatClient, costLogger, 100);
  }

  @Test
  @DisplayName("should throw TokenBudgetExceededException when input exceeds max tokens")
  void extract_oversizeInput_throwsTokenBudgetExceeded() {
    // 100 chars / 4 = 25 tokens; maxContextTokens = 100 means 400 chars. Use 401 chars to exceed.
    // With maxContextTokens=100, need > 400 chars to exceed. Use a 1000-char input.
    String oversizedInput = "x".repeat(401);

    assertThatThrownBy(() -> adapter.extract(oversizedInput))
        .isInstanceOf(TokenBudgetExceededException.class)
        .hasMessageContaining("tokens");

    verify(chatClient, never()).prompt();
  }

  @Test
  @DisplayName("should call ChatClient and return the ExtractionResult entity on success")
  @SuppressWarnings("unchecked")
  void extract_validInput_returnsExtractionResult() {
    String rawSummaryText = "The party entered the dungeon and fought goblins.";

    ExtractionResult expected =
        new ExtractionResult(
            "The party explored a dungeon.",
            List.of(new ExtractedMention("Aragorn", "A ranger", "the ranger")),
            List.of(new ExtractedMention("Dungeon", "Underground", "the dungeon")),
            List.of(
                new ExtractedEvent(
                    "Battle", "Combat", "battle", "Dungeon", List.of("Aragorn"), "fought goblins")),
            List.of());

    // Mock the fluent ChatClient chain using RETURNS_SELF for the request spec
    ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class, Answers.RETURNS_SELF);
    CallResponseSpec callSpec = mock(CallResponseSpec.class);

    ChatResponse chatResponse = mock(ChatResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(chatResponse.getMetadata().getUsage().getPromptTokens()).thenReturn(30);
    when(chatResponse.getMetadata().getUsage().getCompletionTokens()).thenReturn(20);

    ResponseEntity<ChatResponse, ExtractionResult> responseEntity =
        new ResponseEntity<>(chatResponse, expected);

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.responseEntity(ExtractionResult.class)).thenReturn(responseEntity);

    ExtractionResult result = adapter.extract(rawSummaryText);

    assertThat(result).isEqualTo(expected);

    // Verify user() was called with the exact input text
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userCaptor.capture());
    assertThat(userCaptor.getValue()).isEqualTo(rawSummaryText);

    // Verify cost logger was invoked with stage="extraction"
    ArgumentCaptor<String> stageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> tokensInCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> tokensOutCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<java.time.Instant> startCaptor =
        ArgumentCaptor.forClass(java.time.Instant.class);
    verify(costLogger)
        .logLlmCall(
            stageCaptor.capture(),
            tokensInCaptor.capture(),
            tokensOutCaptor.capture(),
            startCaptor.capture());
    assertThat(stageCaptor.getValue()).isEqualTo("extraction");
    assertThat(tokensInCaptor.getValue()).isEqualTo(30);
    assertThat(tokensOutCaptor.getValue()).isEqualTo(20);
  }
}
