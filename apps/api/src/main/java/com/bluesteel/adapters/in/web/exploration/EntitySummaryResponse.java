package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.EntitySummaryView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** REST view of a world-state entity summary for list responses. */
public record EntitySummaryResponse(
    UUID entityId,
    String entityType,
    String name,
    int latestVersionNumber,
    Map<String, Object> currentSnapshot,
    UUID lastUpdatedSessionId,
    Instant createdAt) {

  static EntitySummaryResponse from(EntitySummaryView v) {
    return new EntitySummaryResponse(
        v.entityId(),
        v.entityType(),
        v.name(),
        v.latestVersionNumber(),
        v.currentSnapshot(),
        v.lastUpdatedSessionId(),
        v.createdAt());
  }
}
