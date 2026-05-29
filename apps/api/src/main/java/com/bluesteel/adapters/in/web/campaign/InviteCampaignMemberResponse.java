package com.bluesteel.adapters.in.web.campaign;

/** Response body for {@code POST /api/v1/campaigns/{id}/invitations}. */
public record InviteCampaignMemberResponse(String email, String role, boolean created) {}
