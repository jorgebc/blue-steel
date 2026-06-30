package com.bluesteel.adapters.in.web.proposal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for submitting a proposal. {@code targetType} is validated against the {@code
 * actor}/{@code space} scope in the service (D-108); an empty {@code proposedDelta} is rejected as
 * a business rule (422 EMPTY_DELTA, D-104), so only its presence is enforced here.
 */
public record CreateProposalRequest(
    @NotBlank @Size(max = 64) String targetType,
    @NotNull UUID targetId,
    @NotNull UUID sessionId,
    @NotNull Map<String, Object> proposedDelta) {}
