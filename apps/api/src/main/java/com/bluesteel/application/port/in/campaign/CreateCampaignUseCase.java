package com.bluesteel.application.port.in.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.model.campaign.CreateCampaignCommand;

/** Creates a new campaign with an atomic GM membership assignment (admin-only, D-024, D-061). */
public interface CreateCampaignUseCase {

  CampaignView create(CreateCampaignCommand command);
}
