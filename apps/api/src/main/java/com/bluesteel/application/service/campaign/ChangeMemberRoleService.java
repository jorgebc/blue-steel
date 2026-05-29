package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.ChangeMemberRoleCommand;
import com.bluesteel.application.port.in.campaign.ChangeMemberRoleUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.CannotRemoveGmException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** GM-only: changes a non-GM member's role; a GM's role cannot be changed (D-015, D-043). */
@Service
public class ChangeMemberRoleService implements ChangeMemberRoleUseCase {

  private final CampaignMembershipPort membershipPort;
  private final CampaignMembershipRepository membershipRepository;

  public ChangeMemberRoleService(
      CampaignMembershipPort membershipPort, CampaignMembershipRepository membershipRepository) {
    this.membershipPort = membershipPort;
    this.membershipRepository = membershipRepository;
  }

  @Override
  public void change(ChangeMemberRoleCommand command) {
    requireGm(command.campaignId(), command.callerId());

    CampaignMember target =
        membershipRepository
            .findByCampaignIdAndUserId(command.campaignId(), command.targetUserId())
            .orElseThrow(() -> new CampaignNotFoundException(command.campaignId()));

    if (target.role() == CampaignRole.GM) {
      throw new CannotRemoveGmException(command.targetUserId());
    }

    membershipRepository.save(target.withRole(command.newRole()));
  }

  private void requireGm(UUID campaignId, UUID callerId) {
    CampaignRole role =
        membershipPort
            .resolveRole(campaignId, callerId)
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));
    if (role != CampaignRole.GM) {
      throw new UnauthorizedException("Only the GM may change member roles");
    }
  }
}
