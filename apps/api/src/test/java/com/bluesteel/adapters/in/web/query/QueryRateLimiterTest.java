package com.bluesteel.adapters.in.web.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.domain.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueryRateLimiter")
class QueryRateLimiterTest {

  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CAMPAIGN = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final int MAX_REQUESTS = 3;
  private static final long WINDOW_SECONDS = 60;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-06-09T12:00:00Z"));
  private final QueryRateLimiter sut = new QueryRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, clock);

  @Test
  @DisplayName("should allow requests up to the configured limit within the window")
  void allowsUpToLimit() {
    assertThatCode(
            () -> {
              for (int i = 0; i < MAX_REQUESTS; i++) {
                sut.check(USER, CAMPAIGN);
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should throw RateLimitExceededException on the request past the limit")
  void rejectsOverLimit() {
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(USER, CAMPAIGN);
    }

    assertThatThrownBy(() -> sut.check(USER, CAMPAIGN))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  @DisplayName("should allow requests again once the window has elapsed")
  void allowsAfterWindowElapses() {
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(USER, CAMPAIGN);
    }

    clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));

    assertThatCode(() -> sut.check(USER, CAMPAIGN)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should track each (user, campaign) key independently")
  void keysAreIsolated() {
    UUID otherCampaign = UUID.fromString("22222222-2222-2222-2222-222222222222");
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(USER, CAMPAIGN);
    }

    assertThatCode(() -> sut.check(USER, otherCampaign)).doesNotThrowAnyException();
  }

  /**
   * Test clock whose instant can be advanced to exercise sliding-window expiry deterministically.
   */
  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant start) {
      this.now = start;
    }

    private void advance(Duration amount) {
      now = now.plus(amount);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }
}
