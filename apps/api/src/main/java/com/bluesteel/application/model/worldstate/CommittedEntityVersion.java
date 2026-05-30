package com.bluesteel.application.model.worldstate;

import java.util.UUID;

/**
 * Returned by {@link com.bluesteel.application.port.out.worldstate.WorldStatePort#writeEntity}
 * after a successful write. Carried by {@code SessionCommittedEvent} so the async listener can
 * embed without re-reading the DB (D-063).
 */
public record CommittedEntityVersion(
    String entityType,
    UUID entityId,
    UUID entityVersionId,
    int versionNumber,
    String contentToEmbed,
    String contentHash) {}
