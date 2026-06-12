package com.bluesteel.application.service.query;

import com.bluesteel.application.model.query.QueryUsage;
import com.bluesteel.application.port.in.query.GetQueryUsageUseCase;
import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reports the shared daily LLM spend for Query Mode against the configured cap (D-096), so the UI
 * can show how much of the free tier's daily budget is left and prompt moderation. Reads the same
 * {@code query.cost-cap.daily-usd} cap the {@link QueryService} enforces.
 */
@Service
public class QueryUsageService implements GetQueryUsageUseCase {

  private final LlmCostAccountingPort costAccountingPort;
  private final double dailyCapUsd;

  public QueryUsageService(
      LlmCostAccountingPort costAccountingPort,
      @Value("${query.cost-cap.daily-usd:1.00}") double dailyCapUsd) {
    this.costAccountingPort = costAccountingPort;
    this.dailyCapUsd = dailyCapUsd;
  }

  @Override
  public QueryUsage currentUsage() {
    return new QueryUsage(costAccountingPort.currentDailyCostUsd(), dailyCapUsd);
  }
}
