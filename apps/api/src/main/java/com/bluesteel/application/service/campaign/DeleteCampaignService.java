package com.bluesteel.application.service.campaign;

import com.bluesteel.application.port.in.campaign.DeleteCampaignUseCase;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-only: permanently deletes a campaign and all its data via DB cascade. */
@Service
public class DeleteCampaignService implements DeleteCampaignUseCase {

  private static final Logger log = LoggerFactory.getLogger(DeleteCampaignService.class);

  private final CampaignRepository campaignRepository;

  public DeleteCampaignService(CampaignRepository campaignRepository) {
    this.campaignRepository = campaignRepository;
  }

  @Override
  @Transactional
  public void delete(UUID campaignId, UUID callerId, boolean callerIsAdmin) {
    if (!callerIsAdmin) {
      throw new UnauthorizedException("Only admins may delete campaigns");
    }
    campaignRepository
        .findById(campaignId)
        .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    campaignRepository.deleteById(campaignId);

    log.info("Campaign deleted campaignId={} deletedBy={}", campaignId, callerId);
  }
}
