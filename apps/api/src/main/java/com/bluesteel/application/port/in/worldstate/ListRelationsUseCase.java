package com.bluesteel.application.port.in.worldstate;

import com.bluesteel.application.model.worldstate.RelationSummaryView;
import java.util.List;
import java.util.UUID;

/**
 * Driving port for listing a campaign's relations with their graph endpoints for any campaign
 * member (F4.3.6, D-030).
 */
public interface ListRelationsUseCase {

  /**
   * Returns all relations in the campaign. When {@code actorFilter} is non-null, only relations
   * touching that entity (on either endpoint) are returned.
   */
  List<RelationSummaryView> list(UUID campaignId, UUID callerId, UUID actorFilter);
}
