package com.bluesteel.application.model.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import java.util.UUID;

/** Command for the campaign-scoped member role-change use case. */
public record ChangeMemberRoleCommand(
    UUID campaignId, UUID callerId, UUID targetUserId, CampaignRole newRole) {}
