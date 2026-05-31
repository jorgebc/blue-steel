package com.bluesteel.application.port.out.campaign;

import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence-layer contract for reading, saving, and removing {@link CampaignMember} records. */
public interface CampaignMembershipRepository {

  void save(CampaignMember member);

  Optional<CampaignMember> findByCampaignIdAndUserId(UUID campaignId, UUID userId);

  /** Returns every membership for the campaign, ordered by join time. */
  List<CampaignMember> findByCampaignId(UUID campaignId);

  void deleteByCampaignIdAndUserId(UUID campaignId, UUID userId);

  boolean existsByUserIdAndRole(UUID userId, CampaignRole role);
}
