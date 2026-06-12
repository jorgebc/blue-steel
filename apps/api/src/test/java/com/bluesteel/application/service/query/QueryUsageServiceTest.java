package com.bluesteel.application.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.query.QueryUsage;
import com.bluesteel.application.port.out.cost.LlmCostAccountingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryUsageService")
class QueryUsageServiceTest {

  private static final double DAILY_CAP_USD = 1.00;

  @Mock private LlmCostAccountingPort costAccountingPort;

  @Test
  @DisplayName("should report the recorded daily spend against the configured cap")
  void currentUsage_returnsRecordedSpendAndCap() {
    when(costAccountingPort.currentDailyCostUsd()).thenReturn(0.42);
    QueryUsageService sut = new QueryUsageService(costAccountingPort, DAILY_CAP_USD);

    QueryUsage usage = sut.currentUsage();

    assertThat(usage.consumedUsd()).isEqualTo(0.42);
    assertThat(usage.capUsd()).isEqualTo(DAILY_CAP_USD);
  }

  @Test
  @DisplayName("should report zero consumed when no spend has been recorded today")
  void currentUsage_returnsZeroWhenNothingRecorded() {
    when(costAccountingPort.currentDailyCostUsd()).thenReturn(0.0);
    QueryUsageService sut = new QueryUsageService(costAccountingPort, DAILY_CAP_USD);

    QueryUsage usage = sut.currentUsage();

    assertThat(usage.consumedUsd()).isZero();
    assertThat(usage.capUsd()).isEqualTo(DAILY_CAP_USD);
  }
}
