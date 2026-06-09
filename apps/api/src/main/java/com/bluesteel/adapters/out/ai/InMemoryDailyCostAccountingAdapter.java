package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * In-memory daily LLM cost tally (D-096). Holds a single running total for the current UTC day and
 * resets it on day rollover. Bounded to O(1) memory — suitable for the single-instance Render
 * free-tier deployment; a multi-instance deployment would need a shared store.
 */
@Component
public class InMemoryDailyCostAccountingAdapter implements LlmCostAccountingPort {

  private final Clock clock;
  private LocalDate currentDay;
  private double totalUsd;

  public InMemoryDailyCostAccountingAdapter() {
    this(Clock.systemUTC());
  }

  InMemoryDailyCostAccountingAdapter(Clock clock) {
    this.clock = clock;
    this.currentDay = today();
  }

  @Override
  public synchronized void record(double costUsd) {
    rollIfNewDay();
    totalUsd += costUsd;
  }

  @Override
  public synchronized double currentDailyCostUsd() {
    rollIfNewDay();
    return totalUsd;
  }

  private void rollIfNewDay() {
    LocalDate today = today();
    if (!today.equals(currentDay)) {
      currentDay = today;
      totalUsd = 0.0;
    }
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
