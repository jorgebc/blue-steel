package com.bluesteel.application.model.campaign;

import java.util.UUID;

/** Command to create a new campaign — admin-only operation. */
public record CreateCampaignCommand(
    UUID callerId, boolean callerIsAdmin, String name, UUID gmUserId) {}
