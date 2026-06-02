package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** REST view of a world-state entity with its full ordered version history. */
public record EntityDetailResponse(
    UUID entityId,
    String entityType,
    String name,
    UUID ownerId,
    Instant createdAt,
    List<EntityVersionResponse> versions) {

  static EntityDetailResponse from(EntityDetailView v) {
    return new EntityDetailResponse(
        v.entityId(),
        v.entityType(),
        v.name(),
        v.ownerId(),
        v.createdAt(),
        v.versions().stream().map(EntityVersionResponse::from).toList());
  }
}
