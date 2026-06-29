package com.bluesteel.adapters.in.web.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/v1/users/me/password}. */
public record ChangePasswordRequest(
    // currentPassword is also BCrypt-checked; max 128 caps the input to prevent BCrypt DoS
    @NotBlank @Size(max = 128) String currentPassword,
    // 12-character minimum per NIST SP 800-63B; max 128 to prevent BCrypt DoS on very long inputs
    @NotBlank @Size(min = 12, max = 128) String newPassword) {}
