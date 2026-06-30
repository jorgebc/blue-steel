package com.bluesteel.adapters.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bluesteel.domain.exception.AuthRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("AuthRateLimiter")
class AuthRateLimiterTest {

  private static final String IP = "203.0.113.7";
  private static final String OTHER_IP = "203.0.113.8";
  private static final int MAX_REQUESTS = 3;
  private static final long WINDOW_SECONDS = 60;
  private static final int MAX_TRACKED_KEYS = 100;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-06-09T12:00:00Z"));
  private final AuthRateLimiter sut =
      new AuthRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, MAX_TRACKED_KEYS, clock);

  @Test
  @DisplayName("should allow requests up to the configured limit within the window")
  void allowsUpToLimit() {
    assertThatCode(
            () -> {
              for (int i = 0; i < MAX_REQUESTS; i++) {
                sut.check(IP);
              }
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should throw AuthRateLimitExceededException on the request past the limit")
  void rejectsOverLimit() {
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(IP);
    }

    assertThatThrownBy(() -> sut.check(IP)).isInstanceOf(AuthRateLimitExceededException.class);
  }

  @Test
  @DisplayName("should WARN with the client IP when a request is rejected")
  void rejectsOverLimit_logsWarn() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ch.qos.logback.classic.Logger limiterLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuthRateLimiter.class);
    limiterLogger.addAppender(appender);
    try {
      for (int i = 0; i < MAX_REQUESTS; i++) {
        sut.check(IP);
      }

      assertThatThrownBy(() -> sut.check(IP)).isInstanceOf(AuthRateLimitExceededException.class);
    } finally {
      limiterLogger.detachAppender(appender);
    }

    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage()).contains(IP);
            });
  }

  @Test
  @DisplayName("should allow requests again once the window has elapsed")
  void allowsAfterWindowElapses() {
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(IP);
    }

    clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));

    assertThatCode(() -> sut.check(IP)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should track each client IP key independently")
  void keysAreIsolated() {
    for (int i = 0; i < MAX_REQUESTS; i++) {
      sut.check(IP);
    }

    assertThatCode(() -> sut.check(OTHER_IP)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should evict a stale key once the tracked-key cap is exceeded")
  void staleKeyIsEvictedPastTrackedKeyCap() {
    AuthRateLimiter capped = new AuthRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, 1, clock);
    capped.check(IP);

    clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));
    capped.check(OTHER_IP);

    assertThat(capped.trackedKeyCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("should never evict a key that is still active within the window")
  void activeKeyIsNotEvictedPastTrackedKeyCap() {
    AuthRateLimiter capped = new AuthRateLimiter(MAX_REQUESTS, WINDOW_SECONDS, 1, clock);
    capped.check(IP);

    clock.advance(Duration.ofSeconds(1));
    capped.check(OTHER_IP);

    assertThat(capped.trackedKeyCount()).isEqualTo(2);
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
