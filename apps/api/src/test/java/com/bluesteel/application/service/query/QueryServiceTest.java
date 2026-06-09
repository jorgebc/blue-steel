package com.bluesteel.application.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import com.bluesteel.application.port.out.query.QueryContextRetrievalPort;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CostCapExceededException;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryService")
class QueryServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private EmbeddingPort embeddingPort;
  @Mock private QueryContextRetrievalPort retrievalPort;
  @Mock private QueryAnsweringPort queryAnsweringPort;
  @Mock private LlmCostAccountingPort costAccountingPort;

  private QueryService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final String QUESTION = "Who is Mira?";
  private static final int TOP_N = 8;
  private static final double DAILY_CAP_USD = 1.00;
  private static final float[] EMBEDDING = {0.1f, 0.2f, 0.3f};
  private static final List<EntityContext> CONTEXT =
      List.of(
          new EntityContext(
              UUID.randomUUID(), "actor", "Mira", "{\"name\":\"Mira\"}", UUID.randomUUID(), 1));

  @BeforeEach
  void setUp() {
    sut =
        new QueryService(
            membershipPort,
            embeddingPort,
            retrievalPort,
            queryAnsweringPort,
            costAccountingPort,
            1,
            TOP_N,
            DAILY_CAP_USD);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void answer_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should answer for a PLAYER member — Query Mode is open to every campaign member")
  void answer_playerMember_returnsAnswer() {
    QueryResponse expected =
        new QueryResponse(
            "Mira is a rogue.", List.of(new Citation(UUID.randomUUID(), 1, "Mira appears.")));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT)).thenReturn(expected);

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("should throw CostCapExceededException when the daily cost cap is reached")
  void answer_capReached_throwsCostCapExceeded() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(costAccountingPort.currentDailyCostUsd()).thenReturn(DAILY_CAP_USD);

    assertThatThrownBy(() -> sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .isInstanceOf(CostCapExceededException.class);

    verify(embeddingPort, never()).embed(QUESTION);
    verify(queryAnsweringPort, never()).answer(QUESTION, CONTEXT);
  }

  @Test
  @DisplayName("should embed, retrieve context, then answer — in that order")
  void answer_embedsThenRetrievesThenAnswers() {
    QueryResponse expected = new QueryResponse("Answer.", List.of());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT)).thenReturn(expected);

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
    InOrder inOrder = inOrder(embeddingPort, retrievalPort, queryAnsweringPort);
    inOrder.verify(embeddingPort).embed(QUESTION);
    inOrder.verify(retrievalPort).retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N);
    inOrder.verify(queryAnsweringPort).answer(QUESTION, CONTEXT);
  }

  @Test
  @DisplayName("should still answer when retrieval returns an empty context")
  void answer_emptyContext_stillAnswers() {
    QueryResponse expected = new QueryResponse("I don't have information on that.", List.of());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N))
        .thenReturn(List.of());
    when(queryAnsweringPort.answer(QUESTION, List.of())).thenReturn(expected);

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("should throw QueryTimeoutException when answering exceeds the deadline")
  void answer_slowPort_throwsQueryTimeout() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT))
        .thenAnswer(
            invocation -> {
              Thread.sleep(2_000);
              return new QueryResponse("too late", List.of());
            });

    assertThatThrownBy(() -> sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .isInstanceOf(QueryTimeoutException.class);
  }
}
