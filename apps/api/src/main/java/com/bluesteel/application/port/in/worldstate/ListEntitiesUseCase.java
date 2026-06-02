package com.bluesteel.application.port.in.worldstate;

import com.bluesteel.application.model.worldstate.EntityListPage;
import java.util.UUID;

/**
 * Driving port for listing a campaign's world-state entities of a given type (offset-paginated) for
 * any campaign member.
 */
public interface ListEntitiesUseCase {

  /**
   * Returns the requested page of entities, each carrying its latest committed version. {@code
   * entityType} selects the entity family (actor, space, event); {@code page} is zero-based and
   * {@code size} is clamped to a sane range by the implementation.
   */
  EntityListPage list(String entityType, UUID campaignId, UUID callerId, int page, int size);
}
