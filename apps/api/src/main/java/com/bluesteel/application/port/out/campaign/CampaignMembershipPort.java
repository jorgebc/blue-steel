package com.bluesteel.application.port.out.campaign;

import com.bluesteel.domain.campaign.CampaignRole;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical authorization check for campaign-scoped operations. Resolves the role from the database
 * on every call — never from the JWT (D-043).
 */
public interface CampaignMembershipPort {

  /** Returns the caller's role in the campaign, or empty if they are not a member. */
  Optional<CampaignRole> resolveRole(UUID campaignId, UUID userId);
}
