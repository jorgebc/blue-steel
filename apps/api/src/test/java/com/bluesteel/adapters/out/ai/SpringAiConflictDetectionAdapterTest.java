package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.adapters.out.ai.SpringAiConflictDetectionAdapter.ConflictDetectionResponse;
import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.config.LlmCostLogger;
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
@DisplayName("SpringAiConflictDetectionAdapter")
class SpringAiConflictDetectionAdapterTest {

  @Mock private ChatClient chatClient;
  @Mock private LlmCostLogger costLogger;

  private SpringAiConflictDetectionAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SpringAiConflictDetectionAdapter(chatClient, costLogger, 100);
  }

  @Test
  @DisplayName(
      "should throw TokenBudgetExceededException without calling ChatClient when input exceeds max tokens")
  void detect_oversizeInput_throwsTokenBudgetExceededBeforeLlmCall() {
    // maxContextTokens=100 means 400 chars triggers the budget. Use an actor name of 401 chars.
    ExtractedMention bigMention = new ExtractedMention("x".repeat(401), "desc", "raw");
    ExtractionResult extraction =
        new ExtractionResult("Header.", List.of(bigMention), List.of(), List.of(), List.of());

    assertThatThrownBy(() -> adapter.detect(extraction, List.of()))
        .isInstanceOf(TokenBudgetExceededException.class)
        .hasMessageContaining("tokens");

    verify(chatClient, never()).prompt();
  }

  @Test
  @DisplayName("should return conflict warnings mapped from the LLM structured response")
  @SuppressWarnings("unchecked")
  void detect_llmReturnsConflicts_returnsMappedWarnings() {
    ExtractionResult extraction =
        new ExtractionResult("Mira was seen alive.", List.of(), List.of(), List.of(), List.of());
    ConflictWarning warning = new ConflictWarning("Mira", "Previously described as dead.");
    ConflictDetectionResponse response = new ConflictDetectionResponse(List.of(warning));

    List<ConflictWarning> result = callAdapterWithResponse(extraction, List.of(), response);

    assertThat(result).containsExactly(warning);
  }

  @Test
  @DisplayName("should return an empty list when the LLM finds no conflicts")
  @SuppressWarnings("unchecked")
  void detect_llmReturnsNoConflicts_returnsEmptyList() {
    ExtractionResult extraction =
        new ExtractionResult("A quiet session.", List.of(), List.of(), List.of(), List.of());
    ConflictDetectionResponse response = new ConflictDetectionResponse(List.of());

    List<ConflictWarning> result = callAdapterWithResponse(extraction, List.of(), response);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should include world-state context details in the user prompt sent to the LLM")
  @SuppressWarnings("unchecked")
  void detect_buildsUserPromptContainingContextDetails() {
    ExtractionResult extraction =
        new ExtractionResult("Header.", List.of(), List.of(), List.of(), List.of());
    EntityContext ctx = new EntityContext(null, "actor", "Aldric", "{\"alive\":true}", null, 1);

    ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class, Answers.RETURNS_SELF);
    CallResponseSpec callSpec = mock(CallResponseSpec.class);
    ResponseEntity<ChatResponse, ConflictDetectionResponse> responseEntity =
        new ResponseEntity<>(null, new ConflictDetectionResponse(List.of()));

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.responseEntity(ConflictDetectionResponse.class)).thenReturn(responseEntity);

    adapter.detect(extraction, List.of(ctx));

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userCaptor.capture());
    String prompt = userCaptor.getValue();
    assertThat(prompt).contains("Aldric");
    assertThat(prompt).contains("{\"alive\":true}");
    assertThat(prompt).contains("actor");
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private List<ConflictWarning> callAdapterWithResponse(
      ExtractionResult extraction,
      List<EntityContext> context,
      ConflictDetectionResponse response) {
    ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class, Answers.RETURNS_SELF);
    CallResponseSpec callSpec = mock(CallResponseSpec.class);
    ResponseEntity<ChatResponse, ConflictDetectionResponse> responseEntity =
        new ResponseEntity<>(null, response);

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.responseEntity(ConflictDetectionResponse.class)).thenReturn(responseEntity);

    return adapter.detect(extraction, context);
  }
}
