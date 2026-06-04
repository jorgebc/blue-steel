package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.RelationDetailView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** REST view of a relation with its graph endpoints and full ordered version history (F4.3.6). */
public record RelationDetailResponse(
    UUID relationId,
    String name,
    String kind,
    UUID sourceEntityId,
    String sourceEntityType,
    UUID targetEntityId,
    String targetEntityType,
    UUID ownerId,
    Instant createdAt,
    List<EntityVersionResponse> versions) {

  static RelationDetailResponse from(RelationDetailView v) {
    return new RelationDetailResponse(
        v.relationId(),
        v.name(),
        v.kind(),
        v.sourceEntityId(),
        v.sourceEntityType(),
        v.targetEntityId(),
        v.targetEntityType(),
        v.ownerId(),
        v.createdAt(),
        v.versions().stream().map(EntityVersionResponse::from).toList());
  }
}
