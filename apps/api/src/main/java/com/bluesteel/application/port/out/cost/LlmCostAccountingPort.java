package com.bluesteel.application.port.out.cost;

/**
 * Accumulates the estimated USD cost of LLM calls into a daily running total used by the Query Mode
 * cost cap (D-096). Fed from the LOG-01 cost logging on every real LLM call; the total resets at
 * the start of each UTC day.
 */
public interface LlmCostAccountingPort {

  /** Adds the estimated cost of one LLM call to the current day's total. */
  void record(double costUsd);

  /** Returns the estimated LLM cost accrued so far in the current UTC day. */
  double currentDailyCostUsd();
}
