package com.bluesteel.domain.exception;

/**
 * Thrown when a client exceeds the per-IP request rate limit on the auth endpoints (login/refresh).
 * Mapped to {@code 429 AUTH_RATE_LIMITED}. Defends login/refresh against brute-force and
 * credential-stuffing.
 */
public class AuthRateLimitExceededException extends RuntimeException {
  public AuthRateLimitExceededException() {
    super("Too many attempts. Please wait a moment and try again.");
  }
}
