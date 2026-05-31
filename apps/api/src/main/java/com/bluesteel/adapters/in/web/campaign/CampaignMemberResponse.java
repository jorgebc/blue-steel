package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.application.model.campaign.CampaignMemberView;
import java.time.Instant;
import java.util.UUID;

/** Response body for one row of the campaign member roster (ARCHITECTURE.md §7.8). */
public record CampaignMemberResponse(UUID userId, String email, String role, Instant joinedAt) {

  static CampaignMemberResponse from(CampaignMemberView view) {
    return new CampaignMemberResponse(
        view.userId(), view.email(), view.role().name().toLowerCase(), view.joinedAt());
  }
}
