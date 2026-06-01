package com.bluesteel.application.service.campaign;

import com.bluesteel.application.port.in.campaign.DeleteCampaignUseCase;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Admin-only: permanently deletes a campaign and all its data via DB cascade. */
@Service
public class DeleteCampaignService implements DeleteCampaignUseCase {

  private final CampaignRepository campaignRepository;

  public DeleteCampaignService(CampaignRepository campaignRepository) {
    this.campaignRepository = campaignRepository;
  }

  @Override
  public void delete(UUID campaignId, UUID callerId, boolean callerIsAdmin) {
    if (!callerIsAdmin) {
      throw new UnauthorizedException("Only admins may delete campaigns");
    }
    campaignRepository
        .findById(campaignId)
        .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    campaignRepository.deleteById(campaignId);
  }
}
