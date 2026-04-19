package com.bluesteel.adapters.in.web.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/v1/users/me/password}. */
public record ChangePasswordRequest(
    @NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {}
