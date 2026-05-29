package com.bluesteel.application.model.campaign;

import java.util.UUID;

/** Command for the campaign-scoped member removal use case. */
public record RemoveMemberCommand(UUID campaignId, UUID callerId, UUID targetUserId) {}
