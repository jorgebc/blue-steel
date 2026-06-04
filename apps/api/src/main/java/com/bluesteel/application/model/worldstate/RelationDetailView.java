package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side detail of a relation: its identity and graph endpoints plus the full ordered version
 * history (F4.3.5, D-001, D-030). Mirrors {@link EntityDetailView} but adds the structured
 * endpoints and {@code kind} the generic entity detail does not carry.
 */
public record RelationDetailView(
    UUID relationId,
    String name,
    String kind,
    UUID sourceEntityId,
    String sourceEntityType,
    UUID targetEntityId,
    String targetEntityType,
    UUID ownerId,
    Instant createdAt,
    List<EntityVersionView> versions) {}
