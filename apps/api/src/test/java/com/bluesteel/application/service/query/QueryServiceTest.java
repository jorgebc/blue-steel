package com.bluesteel.application.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryLogEntry;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import com.bluesteel.application.port.out.query.QueryContextRetrievalPort;
import com.bluesteel.application.port.out.query.QueryLogRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CostCapExceededException;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryService")
class QueryServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private EmbeddingPort embeddingPort;
  @Mock private QueryContextRetrievalPort retrievalPort;
  @Mock private QueryAnsweringPort queryAnsweringPort;
  @Mock private LlmCostAccountingPort costAccountingPort;
  @Mock private QueryLogRepository queryLogRepository;

  private QueryService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final String QUESTION = "Who is Mira?";
  private static final int TOP_N = 8;
  private static final double DAILY_CAP_USD = 1.00;
  private static final float[] EMBEDDING = {0.1f, 0.2f, 0.3f};
  private static final UUID CONTEXT_SESSION = UUID.randomUUID();
  private static final List<EntityContext> CONTEXT =
      List.of(
          new EntityContext(
              UUID.randomUUID(), "actor", "Mira", "{\"name\":\"Mira\"}", CONTEXT_SESSION, 1));

  @BeforeEach
  void setUp() {
    sut = serviceWithExecutor(ForkJoinPool.commonPool());
  }

  private QueryService serviceWithExecutor(Executor executor) {
    return new QueryService(
        membershipPort,
        embeddingPort,
        retrievalPort,
        queryAnsweringPort,
        costAccountingPort,
        queryLogRepository,
        executor,
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
            "Mira is a rogue.", List.of(new Citation(CONTEXT_SESSION, 1, "Mira appears.")));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT)).thenReturn(expected);

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("should drop citations whose session is not among the retrieved context")
  void answer_ungroundedCitation_isDropped() {
    Citation grounded = new Citation(CONTEXT_SESSION, 1, "Mira appears.");
    Citation hallucinated = new Citation(UUID.randomUUID(), 2, "Invented.");
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT))
        .thenReturn(new QueryResponse("Answer.", List.of(grounded, hallucinated)));

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual.citations()).containsExactly(grounded);
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
    verify(queryLogRepository, never()).save(any(QueryLogEntry.class));
  }

  @Test
  @DisplayName("should WARN with caller, campaign, and tally vs cap when the cost cap trips")
  void answer_capReached_logsWarn() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ch.qos.logback.classic.Logger serviceLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(QueryService.class);
    serviceLogger.addAppender(appender);
    try {
      when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
          .thenReturn(Optional.of(CampaignRole.PLAYER));
      when(costAccountingPort.currentDailyCostUsd()).thenReturn(DAILY_CAP_USD);

      assertThatThrownBy(() -> sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
          .isInstanceOf(CostCapExceededException.class);
    } finally {
      serviceLogger.detachAppender(appender);
    }

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .contains(CALLER_ID.toString())
                  .contains(CAMPAIGN_ID.toString())
                  .contains(String.valueOf(DAILY_CAP_USD));
            });
  }

  @Test
  @DisplayName(
      "should expose user_id and campaign_id in MDC to the answering call and clear both after")
  void answer_setsMdcForAnsweringCall_andClearsAfter() {
    AtomicReference<String> userIdInMdc = new AtomicReference<>();
    AtomicReference<String> campaignIdInMdc = new AtomicReference<>();
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT))
        .thenAnswer(
            invocation -> {
              userIdInMdc.set(MDC.get("user_id"));
              campaignIdInMdc.set(MDC.get("campaign_id"));
              return new QueryResponse("Answer.", List.of());
            });

    sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(userIdInMdc.get()).isEqualTo(CALLER_ID.toString());
    assertThat(campaignIdInMdc.get()).isEqualTo(CAMPAIGN_ID.toString());
    assertThat(MDC.get("user_id")).isNull();
    assertThat(MDC.get("campaign_id")).isNull();
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
  @DisplayName("should run the answering call on the injected query executor")
  void answer_runsAnsweringOnInjectedExecutor() {
    AtomicReference<String> answeringThreadName = new AtomicReference<>();
    Executor namedThreadExecutor = command -> new Thread(command, "query-test-executor").start();
    sut = serviceWithExecutor(namedThreadExecutor);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT))
        .thenAnswer(
            invocation -> {
              answeringThreadName.set(Thread.currentThread().getName());
              return new QueryResponse("Answer.", List.of());
            });

    sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(answeringThreadName.get()).isEqualTo("query-test-executor");
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

    verify(queryLogRepository, never()).save(any(QueryLogEntry.class));
  }

  @Test
  @DisplayName("should persist a Q&A log entry with the grounded answer on success")
  void answer_success_persistsLogEntry() {
    Citation grounded = new Citation(CONTEXT_SESSION, 1, "Mira appears.");
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT))
        .thenReturn(new QueryResponse("Mira is a rogue.", List.of(grounded)));

    sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    ArgumentCaptor<QueryLogEntry> captor = ArgumentCaptor.forClass(QueryLogEntry.class);
    verify(queryLogRepository).save(captor.capture());
    QueryLogEntry saved = captor.getValue();
    assertThat(saved.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(saved.askerId()).isEqualTo(CALLER_ID);
    assertThat(saved.question()).isEqualTo(QUESTION);
    assertThat(saved.answer()).isEqualTo("Mira is a rogue.");
    assertThat(saved.citations()).containsExactly(grounded);
    assertThat(saved.id()).isNotNull();
    assertThat(saved.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("should persist only the grounded citations, not the dropped ungrounded ones")
  void answer_persistsOnlyGroundedCitations() {
    Citation grounded = new Citation(CONTEXT_SESSION, 1, "Mira appears.");
    Citation hallucinated = new Citation(UUID.randomUUID(), 2, "Invented.");
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT))
        .thenReturn(new QueryResponse("Answer.", List.of(grounded, hallucinated)));

    sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    ArgumentCaptor<QueryLogEntry> captor = ArgumentCaptor.forClass(QueryLogEntry.class);
    verify(queryLogRepository).save(captor.capture());
    assertThat(captor.getValue().citations()).containsExactly(grounded);
  }

  @Test
  @DisplayName("should still return the answer when persisting the Q&A log entry fails")
  void answer_logWriteFails_stillReturnsAnswer() {
    QueryResponse expected =
        new QueryResponse(
            "Mira is a rogue.", List.of(new Citation(CONTEXT_SESSION, 1, "Mira appears.")));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(embeddingPort.embed(QUESTION)).thenReturn(EMBEDDING);
    when(retrievalPort.retrieveRelevantContext(CAMPAIGN_ID, EMBEDDING, TOP_N)).thenReturn(CONTEXT);
    when(queryAnsweringPort.answer(QUESTION, CONTEXT)).thenReturn(expected);
    doThrow(new RuntimeException("db down"))
        .when(queryLogRepository)
        .save(any(QueryLogEntry.class));

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
  }
}
