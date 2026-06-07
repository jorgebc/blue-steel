package com.bluesteel.application.service.query;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.in.query.AnswerQueryUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import com.bluesteel.application.port.out.query.QueryContextRetrievalPort;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Answers a free-text question against a campaign's world state for any campaign member, enforcing
 * a hard synchronous deadline (D-052). Embeds the question, retrieves the top-N most similar
 * committed entity-version snapshots (D-062, D-034), then delegates to the answering port.
 */
@Service
public class QueryService implements AnswerQueryUseCase {

  private static final Logger log = LoggerFactory.getLogger(QueryService.class);

  private final CampaignMembershipPort membershipPort;
  private final EmbeddingPort embeddingPort;
  private final QueryContextRetrievalPort retrievalPort;
  private final QueryAnsweringPort queryAnsweringPort;
  private final int timeoutSeconds;
  private final int topN;

  public QueryService(
      CampaignMembershipPort membershipPort,
      EmbeddingPort embeddingPort,
      QueryContextRetrievalPort retrievalPort,
      QueryAnsweringPort queryAnsweringPort,
      @Value("${query.timeout-seconds:20}") int timeoutSeconds,
      @Value("${query.retrieval.top-n:8}") int topN) {
    this.membershipPort = membershipPort;
    this.embeddingPort = embeddingPort;
    this.retrievalPort = retrievalPort;
    this.queryAnsweringPort = queryAnsweringPort;
    this.timeoutSeconds = timeoutSeconds;
    this.topN = topN;
  }

  @Override
  public QueryResponse answer(UUID campaignId, UUID callerId, String question) {
    log.info("Answering query campaignId={} callerId={}", campaignId, callerId);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    float[] questionEmbedding = embeddingPort.embed(question);
    List<EntityContext> context =
        retrievalPort.retrieveRelevantContext(campaignId, questionEmbedding, topN);

    CompletableFuture<QueryResponse> future =
        CompletableFuture.supplyAsync(() -> queryAnsweringPort.answer(question, context));
    try {
      QueryResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
      log.info("Answered query campaignId={} callerId={}", campaignId, callerId);
      return response;
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
  }
}
