package com.bluesteel.application.model.annotation;

import java.util.UUID;

/** Command to create a new annotation on a world-state entity. */
public record CreateAnnotationCommand(
    UUID campaignId, UUID entityId, String entityType, String content, UUID authorId) {}
