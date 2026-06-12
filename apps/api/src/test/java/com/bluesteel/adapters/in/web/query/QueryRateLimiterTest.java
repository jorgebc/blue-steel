package com.bluesteel.adapters.in.web.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bluesteel.domain.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("QueryRateLimiter")
class QueryRateLimiterTest {

  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CAMPAIGN = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final int MAX_REQUESTS = 3;
  private static final long WINDOW_SECONDS = 60;
  private static final int MAX_TRACKED_KEYS = 100;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-06-09T12:00:00Z"));
  private final QueryRateLimiter sut =
      new QueryRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, MAX_TRACKED_KEYS, clock);

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
  @DisplayName("should WARN with the caller key when a request is rejected")
  void rejectsOverLimit_logsWarn() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ch.qos.logback.classic.Logger limiterLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(QueryRateLimiter.class);
    limiterLogger.addAppender(appender);
    try {
      for (int i = 0; i < MAX_REQUESTS; i++) {
        sut.check(USER, CAMPAIGN);
      }

      assertThatThrownBy(() -> sut.check(USER, CAMPAIGN))
          .isInstanceOf(RateLimitExceededException.class);
    } finally {
      limiterLogger.detachAppender(appender);
    }

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .contains(USER.toString())
                  .contains(CAMPAIGN.toString());
            });
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

  @Test
  @DisplayName("should evict a stale key once the tracked-key cap is exceeded")
  void staleKeyIsEvictedPastTrackedKeyCap() {
    QueryRateLimiter capped = new QueryRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, 1, clock);
    capped.check(USER, CAMPAIGN);

    clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));
    UUID otherCampaign = UUID.fromString("22222222-2222-2222-2222-222222222222");
    capped.check(USER, otherCampaign);

    assertThat(capped.trackedKeyCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("should never evict a key that is still active within the window")
  void activeKeyIsNotEvictedPastTrackedKeyCap() {
    QueryRateLimiter capped = new QueryRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, 1, clock);
    capped.check(USER, CAMPAIGN);

    clock.advance(Duration.ofSeconds(1));
    UUID otherCampaign = UUID.fromString("22222222-2222-2222-2222-222222222222");
    capped.check(USER, otherCampaign);

    assertThat(capped.trackedKeyCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("should report the full limit as remaining before any request")
  void remaining_isFullBeforeAnyRequest() {
    assertThat(sut.remaining(USER, CAMPAIGN)).isEqualTo(MAX_REQUESTS);
  }

  @Test
  @DisplayName("should decrease remaining as requests are recorded within the window")
  void remaining_decreasesWithRecordedRequests() {
    sut.check(USER, CAMPAIGN);
    sut.check(USER, CAMPAIGN);

    assertThat(sut.remaining(USER, CAMPAIGN)).isEqualTo(MAX_REQUESTS - 2);
  }

  @Test
  @DisplayName("should not consume the limit when remaining is queried")
  void remaining_doesNotConsumeBudget() {
    for (int i = 0; i < 5; i++) {
      sut.remaining(USER, CAMPAIGN);
    }

    assertThatCode(
            () -> {
              for (int i = 0; i < MAX_REQUESTS; i++) {
                sut.check(USER, CAMPAIGN);
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should report the full limit again once the window has elapsed")
  void remaining_resetsAfterWindowElapses() {
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(USER, CAMPAIGN);
    }
    assertThat(sut.remaining(USER, CAMPAIGN)).isZero();

    clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));

    assertThat(sut.remaining(USER, CAMPAIGN)).isEqualTo(MAX_REQUESTS);
  }

  @Test
  @DisplayName("should expose the configured max requests and window length")
  void exposesConfiguration() {
    assertThat(sut.maxRequests()).isEqualTo(MAX_REQUESTS);
    assertThat(sut.windowSeconds()).isEqualTo(WINDOW_SECONDS);
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
