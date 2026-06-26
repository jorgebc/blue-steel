package com.bluesteel.application.model.campaign;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A single append-only entity version as carried in a {@link CampaignArchive} export (D-112). */
public record ArchivedEntityVersion(
    UUID versionId,
    int versionNumber,
    UUID sessionId,
    Map<String, Object> changedFields,
    Map<String, Object> fullSnapshot,
    Instant createdAt) {}
