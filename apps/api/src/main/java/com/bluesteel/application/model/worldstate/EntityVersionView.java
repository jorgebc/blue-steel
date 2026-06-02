package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side view of a single append-only entity version (D-001, D-003). {@code
 * sessionSequenceNumber} is the originating session's committed ordinal, joined from {@code
 * sessions} — it is {@code null} when the session has no assigned sequence number yet.
 */
public record EntityVersionView(
    UUID versionId,
    int versionNumber,
    UUID sessionId,
    Integer sessionSequenceNumber,
    Map<String, Object> changedFields,
    Map<String, Object> fullSnapshot,
    Instant createdAt) {}
