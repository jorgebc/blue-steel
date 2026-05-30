package com.bluesteel.application.model.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import java.util.UUID;

/**
 * Diff card for a brand-new entity not previously in the world state; shows full profile (D-007).
 */
@JsonTypeName("NEW")
public record NewEntityCard(
    UUID cardId, String entityType, String name, Map<String, Object> fullProfile)
    implements DiffCard {}
