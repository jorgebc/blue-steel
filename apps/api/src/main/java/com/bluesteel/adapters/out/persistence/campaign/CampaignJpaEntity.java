package com.bluesteel.adapters.out.persistence.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
class CampaignJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CampaignJpaEntity() {}

  CampaignJpaEntity(UUID id, String name, UUID createdBy, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  String getName() {
    return name;
  }

  UUID getCreatedBy() {
    return createdBy;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
