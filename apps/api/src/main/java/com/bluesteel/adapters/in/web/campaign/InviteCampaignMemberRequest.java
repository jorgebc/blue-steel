package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/campaigns/{id}/invitations}; only editor/player invitable.
 */
public record InviteCampaignMemberRequest(
    @NotBlank @Email String email, @NotNull CampaignRole role) {

  @AssertTrue(message = "Role must be editor or player")
  public boolean isInvitableRole() {
    return role != CampaignRole.GM;
  }
}
