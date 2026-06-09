package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryDailyCostAccountingAdapter")
class InMemoryDailyCostAccountingAdapterTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-06-09T12:00:00Z"));
  private final InMemoryDailyCostAccountingAdapter sut =
      new InMemoryDailyCostAccountingAdapter(clock);

  @Test
  @DisplayName("should start the day with a zero total")
  void startsAtZero() {
    assertThat(sut.currentDailyCostUsd()).isZero();
  }

  @Test
  @DisplayName("should accumulate the cost of recorded calls within the same day")
  void accumulatesWithinDay() {
    sut.record(0.0012);
    sut.record(0.0008);

    assertThat(sut.currentDailyCostUsd()).isEqualTo(0.0020);
  }

  @Test
  @DisplayName("should reset the running total at the start of a new UTC day")
  void resetsOnNewDay() {
    sut.record(0.5);
    assertThat(sut.currentDailyCostUsd()).isEqualTo(0.5);

    clock.advance(Duration.ofDays(1));

    assertThat(sut.currentDailyCostUsd()).isZero();
    sut.record(0.1);
    assertThat(sut.currentDailyCostUsd()).isEqualTo(0.1);
  }

  /**
   * Test clock whose instant can be advanced to exercise the UTC day rollover deterministically.
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
