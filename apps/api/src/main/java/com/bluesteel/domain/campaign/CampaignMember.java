package com.bluesteel.domain.campaign;

import java.time.Instant;
import java.util.UUID;

/** Campaign membership value object linking a user to a campaign with a specific role. */
public class CampaignMember {

  private final UUID id;
  private final UUID campaignId;
  private final UUID userId;
  private final CampaignRole role;
  private final Instant joinedAt;

  private CampaignMember(
      UUID id, UUID campaignId, UUID userId, CampaignRole role, Instant joinedAt) {
    if (role == null) throw new IllegalArgumentException("Role must not be null");
    this.id = id;
    this.campaignId = campaignId;
    this.userId = userId;
    this.role = role;
    this.joinedAt = joinedAt;
  }

  public static CampaignMember create(
      UUID id, UUID campaignId, UUID userId, CampaignRole role, Instant joinedAt) {
    return new CampaignMember(id, campaignId, userId, role, joinedAt);
  }

  public UUID id() {
    return id;
  }

  public UUID campaignId() {
    return campaignId;
  }

  public UUID userId() {
    return userId;
  }

  public CampaignRole role() {
    return role;
  }

  public Instant joinedAt() {
    return joinedAt;
  }
}
