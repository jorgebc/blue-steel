package com.bluesteel.domain.annotation;

import com.bluesteel.domain.exception.DomainException;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable, non-canonical note attached to any world-state entity by any campaign member
 * (D-011). Once created it cannot be edited — only deleted by its author or the campaign GM.
 */
public final class Annotation {

  private final UUID id;
  private final UUID campaignId;
  private final UUID entityId;
  private final AnnotationEntityType entityType;
  private final UUID authorId;
  private final String content;
  private final Instant createdAt;

  private Annotation(
      UUID id,
      UUID campaignId,
      UUID entityId,
      AnnotationEntityType entityType,
      UUID authorId,
      String content,
      Instant createdAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.entityId = entityId;
    this.entityType = entityType;
    this.authorId = authorId;
    this.content = content;
    this.createdAt = createdAt;
  }

  /**
   * Creates a new annotation, enforcing that content is non-blank and entityType is non-null.
   *
   * @throws DomainException if content is blank or entityType is null
   */
  public static Annotation create(
      UUID id,
      UUID campaignId,
      UUID entityId,
      AnnotationEntityType entityType,
      UUID authorId,
      String content,
      Instant createdAt) {
    if (content == null || content.isBlank()) {
      throw new DomainException("Annotation content must not be blank");
    }
    if (entityType == null) {
      throw new DomainException("Annotation entityType must not be null");
    }
    return new Annotation(id, campaignId, entityId, entityType, authorId, content, createdAt);
  }

  public UUID id() {
    return id;
  }

  public UUID campaignId() {
    return campaignId;
  }

  public UUID entityId() {
    return entityId;
  }

  public AnnotationEntityType entityType() {
    return entityType;
  }

  public UUID authorId() {
    return authorId;
  }

  public String content() {
    return content;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
