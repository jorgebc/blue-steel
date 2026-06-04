package com.bluesteel.application.model.worldstate;

import java.util.Map;
import java.util.UUID;

/**
 * Command carrying everything the world-state write needs to insert or update an entity. {@code
 * existingEntityId} null means a brand-new head row is created; non-null means a new version is
 * appended to the existing head.
 *
 * <p>The four {@code source*}/{@code target*} fields carry a relation's resolved graph endpoints
 * (F4.3.4, D-095); they are null for non-relation entities and for relations whose endpoints could
 * not be name-matched.
 */
public record EntityWriteCommand(
    String entityType,
    UUID existingEntityId,
    UUID campaignId,
    UUID ownerId,
    String name,
    Map<String, Object> changedFields,
    Map<String, Object> fullSnapshot,
    UUID sessionId,
    UUID sourceEntityId,
    String sourceEntityType,
    UUID targetEntityId,
    String targetEntityType) {

  /** Convenience constructor for non-relation writes, leaving all graph endpoints null. */
  public EntityWriteCommand(
      String entityType,
      UUID existingEntityId,
      UUID campaignId,
      UUID ownerId,
      String name,
      Map<String, Object> changedFields,
      Map<String, Object> fullSnapshot,
      UUID sessionId) {
    this(
        entityType,
        existingEntityId,
        campaignId,
        ownerId,
        name,
        changedFields,
        fullSnapshot,
        sessionId,
        null,
        null,
        null,
        null);
  }

  /** Returns a copy of this command with the given relation graph endpoints set. */
  public EntityWriteCommand withEndpoints(
      UUID sourceEntityId, String sourceEntityType, UUID targetEntityId, String targetEntityType) {
    return new EntityWriteCommand(
        entityType,
        existingEntityId,
        campaignId,
        ownerId,
        name,
        changedFields,
        fullSnapshot,
        sessionId,
        sourceEntityId,
        sourceEntityType,
        targetEntityId,
        targetEntityType);
  }
}
