package com.bluesteel.application.model.worldstate;

import java.util.Map;
import java.util.UUID;

/**
 * Command carrying everything the world-state write needs to insert or update an entity. {@code
 * existingEntityId} null means a brand-new head row is created; non-null means a new version is
 * appended to the existing head.
 */
public record EntityWriteCommand(
    String entityType,
    UUID existingEntityId,
    UUID campaignId,
    UUID ownerId,
    String name,
    Map<String, Object> changedFields,
    Map<String, Object> fullSnapshot,
    UUID sessionId) {}
