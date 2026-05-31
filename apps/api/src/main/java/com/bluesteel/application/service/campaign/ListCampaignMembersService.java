package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.CampaignMemberView;
import com.bluesteel.application.port.in.campaign.ListCampaignMembersUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Returns a campaign's member roster (with emails) to any member, or to the platform admin. */
@Service
public class ListCampaignMembersService implements ListCampaignMembersUseCase {

  private static final Logger log = LoggerFactory.getLogger(ListCampaignMembersService.class);

  private final CampaignMembershipPort membershipPort;
  private final CampaignMembershipRepository membershipRepository;
  private final UserRepository userRepository;

  public ListCampaignMembersService(
      CampaignMembershipPort membershipPort,
      CampaignMembershipRepository membershipRepository,
      UserRepository userRepository) {
    this.membershipPort = membershipPort;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
  }

  @Override
  public List<CampaignMemberView> list(UUID campaignId, UUID callerId, boolean callerIsAdmin) {
    log.info("Listing members campaignId={} callerId={}", campaignId, callerId);

    boolean isMember = membershipPort.resolveRole(campaignId, callerId).isPresent();
    if (!callerIsAdmin && !isMember) {
      throw new UnauthorizedException("Caller is not a member of this campaign");
    }

    return membershipRepository.findByCampaignId(campaignId).stream().map(this::toView).toList();
  }

  private CampaignMemberView toView(CampaignMember member) {
    String email = userRepository.findById(member.userId()).map(u -> u.email()).orElse(null);
    return new CampaignMemberView(member.userId(), email, member.role(), member.joinedAt());
  }
}
