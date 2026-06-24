package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.domain.campaign.Campaign;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lists campaigns: admin gets all with resolved-or-null role; regular user gets only their own
 * campaigns (D-024, D-043).
 */
@Service
public class ListCampaignsService implements ListCampaignsUseCase {

  private final CampaignRepository campaignRepository;
  private final CampaignMembershipPort membershipPort;

  public ListCampaignsService(
      CampaignRepository campaignRepository, CampaignMembershipPort membershipPort) {
    this.campaignRepository = campaignRepository;
    this.membershipPort = membershipPort;
  }

  @Override
  public List<CampaignView> list(UUID callerId, boolean callerIsAdmin) {
    List<Campaign> campaigns =
        callerIsAdmin
            ? campaignRepository.findAll()
            : campaignRepository.findAllByMemberId(callerId);

    return campaigns.stream()
        .map(
            c ->
                new CampaignView(
                    c.id(),
                    c.name(),
                    c.createdBy(),
                    c.createdAt(),
                    c.contentLanguage(),
                    membershipPort.resolveRole(c.id(), callerId).orElse(null)))
        .toList();
  }
}
