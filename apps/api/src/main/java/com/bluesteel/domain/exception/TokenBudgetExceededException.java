package com.bluesteel.domain.exception;

/**
 * Thrown before any LLM call when estimated input tokens exceed the configured budget (D-034). On
 * the synchronous Query Mode path it maps to {@code 422 QUERY_TOKEN_BUDGET_EXCEEDED}; the async
 * ingestion pipeline catches it and fails the session instead.
 */
public class TokenBudgetExceededException extends RuntimeException {

  public TokenBudgetExceededException(int estimated, int max) {
    super("Estimated " + estimated + " tokens exceeds budget of " + max);
  }
}
