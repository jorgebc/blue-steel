package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import java.util.List;
import java.util.UUID;

/** Returns the campaigns the caller belongs to; admin callers receive all campaigns. */
public interface ListCampaignsUseCase {

  List<CampaignView> list(UUID callerId, boolean callerIsAdmin);
}
