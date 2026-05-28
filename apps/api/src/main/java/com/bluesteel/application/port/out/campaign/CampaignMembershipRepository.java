package com.bluesteel.application.port.out.campaign;

import com.bluesteel.domain.campaign.CampaignMember;

/** Persistence-layer contract for saving {@link CampaignMember} records. */
public interface CampaignMembershipRepository {

  void save(CampaignMember member);
}
