package com.bluesteel.application.model.campaign;

import java.time.Instant;
import java.util.UUID;

/** A user annotation as carried in a {@link CampaignArchive} export (D-112). */
public record ArchivedAnnotation(
    UUID id, String entityType, UUID entityId, UUID authorId, String content, Instant createdAt) {}
