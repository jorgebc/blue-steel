package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side detail of a world-state entity: the head identity plus its full ordered version history
 * (D-001, D-003). {@code versions} is ordered by ascending version number.
 */
public record EntityDetailView(
    UUID entityId,
    String entityType,
    String name,
    UUID ownerId,
    Instant createdAt,
    List<EntityVersionView> versions) {}
