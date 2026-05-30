package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TokenEstimator")
class TokenEstimatorTest {

  @Test
  @DisplayName("should return 0 for an empty string")
  void estimate_emptyString_returnsZero() {
    assertThat(TokenEstimator.estimate("")).isEqualTo(0);
  }

  @Test
  @DisplayName("should return 1 for a 4-character string")
  void estimate_fourCharString_returnsOne() {
    assertThat(TokenEstimator.estimate("abcd")).isEqualTo(1);
  }

  @Test
  @DisplayName("should return 2 for a 5-character string (ceiling)")
  void estimate_fiveCharString_returnsTwo() {
    assertThat(TokenEstimator.estimate("abcde")).isEqualTo(2);
  }

  @Test
  @DisplayName("should estimate correctly for a 400-character string")
  void estimate_fourHundredCharString_returnsHundred() {
    String text = "a".repeat(400);
    assertThat(TokenEstimator.estimate(text)).isEqualTo(100);
  }

  @Test
  @DisplayName("should return 0 for a null string")
  void estimate_nullString_returnsZero() {
    assertThat(TokenEstimator.estimate(null)).isEqualTo(0);
  }
}
