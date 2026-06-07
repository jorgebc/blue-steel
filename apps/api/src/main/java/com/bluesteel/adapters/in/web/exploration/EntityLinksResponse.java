package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.adapters.in.web.session.SessionSummaryResponse;
import com.bluesteel.application.model.worldstate.EntityLinks;
import java.util.List;

/**
 * REST view of an entity's profile cross-links (F4.7.2, D-009): the relations it participates in,
 * the entities at the other end, the events linked to it, and the sessions it appears in.
 * Appearances carry the session summary (sequence number, status) for labelling and deep-linking
 * (F4.8.1).
 */
public record EntityLinksResponse(
    List<RelationResponse> relations,
    List<EntitySummaryResponse> relatedEntities,
    List<TimelineEventResponse> events,
    List<SessionSummaryResponse> appearances) {

  static EntityLinksResponse from(EntityLinks links) {
    return new EntityLinksResponse(
        links.relations().stream().map(RelationResponse::from).toList(),
        links.relatedEntities().stream().map(EntitySummaryResponse::from).toList(),
        links.events().stream().map(TimelineEventResponse::from).toList(),
        links.appearances().stream()
            .map(
                a ->
                    new SessionSummaryResponse(
                        a.sessionId(),
                        a.status(),
                        a.sequenceNumber(),
                        a.committedAt(),
                        a.createdAt()))
            .toList());
  }
}
