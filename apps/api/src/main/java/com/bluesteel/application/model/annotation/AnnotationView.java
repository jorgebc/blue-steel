package com.bluesteel.application.model.annotation;

import java.time.Instant;
import java.util.UUID;

/** Read-only projection of a persisted annotation returned by query use cases. */
public record AnnotationView(
    UUID id,
    UUID campaignId,
    UUID entityId,
    String entityType,
    UUID authorId,
    String content,
    Instant createdAt) {}
