package com.bluesteel.domain.exception;

/**
 * Thrown when the configured daily LLM cost cap has been reached, blocking further queries (D-096).
 * Mapped to {@code 503 QUERY_COST_CAP}.
 */
public class CostCapExceededException extends RuntimeException {
  public CostCapExceededException() {
    super("The daily query budget has been reached. Please try again tomorrow.");
  }
}
