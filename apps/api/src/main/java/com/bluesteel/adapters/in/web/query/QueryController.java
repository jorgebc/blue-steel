package com.bluesteel.adapters.in.web.query;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.model.query.QueryUsage;
import com.bluesteel.application.port.in.query.AnswerQueryUseCase;
import com.bluesteel.application.port.in.query.GetQueryUsageUseCase;
import com.bluesteel.domain.exception.UnauthorizedException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Answers free-text questions against a campaign's world state for any campaign member (D-052). */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/queries")
public class QueryController {

  private final AnswerQueryUseCase answerQueryUseCase;
  private final GetQueryUsageUseCase getQueryUsageUseCase;
  private final QueryRateLimiter rateLimiter;

  public QueryController(
      AnswerQueryUseCase answerQueryUseCase,
      GetQueryUsageUseCase getQueryUsageUseCase,
      QueryRateLimiter rateLimiter) {
    this.answerQueryUseCase = answerQueryUseCase;
    this.getQueryUsageUseCase = getQueryUsageUseCase;
    this.rateLimiter = rateLimiter;
  }

  /** Submits a question and returns the grounded answer with its citations; 200 on success. */
  @PostMapping
  public ResponseEntity<ApiResponse<QueryAnswerResponse>> ask(
      @PathVariable UUID id, @Valid @RequestBody QueryRequest request) {
    UUID callerId = resolveCallerId();
    rateLimiter.check(callerId, id);
    QueryResponse result = answerQueryUseCase.answer(id, callerId, request.question());
    return ResponseEntity.ok(ApiResponse.success(toResponse(result)));
  }

  /**
   * Reports the shared daily LLM budget consumed and the caller's remaining rate-limit headroom, so
   * the UI can encourage moderate use of the free tier. Read-only — does not consume the rate
   * limit.
   */
  @GetMapping("/usage")
  public ResponseEntity<ApiResponse<QueryUsageResponse>> usage(@PathVariable UUID id) {
    UUID callerId = resolveCallerId();
    QueryUsage usage = getQueryUsageUseCase.currentUsage();
    QueryUsageResponse response =
        new QueryUsageResponse(
            usage.consumedUsd(),
            usage.capUsd(),
            rateLimiter.remaining(callerId, id),
            rateLimiter.maxRequests(),
            rateLimiter.windowSeconds());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private static QueryAnswerResponse toResponse(QueryResponse result) {
    List<CitationResponse> citations =
        result.citations().stream()
            .map(c -> new CitationResponse(c.sessionId(), c.sequenceNumber(), c.snippet()))
            .toList();
    return new QueryAnswerResponse(result.answer(), citations);
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
      throw new UnauthorizedException("Authentication is required to query a campaign");
    }
    return UUID.fromString(auth.getName());
  }
}
