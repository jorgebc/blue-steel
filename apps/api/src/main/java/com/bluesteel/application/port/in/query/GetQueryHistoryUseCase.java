package com.bluesteel.application.port.in.query;

import com.bluesteel.application.model.query.QueryHistoryView;
import java.util.UUID;

/** Driving port for reading a campaign's Q&amp;A history for any campaign member (D-058). */
public interface GetQueryHistoryUseCase {

  /**
   * Returns one page of the campaign's logged Q&amp;A entries, newest first, using zero-based
   * offset pagination. {@code size} is clamped to a sane range by the implementation.
   */
  QueryHistoryView getHistory(UUID campaignId, UUID callerId, int page, int size);
}
