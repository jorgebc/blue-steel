package com.bluesteel.application.model.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import java.time.Instant;
import java.util.UUID;

/** Read model for one member of a campaign roster (ARCHITECTURE.md §7.8). */
public record CampaignMemberView(UUID userId, String email, CampaignRole role, Instant joinedAt) {}
