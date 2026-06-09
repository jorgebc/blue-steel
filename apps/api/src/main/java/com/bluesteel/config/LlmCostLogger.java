package com.bluesteel.config;

import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Structured cost logger for every real LLM call. {@code session_id} and {@code user_id} are set in
 * MDC by the calling pipeline service before invoking this logger (LOG-01, D-034). Each call's
 * estimated cost is also accrued to the daily cost tally backing the Query Mode cost cap (D-096).
 */
@Component
public class LlmCostLogger {

  private static final Logger log = LoggerFactory.getLogger(LlmCostLogger.class);

  private final LlmCostAccountingPort costAccounting;

  public LlmCostLogger(LlmCostAccountingPort costAccounting) {
    this.costAccounting = costAccounting;
  }

  /**
   * Emits one structured INFO log line for an LLM call and accrues its cost to the daily tally. MDC
   * must contain {@code session_id} and {@code user_id} set by the caller.
   */
  public void logLlmCall(String stage, int tokensIn, int tokensOut, Instant start) {
    double costUsd = estimateCostUsd(tokensIn, tokensOut);
    log.atInfo()
        .addKeyValue("stage", stage)
        .addKeyValue("tokens_in", tokensIn)
        .addKeyValue("tokens_out", tokensOut)
        .addKeyValue("cost_usd", costUsd)
        .addKeyValue("duration_ms", Duration.between(start, Instant.now()).toMillis())
        .log("LLM call completed: {}", stage);
    costAccounting.record(costUsd);
  }

  private double estimateCostUsd(int tokensIn, int tokensOut) {
    // gemini-2.5-flash pricing: ~$0.30/MTok input, ~$2.50/MTok output (update when pricing changes)
    return (tokensIn * 0.30 + tokensOut * 2.50) / 1_000_000.0;
  }
}
