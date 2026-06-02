package com.bluesteel.application.port.in.worldstate;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import java.util.UUID;

/**
 * Driving port for reading a single world-state entity with its full version history, for any
 * campaign member.
 */
public interface GetEntityDetailUseCase {

  /**
   * Returns the entity's identity plus its full ordered version history. Throws if the caller is
   * not a campaign member or the entity does not exist in the campaign.
   */
  EntityDetailView getDetail(String entityType, UUID campaignId, UUID entityId, UUID callerId);
}
