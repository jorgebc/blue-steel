package com.bluesteel.adapters.in.web.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for POST /api/v1/campaigns. */
public record CreateCampaignRequest(@NotBlank String name, @NotNull UUID gmUserId) {}
