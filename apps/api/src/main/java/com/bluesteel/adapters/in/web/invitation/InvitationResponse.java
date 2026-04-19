package com.bluesteel.adapters.in.web.invitation;

/** Response body for {@code POST /api/v1/invitations}. */
public record InvitationResponse(String email, String status) {}
