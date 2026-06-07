package com.bluesteel.application.service.query;

import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.in.query.AnswerQueryUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
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
 * a hard synchronous deadline (D-052). Retrieval is wired in later (F3.2.3); for now the answering
 * port receives an empty context placeholder.
 */
@Service
public class QueryService implements AnswerQueryUseCase {

  private static final Logger log = LoggerFactory.getLogger(QueryService.class);

  private final CampaignMembershipPort membershipPort;
  private final QueryAnsweringPort queryAnsweringPort;
  private final int timeoutSeconds;

  public QueryService(
      CampaignMembershipPort membershipPort,
      QueryAnsweringPort queryAnsweringPort,
      @Value("${query.timeout-seconds:20}") int timeoutSeconds) {
    this.membershipPort = membershipPort;
    this.queryAnsweringPort = queryAnsweringPort;
    this.timeoutSeconds = timeoutSeconds;
  }

  @Override
  public QueryResponse answer(UUID campaignId, UUID callerId, String question) {
    log.info("Answering query campaignId={} callerId={}", campaignId, callerId);

    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));

    CompletableFuture<QueryResponse> future =
        CompletableFuture.supplyAsync(() -> queryAnsweringPort.answer(question, List.of()));
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
