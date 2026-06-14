package com.bluesteel.adapters.out.persistence.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "proposals")
class ProposalJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;

  @Column(name = "target_entity_type", nullable = false)
  private String targetEntityType;

  @Column(name = "target_entity_id", nullable = false)
  private UUID targetEntityId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proposed_delta", columnDefinition = "jsonb", nullable = false)
  private String proposedDelta;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "session_id")
  private UUID sessionId;

  @Column(name = "resulting_entity_version_id")
  private UUID resultingEntityVersionId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ProposalJpaEntity() {}

  ProposalJpaEntity(
      UUID id,
      UUID campaignId,
      String targetEntityType,
      UUID targetEntityId,
      UUID authorId,
      String proposedDelta,
      String status,
      UUID sessionId,
      UUID resultingEntityVersionId,
      Instant expiresAt,
      Instant createdAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.targetEntityType = targetEntityType;
    this.targetEntityId = targetEntityId;
    this.authorId = authorId;
    this.proposedDelta = proposedDelta;
    this.status = status;
    this.sessionId = sessionId;
    this.resultingEntityVersionId = resultingEntityVersionId;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getCampaignId() {
    return campaignId;
  }

  String getTargetEntityType() {
    return targetEntityType;
  }

  UUID getTargetEntityId() {
    return targetEntityId;
  }

  UUID getAuthorId() {
    return authorId;
  }

  String getProposedDelta() {
    return proposedDelta;
  }

  String getStatus() {
    return status;
  }

  UUID getSessionId() {
    return sessionId;
  }

  UUID getResultingEntityVersionId() {
    return resultingEntityVersionId;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
