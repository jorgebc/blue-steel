package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.RelationSummaryView;
import java.util.UUID;

/**
 * REST view of a relation summary for the graph (F4.3.6, D-030). {@code sourceEntityId}/{@code
 * targetEntityId} are null when the endpoint could not be resolved at commit.
 */
public record RelationResponse(
    UUID relationId,
    String name,
    String kind,
    UUID sourceEntityId,
    String sourceEntityType,
    UUID targetEntityId,
    String targetEntityType,
    UUID sessionId) {

  static RelationResponse from(RelationSummaryView v) {
    return new RelationResponse(
        v.relationId(),
        v.name(),
        v.kind(),
        v.sourceEntityId(),
        v.sourceEntityType(),
        v.targetEntityId(),
        v.targetEntityType(),
        v.sessionId());
  }
}
