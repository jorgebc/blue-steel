package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.RemoveMemberCommand;
import com.bluesteel.application.port.in.campaign.RemoveMemberUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.CannotRemoveGmException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** GM-only: removes a non-GM member from a campaign; removing the GM is rejected (D-043, D-061). */
@Service
public class RemoveMemberService implements RemoveMemberUseCase {

  private final CampaignMembershipPort membershipPort;
  private final CampaignMembershipRepository membershipRepository;

  public RemoveMemberService(
      CampaignMembershipPort membershipPort, CampaignMembershipRepository membershipRepository) {
    this.membershipPort = membershipPort;
    this.membershipRepository = membershipRepository;
  }

  @Override
  public void remove(RemoveMemberCommand command) {
    requireGm(command.campaignId(), command.callerId());

    CampaignMember target =
        membershipRepository
            .findByCampaignIdAndUserId(command.campaignId(), command.targetUserId())
            .orElseThrow(() -> new CampaignNotFoundException(command.campaignId()));

    if (target.role() == CampaignRole.GM) {
      throw new CannotRemoveGmException(command.targetUserId());
    }

    membershipRepository.deleteByCampaignIdAndUserId(command.campaignId(), command.targetUserId());
  }

  private void requireGm(UUID campaignId, UUID callerId) {
    CampaignRole role =
        membershipPort
            .resolveRole(campaignId, callerId)
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));
    if (role != CampaignRole.GM) {
      throw new UnauthorizedException("Only the GM may remove campaign members");
    }
  }
}
