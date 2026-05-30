package com.bluesteel.config;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Structured cost logger for every real LLM call. {@code session_id} and {@code user_id} are set in
 * MDC by the calling pipeline service before invoking this logger (LOG-01, D-034).
 */
@Component
public class LlmCostLogger {

  private static final Logger log = LoggerFactory.getLogger(LlmCostLogger.class);

  /**
   * Emits one structured INFO log line for an LLM call. MDC must contain {@code session_id} and
   * {@code user_id} set by the caller.
   */
  public void logLlmCall(String stage, int tokensIn, int tokensOut, Instant start) {
    log.atInfo()
        .addKeyValue("stage", stage)
        .addKeyValue("tokens_in", tokensIn)
        .addKeyValue("tokens_out", tokensOut)
        .addKeyValue("cost_usd", estimateCostUsd(tokensIn, tokensOut))
        .addKeyValue("duration_ms", Duration.between(start, Instant.now()).toMillis())
        .log("LLM call completed: {}", stage);
  }

  private double estimateCostUsd(int tokensIn, int tokensOut) {
    // gemini-2.5-flash pricing: ~$0.30/MTok input, ~$2.50/MTok output (update when pricing changes)
    return (tokensIn * 0.30 + tokensOut * 2.50) / 1_000_000.0;
  }
}
