package com.bluesteel.application.model.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a campaign, including the caller's role. Role is {@code null} when an admin lists
 * a campaign they are not a member of.
 */
public record CampaignView(
    UUID id,
    String name,
    UUID createdBy,
    Instant createdAt,
    String contentLanguage,
    CampaignRole role) {}
