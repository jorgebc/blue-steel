package com.bluesteel.adapters.in.web.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/invitations}. */
public record InvitePlatformUserRequest(@NotBlank @Email String email) {}
