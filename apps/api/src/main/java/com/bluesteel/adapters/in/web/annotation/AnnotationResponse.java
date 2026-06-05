package com.bluesteel.adapters.in.web.annotation;

import com.bluesteel.application.model.annotation.AnnotationView;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single annotation resource. */
public record AnnotationResponse(
    UUID id, String entityType, UUID entityId, UUID authorId, String content, Instant createdAt) {

  public static AnnotationResponse from(AnnotationView view) {
    return new AnnotationResponse(
        view.id(),
        view.entityType(),
        view.entityId(),
        view.authorId(),
        view.content(),
        view.createdAt());
  }
}
