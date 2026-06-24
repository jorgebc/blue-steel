package com.bluesteel.adapters.in.web.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Request body for POST /api/v1/campaigns. {@code contentLanguage} is optional in shape (omitting
 * it defaults to {@code en}); when supplied it must be {@code en} or {@code es} (immutable, D-103).
 * The {@code @Pattern} skips null, so a bad value yields {@code 400} via the validation handler.
 */
public record CreateCampaignRequest(
    @NotBlank String name,
    @NotNull UUID gmUserId,
    @Pattern(regexp = "^(en|es)$") String contentLanguage) {}
