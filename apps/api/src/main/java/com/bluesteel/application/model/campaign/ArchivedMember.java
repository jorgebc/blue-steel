package com.bluesteel.application.model.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import java.time.Instant;
import java.util.UUID;

/** A campaign member as carried in a {@link CampaignArchive} export (D-112). */
public record ArchivedMember(UUID userId, CampaignRole role, Instant joinedAt) {}
