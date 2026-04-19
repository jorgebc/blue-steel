package com.bluesteel.adapters.in.web.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Response body for {@code GET /api/v1/users/me}. */
public record UserMeResponse(
    UUID id,
    String email,
    @JsonProperty("isAdmin") boolean isAdmin,
    @JsonProperty("forcePasswordChange") boolean forcePasswordChange) {}
