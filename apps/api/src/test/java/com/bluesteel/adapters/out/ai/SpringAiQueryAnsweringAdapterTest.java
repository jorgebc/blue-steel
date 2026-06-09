package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.config.LlmCostLogger;
import com.bluesteel.domain.exception.TokenBudgetExceededException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.ai.chat.model.ChatResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringAiQueryAnsweringAdapter")
class SpringAiQueryAnsweringAdapterTest {

  private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Mock private ChatClient chatClient;
  @Mock private QueryPromptAssembler promptAssembler;
  @Mock private QueryResponseParser responseParser;
  @Mock private LlmCostLogger costLogger;

  private SpringAiQueryAnsweringAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new SpringAiQueryAnsweringAdapter(chatClient, promptAssembler, responseParser, costLogger);
  }

  @Test
  @DisplayName("should assemble the prompt, call ChatClient, and return the parsed QueryResponse")
  void answer_success_returnsParsedResponseAndLogsCost() {
    String question = "Who is Aragorn?";
    List<EntityContext> context =
        List.of(
            new EntityContext(UUID.randomUUID(), "actor", "Aragorn", "a ranger", SESSION_ID, 3));
    String systemPrompt = "SYSTEM PROMPT WITH CONTEXT";
    String llmJson = "{\"answer\":\"Aragorn is a ranger.\",\"citations\":[]}";
    QueryResponse expected =
        new QueryResponse(
            "Aragorn is a ranger.", List.of(new Citation(SESSION_ID, 3, "the ranger")));

    when(promptAssembler.assemble(question, context)).thenReturn(systemPrompt);

    ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class, Answers.RETURNS_SELF);
    CallResponseSpec callSpec = mock(CallResponseSpec.class);
    ChatResponse chatResponse = mock(ChatResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(chatResponse.getResult().getOutput().getText()).thenReturn(llmJson);
    when(chatResponse.getMetadata().getUsage().getPromptTokens()).thenReturn(120);
    when(chatResponse.getMetadata().getUsage().getCompletionTokens()).thenReturn(45);

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.chatResponse()).thenReturn(chatResponse);
    when(responseParser.parse(llmJson)).thenReturn(expected);

    QueryResponse result = adapter.answer(question, context);

    assertThat(result).isEqualTo(expected);
    verify(requestSpec).system(systemPrompt);
    verify(requestSpec).user(question);

    ArgumentCaptor<String> stageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> tokensInCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> tokensOutCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(costLogger)
        .logLlmCall(
            stageCaptor.capture(),
            tokensInCaptor.capture(),
            tokensOutCaptor.capture(),
            startCaptor.capture());
    assertThat(stageCaptor.getValue()).isEqualTo("query_answering");
    assertThat(tokensInCaptor.getValue()).isEqualTo(120);
    assertThat(tokensOutCaptor.getValue()).isEqualTo(45);
  }

  @Test
  @DisplayName(
      "should propagate TokenBudgetExceededException from the assembler without calling the LLM")
  void answer_overBudget_propagatesAndSkipsLlmCall() {
    String question = "Who is Aragorn?";
    List<EntityContext> context = List.of();
    when(promptAssembler.assemble(question, context))
        .thenThrow(new TokenBudgetExceededException(9000, 6000));

    assertThatThrownBy(() -> adapter.answer(question, context))
        .isInstanceOf(TokenBudgetExceededException.class);

    verify(chatClient, never()).prompt();
  }
}
