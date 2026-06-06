package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.EntityLinks;
import java.util.List;
import java.util.UUID;

/**
 * REST view of an entity's profile cross-links (F4.7.2, D-009): the relations it participates in,
 * the entities at the other end, the events linked to it, and the sessions it appears in.
 */
public record EntityLinksResponse(
    List<RelationResponse> relations,
    List<EntitySummaryResponse> relatedEntities,
    List<TimelineEventResponse> events,
    List<UUID> appearanceSessionIds) {

  static EntityLinksResponse from(EntityLinks links) {
    return new EntityLinksResponse(
        links.relations().stream().map(RelationResponse::from).toList(),
        links.relatedEntities().stream().map(EntitySummaryResponse::from).toList(),
        links.events().stream().map(TimelineEventResponse::from).toList(),
        links.appearanceSessionIds());
  }
}
