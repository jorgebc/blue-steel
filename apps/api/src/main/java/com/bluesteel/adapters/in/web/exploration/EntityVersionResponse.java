package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.EntityVersionView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** REST view of a single world-state entity version (D-001, D-003). */
public record EntityVersionResponse(
    UUID versionId,
    int versionNumber,
    UUID sessionId,
    Integer sessionSequenceNumber,
    Map<String, Object> changedFields,
    Map<String, Object> fullSnapshot,
    Instant createdAt) {

  static EntityVersionResponse from(EntityVersionView v) {
    return new EntityVersionResponse(
        v.versionId(),
        v.versionNumber(),
        v.sessionId(),
        v.sessionSequenceNumber(),
        v.changedFields(),
        v.fullSnapshot(),
        v.createdAt());
  }
}
