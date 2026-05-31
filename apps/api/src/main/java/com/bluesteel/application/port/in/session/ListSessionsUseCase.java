package com.bluesteel.application.port.in.session;

import com.bluesteel.application.model.session.SessionListView;
import java.util.UUID;

/** Driving port for listing a campaign's sessions (offset-paginated) for any campaign member. */
public interface ListSessionsUseCase {

  /**
   * Returns the requested page of sessions for the campaign, ordered by sequence number (committed
   * sessions first, in order) then creation time. {@code page} is zero-based; {@code size} is
   * clamped to a sane range by the implementation.
   */
  SessionListView list(UUID campaignId, UUID callerId, int page, int size);
}
