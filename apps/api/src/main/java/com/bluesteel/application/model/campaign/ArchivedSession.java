package com.bluesteel.application.model.campaign;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable session metadata as carried in a {@link CampaignArchive} export (D-112). The transient
 * {@code diff_payload} is intentionally omitted — it is cleared on commit/discard.
 */
public record ArchivedSession(
    UUID id,
    UUID ownerId,
    Integer sequenceNumber,
    String status,
    Instant committedAt,
    Instant createdAt) {}
