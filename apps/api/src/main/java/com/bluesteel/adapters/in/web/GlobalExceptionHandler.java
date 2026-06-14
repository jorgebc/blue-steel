package com.bluesteel.adapters.in.web;

import com.bluesteel.domain.exception.ActiveSessionExistsException;
import com.bluesteel.domain.exception.AlreadyCampaignMemberException;
import com.bluesteel.domain.exception.AnnotationNotFoundException;
import com.bluesteel.domain.exception.AuthorCannotCoSignException;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.CannotRemoveGmException;
import com.bluesteel.domain.exception.CommitValidationException;
import com.bluesteel.domain.exception.ConcurrentProposalException;
import com.bluesteel.domain.exception.CostCapExceededException;
import com.bluesteel.domain.exception.DomainException;
import com.bluesteel.domain.exception.DuplicateVoteException;
import com.bluesteel.domain.exception.EmailDeliveryException;
import com.bluesteel.domain.exception.EmptyDeltaException;
import com.bluesteel.domain.exception.EntityNotFoundException;
import com.bluesteel.domain.exception.InvalidCredentialsException;
import com.bluesteel.domain.exception.InvalidPasswordException;
import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
import com.bluesteel.domain.exception.ProposalNotFoundException;
import com.bluesteel.domain.exception.ProposalTargetNotFoundException;
import com.bluesteel.domain.exception.QueryResponseParseException;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.RateLimitExceededException;
import com.bluesteel.domain.exception.RefreshTokenException;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.SummaryTooLargeException;
import com.bluesteel.domain.exception.TokenBudgetExceededException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.exception.UnsupportedTargetTypeException;
import com.bluesteel.domain.exception.UserNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps exceptions to the standard error envelope ({@code { "errors": [...] }}) defined in ERR-01.
 * Never leaks stack traces to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                (FieldError fe) ->
                    ApiError.ofField("VALIDATION_ERROR", fe.getDefaultMessage(), fe.getField()))
            .toList();
    return ApiResponse.error(errors);
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
    return ApiResponse.error(ApiError.of("ACCESS_DENIED", "Access denied"));
  }

  @ExceptionHandler(UnauthorizedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex) {
    return ApiResponse.error(ApiError.of("FORBIDDEN", ex.getMessage()));
  }

  @ExceptionHandler(UserNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleUserNotFound(UserNotFoundException ex) {
    return ApiResponse.error(ApiError.of("USER_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(CampaignNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleCampaignNotFound(CampaignNotFoundException ex) {
    return ApiResponse.error(ApiError.of("CAMPAIGN_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(AlreadyCampaignMemberException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> handleAlreadyCampaignMember(AlreadyCampaignMemberException ex) {
    return ApiResponse.error(ApiError.of("ALREADY_CAMPAIGN_MEMBER", ex.getMessage()));
  }

  @ExceptionHandler(CannotRemoveGmException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleCannotRemoveGm(CannotRemoveGmException ex) {
    return ApiResponse.error(ApiError.of("CANNOT_REMOVE_GM", ex.getMessage()));
  }

  @ExceptionHandler(InvalidPasswordException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleInvalidPassword(InvalidPasswordException ex) {
    return ApiResponse.error(ApiError.of("INVALID_CURRENT_PASSWORD", ex.getMessage()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException ex) {
    return ApiResponse.error(ApiError.of("INVALID_CREDENTIALS", ex.getMessage()));
  }

  @ExceptionHandler(RefreshTokenException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> handleRefreshToken(RefreshTokenException ex) {
    return ApiResponse.error(ApiError.of(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(CommitValidationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleCommitValidation(CommitValidationException ex) {
    return ApiResponse.error(ApiError.of(ex.code(), ex.getMessage()));
  }

  /**
   * Malformed JSON body or unreadable request — return 400 rather than letting it fall through to
   * the 500 catch-all.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return ApiResponse.error(
        ApiError.of("MALFORMED_REQUEST", "Request body is malformed or missing"));
  }

  /**
   * Path or query parameter that cannot be coerced to the declared type (e.g. a non-UUID string in
   * a {@code @PathVariable UUID} slot). Spring would otherwise let this fall to the 500 catch-all.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
    return ApiResponse.error(
        ApiError.ofField(
            "INVALID_PATH_PARAMETER",
            "Parameter '%s' is not a valid %s".formatted(ex.getName(), type),
            ex.getName()));
  }

  @ExceptionHandler(SummaryTooLargeException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> handleSummaryTooLarge(SummaryTooLargeException ex) {
    return ApiResponse.error(ApiError.of("SUMMARY_TOO_LARGE", ex.getMessage()));
  }

  @ExceptionHandler(ActiveSessionExistsException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> handleActiveSessionExists(ActiveSessionExistsException ex) {
    return ApiResponse.error(ApiError.of("ACTIVE_SESSION_EXISTS", ex.getMessage()));
  }

  @ExceptionHandler(InvalidSessionStateTransitionException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> handleInvalidSessionStateTransition(
      InvalidSessionStateTransitionException ex) {
    return ApiResponse.error(ApiError.of("INVALID_SESSION_STATE", ex.getMessage()));
  }

  @ExceptionHandler(SessionNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleSessionNotFound(SessionNotFoundException ex) {
    return ApiResponse.error(ApiError.of("SESSION_NOT_FOUND", ex.getMessage()));
  }

  /** Synchronous Query Mode request exceeded its deadline (D-052). */
  @ExceptionHandler(QueryTimeoutException.class)
  @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
  public ApiResponse<Void> handleQueryTimeout(QueryTimeoutException ex) {
    return ApiResponse.error(
        ApiError.of(
            "QUERY_TIMEOUT", "The query timed out. Try rephrasing or narrowing your question."));
  }

  /** Caller exceeded the per-user/per-campaign Query Mode request rate limit (D-096). */
  @ExceptionHandler(RateLimitExceededException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ApiResponse<Void> handleRateLimitExceeded(RateLimitExceededException ex) {
    return ApiResponse.error(ApiError.of("QUERY_RATE_LIMITED", ex.getMessage()));
  }

  /** Daily LLM cost cap reached — Query Mode is temporarily unavailable (D-096). */
  @ExceptionHandler(CostCapExceededException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiResponse<Void> handleCostCapExceeded(CostCapExceededException ex) {
    return ApiResponse.error(ApiError.of("QUERY_COST_CAP", ex.getMessage()));
  }

  /**
   * Estimated LLM input tokens exceeded the configured budget on the synchronous query path (D-034)
   * — a request the caller can fix by narrowing the question, not a server fault.
   */
  @ExceptionHandler(TokenBudgetExceededException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleTokenBudgetExceeded(TokenBudgetExceededException ex) {
    return ApiResponse.error(
        ApiError.of(
            "QUERY_TOKEN_BUDGET_EXCEEDED",
            "Your question pulled in too much context to answer. Narrow it and try again."));
  }

  /** The LLM returned a query answer that could not be parsed — an upstream fault (D-003). */
  @ExceptionHandler(QueryResponseParseException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ApiResponse<Void> handleQueryResponseParse(QueryResponseParseException ex) {
    return ApiResponse.error(
        ApiError.of(
            "QUERY_ANSWER_UNPARSEABLE",
            "The answer service returned an unreadable response. Please try again."));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleEntityNotFound(EntityNotFoundException ex) {
    return ApiResponse.error(ApiError.of("ENTITY_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(AnnotationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleAnnotationNotFound(AnnotationNotFoundException ex) {
    return ApiResponse.error(ApiError.of("ANNOTATION_NOT_FOUND", ex.getMessage()));
  }

  /** Proposal targets an entity type outside the v2 actor/space scope (D-108). */
  @ExceptionHandler(UnsupportedTargetTypeException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleUnsupportedTargetType(UnsupportedTargetTypeException ex) {
    return ApiResponse.error(ApiError.of("UNSUPPORTED_TARGET_TYPE", ex.getMessage()));
  }

  /** Proposal submitted with a missing or empty {@code proposed_delta} (D-104). */
  @ExceptionHandler(EmptyDeltaException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleEmptyDelta(EmptyDeltaException ex) {
    return ApiResponse.error(ApiError.of("EMPTY_DELTA", ex.getMessage()));
  }

  /** Proposal target entity does not exist in the campaign. */
  @ExceptionHandler(ProposalTargetNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleProposalTargetNotFound(ProposalTargetNotFoundException ex) {
    return ApiResponse.error(ApiError.of("PROPOSAL_TARGET_NOT_FOUND", ex.getMessage()));
  }

  /** Proposal referenced by id does not exist in the campaign. */
  @ExceptionHandler(ProposalNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleProposalNotFound(ProposalNotFoundException ex) {
    return ApiResponse.error(ApiError.of("PROPOSAL_NOT_FOUND", ex.getMessage()));
  }

  /** An open or cosigned proposal already targets the same entity (D-106). */
  @ExceptionHandler(ConcurrentProposalException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> handleConcurrentProposal(ConcurrentProposalException ex) {
    return ApiResponse.error(ApiError.of("CONCURRENT_PROPOSAL_EXISTS", ex.getMessage()));
  }

  /** A proposal's author attempted to co-sign their own proposal. */
  @ExceptionHandler(AuthorCannotCoSignException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleAuthorCannotCoSign(AuthorCannotCoSignException ex) {
    return ApiResponse.error(ApiError.of("AUTHOR_CANNOT_COSIGN", ex.getMessage()));
  }

  /** A member voted twice on the same proposal (D-109). */
  @ExceptionHandler(DuplicateVoteException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiResponse<Void> handleDuplicateVote(DuplicateVoteException ex) {
    return ApiResponse.error(ApiError.of("DUPLICATE_VOTE", ex.getMessage()));
  }

  /**
   * Domain invariant violation — expected business-rule failures, not bugs. Logged at WARN (not
   * ERROR) to avoid flooding the error alert channel with expected conditions.
   */
  @ExceptionHandler(DomainException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  public ApiResponse<Void> handleDomainException(DomainException ex) {
    log.warn("Domain invariant violation: {}", ex.getMessage());
    return ApiResponse.error(ApiError.of("DOMAIN_ERROR", ex.getMessage()));
  }

  /**
   * Email provider failure — the surrounding invite transaction has rolled back; admin may retry.
   */
  @ExceptionHandler(EmailDeliveryException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ApiResponse<Void> handleEmailDelivery(EmailDeliveryException ex) {
    log.error("Email delivery failed", ex);
    return ApiResponse.error(
        ApiError.of("EMAIL_DELIVERY_FAILED", "Could not send the email. Please try again."));
  }

  @ExceptionHandler(RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleUnexpected(RuntimeException ex) {
    log.error("Unhandled exception", ex);
    return ApiResponse.error(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
  }
}
