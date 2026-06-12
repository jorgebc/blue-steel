package com.bluesteel.application.port.in.query;

import com.bluesteel.application.model.query.QueryUsage;

/**
 * Driving port exposing the current shared daily LLM spend for Query Mode against its cap (D-096).
 */
public interface GetQueryUsageUseCase {

  /** Returns today's recorded LLM spend and the configured daily cap. */
  QueryUsage currentUsage();
}
