package com.bluesteel.application.port.in.worldstate;

import com.bluesteel.application.model.worldstate.EntityLinks;
import java.util.UUID;

/** Member-authorized read of an actor's or space's profile cross-links (F4.7.2, D-009, D-010). */
public interface GetEntityLinksUseCase {

  /**
   * @param entityType {@code "actor"} or {@code "space"}
   */
  EntityLinks getLinks(String entityType, UUID campaignId, UUID entityId, UUID callerId);
}
