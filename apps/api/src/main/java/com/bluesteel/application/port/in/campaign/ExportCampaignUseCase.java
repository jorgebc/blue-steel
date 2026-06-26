package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.CampaignArchive;
import java.util.UUID;

/** Assembles a campaign's complete dataset into a portable archive, for GM or admin (D-112). */
public interface ExportCampaignUseCase {

  /**
   * Authorizes the caller (admin, or GM resolved via DB per D-043), enforces the entity cap, and
   * returns the assembled {@link CampaignArchive}.
   */
  CampaignArchive export(UUID campaignId, UUID callerId, boolean callerIsAdmin);
}
