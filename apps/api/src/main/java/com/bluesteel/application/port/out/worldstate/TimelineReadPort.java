package com.bluesteel.application.port.out.worldstate;

import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import java.util.UUID;

/**
 * Driven port for the keyset-paginated Timeline feed (D-055, F4.2): a campaign's events ordered by
 * their latest committed version's session sequence, across all committed sessions.
 */
public interface TimelineReadPort {

  /**
   * Returns one keyset page of the campaign's event feed. {@code cursor} is {@code null} for the
   * first page or the opaque {@code nextCursor} of the previous page; {@code limit} is the maximum
   * number of entries; {@code filter} narrows by event type, actor, or space (all optional).
   */
  TimelinePage page(UUID campaignId, String cursor, int limit, TimelineFilter filter);
}
