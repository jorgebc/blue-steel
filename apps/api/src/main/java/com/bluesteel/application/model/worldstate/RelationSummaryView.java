package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side summary of a relation for the Relations graph (F4.3.5, D-030). Carries the structured
 * graph endpoints (nullable when unresolved) plus the relation {@code kind} read from the latest
 * version snapshot (nullable; the graph edge label falls back to {@code name}).
 */
public record RelationSummaryView(
    UUID relationId,
    String name,
    String kind,
    UUID sourceEntityId,
    String sourceEntityType,
    UUID targetEntityId,
    String targetEntityType,
    UUID sessionId,
    Instant createdAt) {}
