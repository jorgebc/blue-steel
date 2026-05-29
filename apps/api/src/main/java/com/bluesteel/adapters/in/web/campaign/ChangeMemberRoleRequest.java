package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/campaigns/{id}/members/{uid}}; only editor/player allowed.
 */
public record ChangeMemberRoleRequest(@NotNull CampaignRole role) {

  @AssertTrue(message = "Role must be editor or player")
  public boolean isAssignableRole() {
    return role != CampaignRole.GM;
  }
}
