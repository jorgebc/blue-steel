package com.bluesteel.application.service.query;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.in.query.AnswerQueryUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import com.bluesteel.application.port.out.query.QueryContextRetrievalPort;
import com.bluesteel.domain.exception.CostCapExceededException;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Answers a free-text question against a campaign's world state for any campaign member, enforcing
 * a hard synchronous deadline (D-052). Embeds the question, retrieves the top-N most similar
 * committed entity-version snapshots (D-062, D-034), then delegates to the answering port on the
 * dedicated bounded query executor.
 *
 * <p>Known cost-cap limits, accepted for the single-instance free-tier deployment (D-096): the
 * daily cap check is read-then-act, so concurrent queries may overshoot the cap by the calls
 * admitted before their cost is recorded — bounded by the query executor's pool plus queue and by
 * the per-user rate limit. On timeout the future is cancelled, but the blocking LLM call is not
 * interrupted: it completes on its pool thread and still records its cost (the spend was genuinely
 * incurred).
 */
@Service
public class QueryService implements AnswerQueryUseCase {

  private static final Logger log = LoggerFactory.getLogger(QueryService.class);

  private final CampaignMembershipPort membershipPort;
  private final EmbeddingPort embeddingPort;
  private final QueryContextRetrievalPort retrievalPort;
  private final QueryAnsweringPort queryAnsweringPort;
  private final LlmCostAccountingPort costAccountingPort;
  private final Executor queryExecutor;
  private final int timeoutSeconds;
  private final int topN;
  private final double dailyCapUsd;

  public QueryService(
      CampaignMembershipPort membershipPort,
      EmbeddingPort embeddingPort,
      QueryContextRetrievalPort retrievalPort,
      QueryAnsweringPort queryAnsweringPort,
      LlmCostAccountingPort costAccountingPort,
      @Qualifier("queryTaskExecutor") Executor queryExecutor,
      @Value("${query.timeout-seconds:20}") int timeoutSeconds,
      @Value("${query.retrieval.top-n:8}") int topN,
      @Value("${query.cost-cap.daily-usd:1.00}") double dailyCapUsd) {
    this.membershipPort = membershipPort;
    this.embeddingPort = embeddingPort;
    this.retrievalPort = retrievalPort;
    this.queryAnsweringPort = queryAnsweringPort;
    this.costAccountingPort = costAccountingPort;
    this.queryExecutor = queryExecutor;
    this.timeoutSeconds = timeoutSeconds;
    this.topN = topN;
    this.dailyCapUsd = dailyCapUsd;
  }

  @Override
  public QueryResponse answer(UUID campaignId, UUID callerId, String question) {
    log.info("Answering query campaignId={} callerId={}", campaignId, callerId);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    MDC.put("user_id", callerId.toString());
    MDC.put("campaign_id", campaignId.toString());
    try {
      double currentDailyCostUsd = costAccountingPort.currentDailyCostUsd();
      if (currentDailyCostUsd >= dailyCapUsd) {
        log.warn(
            "Query cost cap tripped callerId={} campaignId={} currentDailyCostUsd={} dailyCapUsd={}",
            callerId,
            campaignId,
            currentDailyCostUsd,
            dailyCapUsd);
        throw new CostCapExceededException();
      }

      float[] questionEmbedding = embeddingPort.embed(question);
      List<EntityContext> context =
          retrievalPort.retrieveRelevantContext(campaignId, questionEmbedding, topN);

      // MDC is thread-local: hand the caller's context map to the pool thread so the
      // query_answering cost-log line stays attributable (LOG-01).
      Map<String, String> mdc = MDC.getCopyOfContextMap();
      CompletableFuture<QueryResponse> future =
          CompletableFuture.supplyAsync(
              () -> {
                if (mdc != null) {
                  MDC.setContextMap(mdc);
                }
                try {
                  return queryAnsweringPort.answer(question, context);
                } finally {
                  MDC.clear();
                }
              },
              queryExecutor);
      try {
        QueryResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
        log.info("Answered query campaignId={} callerId={}", campaignId, callerId);
        return withGroundedCitations(response, context, campaignId);
      } catch (TimeoutException e) {
        future.cancel(true);
        throw new QueryTimeoutException();
      } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException re) {
          throw re;
        }
        throw new IllegalStateException("Query answering failed", cause);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Query answering was interrupted", e);
      }
    } finally {
      MDC.remove("user_id");
      MDC.remove("campaign_id");
    }
  }

  /**
   * Drops any citation whose session is not among the retrieved context: the LLM may only cite
   * sessions it was given, so an unknown session id is a hallucination that would otherwise resolve
   * to a broken citation link (D-003).
   */
  private QueryResponse withGroundedCitations(
      QueryResponse response, List<EntityContext> context, UUID campaignId) {
    Set<UUID> retrievedSessions =
        context.stream().map(EntityContext::sessionId).collect(Collectors.toSet());
    List<Citation> grounded =
        response.citations().stream()
            .filter(citation -> retrievedSessions.contains(citation.sessionId()))
            .toList();
    if (grounded.size() != response.citations().size()) {
      log.warn(
          "Dropped {} ungrounded citation(s) from query answer campaignId={}",
          response.citations().size() - grounded.size(),
          campaignId);
      return new QueryResponse(response.answer(), grounded);
    }
    return response;
  }
}
