package com.bluesteel.adapters.out.ai;

/**
 * Thrown before any {@code ChatClient} call when estimated input tokens exceed the configured
 * budget (D-034).
 */
public class TokenBudgetExceededException extends RuntimeException {

  public TokenBudgetExceededException(int estimated, int max) {
    super("Estimated " + estimated + " tokens exceeds budget of " + max);
  }
}
