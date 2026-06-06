package com.bluesteel.application.port.out.worldstate;

import com.bluesteel.application.model.worldstate.EntityLinks;
import java.util.UUID;

/** Native-SQL read of an actor's or space's cross-links for its profile (F4.7.1, D-009, D-095). */
public interface EntityLinksReadPort {

  /**
   * Returns the entity's relations, related entities, linked events, and distinct appearance
   * session ids. An unknown entity yields empty sections rather than null (read-only, D-010).
   *
   * @param entityType {@code "actor"} or {@code "space"}
   */
  EntityLinks getLinks(String entityType, UUID campaignId, UUID entityId);
}
