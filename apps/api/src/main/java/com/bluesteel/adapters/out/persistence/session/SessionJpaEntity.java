package com.bluesteel.adapters.out.persistence.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sessions")
class SessionJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "sequence_number")
  private Integer sequenceNumber;

  @Column(name = "failure_reason")
  private String failureReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "diff_payload", columnDefinition = "jsonb")
  private String diffPayload;

  @Column(name = "committed_at")
  private Instant committedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SessionJpaEntity() {}

  SessionJpaEntity(
      UUID id,
      UUID campaignId,
      UUID ownerId,
      String status,
      Integer sequenceNumber,
      String failureReason,
      String diffPayload,
      Instant committedAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.ownerId = ownerId;
    this.status = status;
    this.sequenceNumber = sequenceNumber;
    this.failureReason = failureReason;
    this.diffPayload = diffPayload;
    this.committedAt = committedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  UUID getId() {
    return id;
  }

  UUID getCampaignId() {
    return campaignId;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  String getStatus() {
    return status;
  }

  Integer getSequenceNumber() {
    return sequenceNumber;
  }

  String getFailureReason() {
    return failureReason;
  }

  String getDiffPayload() {
    return diffPayload;
  }

  Instant getCommittedAt() {
    return committedAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
