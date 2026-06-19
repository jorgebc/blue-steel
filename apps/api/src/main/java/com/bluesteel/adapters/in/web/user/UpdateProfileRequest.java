package com.bluesteel.adapters.in.web.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/v1/users/me}. All fields are optional in shape; supplied
 * values must satisfy the format constraints ({@code @Pattern}/{@code @Size} skip nulls), yielding
 * {@code 400} on a bad value via the standard validation handler.
 */
public record UpdateProfileRequest(
    @Size(max = 100) String displayName,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String avatarAccentColor,
    @Pattern(regexp = "^(en|es)$") String uiLocale,
    @Pattern(regexp = "^(light|dark|system)$") String theme) {}
