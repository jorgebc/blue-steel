package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import java.util.UUID;

/** Retrieves a single campaign with the caller's role (D-043). */
public interface GetCampaignUseCase {

  CampaignView get(UUID campaignId, UUID callerId, boolean callerIsAdmin);
}
