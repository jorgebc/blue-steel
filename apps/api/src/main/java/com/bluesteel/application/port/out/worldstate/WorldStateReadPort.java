package com.bluesteel.application.port.out.worldstate;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListFilter;
import com.bluesteel.application.model.worldstate.EntityListPage;
import java.util.UUID;

/**
 * Driven port for generic, read-only world-state queries over the head/version table pairs (D-089).
 * Reads are scoped to a campaign; the {@code entityType} selects the table pair (actor, space,
 * event, relation).
 */
public interface WorldStateReadPort {

  /**
   * Returns one offset-paginated page of the campaign's entities of the given type, each carrying
   * its latest committed version, plus the total count across all pages (D-055).
   */
  EntityListPage list(
      String entityType, UUID campaignId, EntityListFilter filter, int page, int size);

  /**
   * Returns the entity's head identity plus its full ordered version history, or {@code null} if no
   * such entity exists in the campaign.
   */
  EntityDetailView getWithHistory(String entityType, UUID campaignId, UUID entityId);
}
