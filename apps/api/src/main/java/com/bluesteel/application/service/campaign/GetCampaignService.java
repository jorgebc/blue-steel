package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Returns a campaign with the caller's resolved role, enforcing member-or-admin access (D-043). */
@Service
public class GetCampaignService implements GetCampaignUseCase {

  private final CampaignRepository campaignRepository;
  private final CampaignMembershipPort membershipPort;

  public GetCampaignService(
      CampaignRepository campaignRepository, CampaignMembershipPort membershipPort) {
    this.campaignRepository = campaignRepository;
    this.membershipPort = membershipPort;
  }

  @Override
  public CampaignView get(UUID campaignId, UUID callerId, boolean callerIsAdmin) {
    Campaign campaign =
        campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));

    Optional<CampaignRole> role = membershipPort.resolveRole(campaignId, callerId);

    if (!callerIsAdmin && role.isEmpty()) {
      throw new UnauthorizedException("Caller is not a member of this campaign");
    }

    return new CampaignView(
        campaign.id(),
        campaign.name(),
        campaign.createdBy(),
        campaign.createdAt(),
        role.orElse(null));
  }
}
