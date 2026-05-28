package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.model.campaign.CreateCampaignCommand;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.exception.UserNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a campaign and atomically assigns the specified user as GM (D-061). */
@Service
public class CreateCampaignService implements CreateCampaignUseCase {

  private static final Logger log = LoggerFactory.getLogger(CreateCampaignService.class);

  private final CampaignRepository campaignRepository;
  private final CampaignMembershipRepository membershipRepository;
  private final UserRepository userRepository;

  public CreateCampaignService(
      CampaignRepository campaignRepository,
      CampaignMembershipRepository membershipRepository,
      UserRepository userRepository) {
    this.campaignRepository = campaignRepository;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
  }

  @Override
  @Transactional
  public CampaignView create(CreateCampaignCommand command) {
    if (!command.callerIsAdmin()) {
      throw new UnauthorizedException("Only admins may create campaigns");
    }

    userRepository
        .findById(command.gmUserId())
        .orElseThrow(() -> new UserNotFoundException("GM user not found: " + command.gmUserId()));

    Instant now = Instant.now();
    Campaign campaign = Campaign.create(UUID.randomUUID(), command.name(), command.callerId(), now);
    campaignRepository.save(campaign);

    CampaignMember gm =
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), command.gmUserId(), CampaignRole.GM, now);
    membershipRepository.save(gm);

    log.info(
        "Campaign created campaignId={} name={} gmUserId={}",
        campaign.id(),
        campaign.name(),
        command.gmUserId());

    return new CampaignView(
        campaign.id(),
        campaign.name(),
        campaign.createdBy(),
        campaign.createdAt(),
        CampaignRole.GM);
  }
}
