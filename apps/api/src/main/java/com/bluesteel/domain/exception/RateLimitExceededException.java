package com.bluesteel.domain.exception;

/**
 * Thrown when a caller exceeds the per-user/per-campaign Query Mode request rate limit (D-096).
 * Mapped to {@code 429 QUERY_RATE_LIMITED}.
 */
public class RateLimitExceededException extends RuntimeException {
  public RateLimitExceededException() {
    super("Too many queries. Please wait a moment and try again.");
  }
}
