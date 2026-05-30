package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.adapters.out.ai.SpringAiEntityResolutionAdapter.EntityResolutionDecision;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.config.LlmCostLogger;
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
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringAiEntityResolutionAdapter")
class SpringAiEntityResolutionAdapterTest {

  @Mock private ChatClient chatClient;
  @Mock private LlmCostLogger costLogger;

  private static final UUID EXISTING_ENTITY_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000042");

  private SpringAiEntityResolutionAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SpringAiEntityResolutionAdapter(chatClient, costLogger, 100);
  }

  @Test
  @DisplayName(
      "should throw TokenBudgetExceededException without calling ChatClient when input exceeds max tokens")
  void resolve_oversizeInput_throwsTokenBudgetExceededBeforeLlmCall() {
    // maxContextTokens=100 means 400 chars triggers the budget. Use 401 chars.
    String longDescription = "x".repeat(401);
    ExtractedMention bigMention = new ExtractedMention("A", longDescription, "raw");

    assertThatThrownBy(() -> adapter.resolve(List.of(bigMention), List.of()))
        .isInstanceOf(TokenBudgetExceededException.class)
        .hasMessageContaining("tokens");

    verify(chatClient, never()).prompt();
  }

  @Test
  @DisplayName("should return MATCH with matchedEntityId when LLM returns MATCH outcome")
  @SuppressWarnings("unchecked")
  void resolve_matchOutcome_returnsResolvedEntityWithMatchedId() {
    ExtractedMention mention = new ExtractedMention("Mira", "A scout", "the scout Mira");
    EntityContext candidate =
        new EntityContext(
            EXISTING_ENTITY_ID, "actor", "Mira Voss", "{\"role\":\"scout\"}", null, 1);

    List<ResolvedEntity> results =
        callAdapterWithDecision(
            mention,
            List.of(candidate),
            new EntityResolutionDecision("MATCH", EXISTING_ENTITY_ID.toString()));

    assertThat(results).hasSize(1);
    ResolvedEntity resolved = results.get(0);
    assertThat(resolved.outcome()).isEqualTo(ResolutionOutcome.MATCH);
    assertThat(resolved.matchedEntityId()).isEqualTo(EXISTING_ENTITY_ID);
    assertThat(resolved.mention()).isEqualTo(mention);
  }

  @Test
  @DisplayName("should return NEW with null matchedEntityId when LLM returns NEW outcome")
  @SuppressWarnings("unchecked")
  void resolve_newOutcome_returnsResolvedEntityWithNullId() {
    ExtractedMention mention =
        new ExtractedMention("Thornwick", "A wizard", "the wizard Thornwick");

    List<ResolvedEntity> results =
        callAdapterWithDecision(mention, List.of(), new EntityResolutionDecision("NEW", null));

    assertThat(results).hasSize(1);
    ResolvedEntity resolved = results.get(0);
    assertThat(resolved.outcome()).isEqualTo(ResolutionOutcome.NEW);
    assertThat(resolved.matchedEntityId()).isNull();
  }

  @Test
  @DisplayName(
      "should return UNCERTAIN with null matchedEntityId when LLM returns UNCERTAIN outcome")
  @SuppressWarnings("unchecked")
  void resolve_uncertainOutcome_returnsResolvedEntityWithNullId() {
    ExtractedMention mention = new ExtractedMention("Stranger", "Unknown person", "a stranger");
    EntityContext candidate =
        new EntityContext(EXISTING_ENTITY_ID, "actor", "Unknown", "{}", null, 1);

    List<ResolvedEntity> results =
        callAdapterWithDecision(
            mention, List.of(candidate), new EntityResolutionDecision("UNCERTAIN", null));

    assertThat(results).hasSize(1);
    ResolvedEntity resolved = results.get(0);
    assertThat(resolved.outcome()).isEqualTo(ResolutionOutcome.UNCERTAIN);
    assertThat(resolved.matchedEntityId()).isNull();
  }

  @Test
  @DisplayName("should include mention name and candidate id in the user prompt sent to the LLM")
  @SuppressWarnings("unchecked")
  void resolve_buildsUserPromptContainingMentionAndCandidateDetails() {
    ExtractedMention mention = new ExtractedMention("Aldric", "A warrior", "the warrior Aldric");
    EntityContext candidate =
        new EntityContext(EXISTING_ENTITY_ID, "actor", "Aldric the Brave", "{}", null, 1);

    ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class, Answers.RETURNS_SELF);
    CallResponseSpec callSpec = mock(CallResponseSpec.class);
    ResponseEntity<ChatResponse, EntityResolutionDecision> responseEntity =
        new ResponseEntity<>(null, new EntityResolutionDecision("NEW", null));

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.responseEntity(EntityResolutionDecision.class)).thenReturn(responseEntity);

    adapter.resolve(List.of(mention), List.of(candidate));

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(userCaptor.capture());
    String userPrompt = userCaptor.getValue();
    assertThat(userPrompt).contains("Aldric");
    assertThat(userPrompt).contains(EXISTING_ENTITY_ID.toString());
    assertThat(userPrompt).contains("Aldric the Brave");
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private List<ResolvedEntity> callAdapterWithDecision(
      ExtractedMention mention, List<EntityContext> candidates, EntityResolutionDecision decision) {
    ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class, Answers.RETURNS_SELF);
    CallResponseSpec callSpec = mock(CallResponseSpec.class);
    ResponseEntity<ChatResponse, EntityResolutionDecision> responseEntity =
        new ResponseEntity<>(null, decision);

    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.responseEntity(EntityResolutionDecision.class)).thenReturn(responseEntity);

    return adapter.resolve(List.of(mention), candidates);
  }
}
