package com.bluesteel.application.model.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import java.util.UUID;

/** Diff card for an entity that already exists in the world state; shows delta only (D-006). */
@JsonTypeName("EXISTING")
public record ExistingEntityCard(
    UUID cardId, UUID entityId, String entityType, String name, Map<String, Object> changedFields)
    implements DiffCard {}
