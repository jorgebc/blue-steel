package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.RemoveMemberCommand;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.service.campaign.RemoveMemberService;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.CannotRemoveGmException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RemoveMemberService")
class RemoveMemberServiceTest {

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID TARGET_ID = UUID.randomUUID();

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private CampaignMembershipRepository membershipRepository;

  private RemoveMemberService service;

  @BeforeEach
  void setUp() {
    service = new RemoveMemberService(membershipPort, membershipRepository);
  }

  private RemoveMemberCommand command() {
    return new RemoveMemberCommand(CAMPAIGN_ID, CALLER_ID, TARGET_ID);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not the GM")
  void remove_callerNotGm_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    assertThatThrownBy(() -> service.remove(command())).isInstanceOf(UnauthorizedException.class);

    verify(membershipRepository, never())
        .deleteByCampaignIdAndUserId(any(UUID.class), any(UUID.class));
  }

  @Test
  @DisplayName("should throw CampaignNotFoundException when target is not a member")
  void remove_targetNotMember_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove(command()))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(membershipRepository, never())
        .deleteByCampaignIdAndUserId(any(UUID.class), any(UUID.class));
  }

  @Test
  @DisplayName("should throw CannotRemoveGmException when target is a GM")
  void remove_targetIsGm_throwsCannotRemoveGm() {
    CampaignMember gm =
        CampaignMember.create(
            UUID.randomUUID(), CAMPAIGN_ID, TARGET_ID, CampaignRole.GM, Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID))
        .thenReturn(Optional.of(gm));

    assertThatThrownBy(() -> service.remove(command())).isInstanceOf(CannotRemoveGmException.class);

    verify(membershipRepository, never())
        .deleteByCampaignIdAndUserId(any(UUID.class), any(UUID.class));
  }

  @Test
  @DisplayName("should delete the membership for a valid non-GM target")
  void remove_validTarget_deletesMembership() {
    CampaignMember player =
        CampaignMember.create(
            UUID.randomUUID(), CAMPAIGN_ID, TARGET_ID, CampaignRole.PLAYER, Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID))
        .thenReturn(Optional.of(player));

    service.remove(command());

    verify(membershipRepository).deleteByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID);
  }
}
