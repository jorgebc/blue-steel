package com.bluesteel.adapters.out.persistence.annotation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "annotations")
class AnnotationJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AnnotationJpaEntity() {}

  AnnotationJpaEntity(
      UUID id,
      UUID campaignId,
      String entityType,
      UUID entityId,
      UUID authorId,
      String content,
      Instant createdAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.authorId = authorId;
    this.content = content;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getCampaignId() {
    return campaignId;
  }

  String getEntityType() {
    return entityType;
  }

  UUID getEntityId() {
    return entityId;
  }

  UUID getAuthorId() {
    return authorId;
  }

  String getContent() {
    return content;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
