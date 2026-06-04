package com.bluesteel.application.port.in.worldstate;

import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import java.util.UUID;

/**
 * Driving port for the keyset-paginated Timeline feed (D-055, F4.2), available to any campaign
 * member (D-043).
 */
public interface GetTimelineUseCase {

  /**
   * Returns one keyset page of the campaign's event feed for an authorized member. {@code cursor}
   * is {@code null} for the first page; {@code limit} is clamped to a sane range by the
   * implementation; {@code filter} optionally narrows by event type, actor, or space.
   */
  TimelinePage getTimeline(
      UUID campaignId, UUID callerId, String cursor, int limit, TimelineFilter filter);
}
