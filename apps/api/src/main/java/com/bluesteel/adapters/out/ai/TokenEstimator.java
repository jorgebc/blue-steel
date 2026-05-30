package com.bluesteel.adapters.out.ai;

/** Conservative token-count estimator for pre-call input-budget checks (D-034). */
public class TokenEstimator {

  private TokenEstimator() {}

  /** Estimates token count: 1 token ≈ 4 characters for English text. */
  public static int estimate(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return (int) Math.ceil(text.length() / 4.0);
  }
}
