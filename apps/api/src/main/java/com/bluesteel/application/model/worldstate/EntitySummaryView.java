package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side summary of a world-state entity for list views: the head identity plus its latest
 * committed version (D-001). {@code currentSnapshot} is the freeform, entity-type-specific JSONB of
 * the most recent version.
 */
public record EntitySummaryView(
    UUID entityId,
    String entityType,
    String name,
    int latestVersionNumber,
    Map<String, Object> currentSnapshot,
    UUID lastUpdatedSessionId,
    Instant createdAt) {}
