package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.CampaignMemberView;
import java.util.List;
import java.util.UUID;

/** Driving port for reading a campaign's member roster (member-or-admin access, D-043). */
public interface ListCampaignMembersUseCase {

  /** Returns the campaign's members; the caller must be a member or the platform admin. */
  List<CampaignMemberView> list(UUID campaignId, UUID callerId, boolean callerIsAdmin);
}
