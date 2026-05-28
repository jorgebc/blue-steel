package com.bluesteel.adapters.out.persistence.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_members")
class CampaignMemberJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Stored as lowercase text matching the DB CHECK constraint (D-061). */
  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  protected CampaignMemberJpaEntity() {}

  CampaignMemberJpaEntity(UUID id, UUID campaignId, UUID userId, String role, Instant joinedAt) {
    this.id = id;
    this.campaignId = campaignId;
    this.userId = userId;
    this.role = role;
    this.joinedAt = joinedAt;
  }

  UUID getId() {
    return id;
  }

  UUID getCampaignId() {
    return campaignId;
  }

  UUID getUserId() {
    return userId;
  }

  String getRole() {
    return role;
  }

  Instant getJoinedAt() {
    return joinedAt;
  }
}
