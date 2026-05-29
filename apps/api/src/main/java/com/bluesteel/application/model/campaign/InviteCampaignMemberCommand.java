package com.bluesteel.application.model.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import java.util.UUID;

/** Command for the campaign-scoped member invitation use case. */
public record InviteCampaignMemberCommand(
    UUID campaignId, UUID callerId, String email, CampaignRole role) {}
