package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.ChangeMemberRoleCommand;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.service.campaign.ChangeMemberRoleService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeMemberRoleService")
class ChangeMemberRoleServiceTest {

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID TARGET_ID = UUID.randomUUID();

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private CampaignMembershipRepository membershipRepository;

  private ChangeMemberRoleService service;

  @BeforeEach
  void setUp() {
    service = new ChangeMemberRoleService(membershipPort, membershipRepository);
  }

  private ChangeMemberRoleCommand command() {
    return new ChangeMemberRoleCommand(CAMPAIGN_ID, CALLER_ID, TARGET_ID, CampaignRole.EDITOR);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not the GM")
  void change_callerNotGm_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));

    assertThatThrownBy(() -> service.change(command())).isInstanceOf(UnauthorizedException.class);

    verify(membershipRepository, never()).save(any(CampaignMember.class));
  }

  @Test
  @DisplayName("should throw CampaignNotFoundException when target is not a member")
  void change_targetNotMember_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.change(command()))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(membershipRepository, never()).save(any(CampaignMember.class));
  }

  @Test
  @DisplayName("should throw CannotRemoveGmException when target is a GM")
  void change_targetIsGm_throwsCannotRemoveGm() {
    CampaignMember gm =
        CampaignMember.create(
            UUID.randomUUID(), CAMPAIGN_ID, TARGET_ID, CampaignRole.GM, Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID))
        .thenReturn(Optional.of(gm));

    assertThatThrownBy(() -> service.change(command())).isInstanceOf(CannotRemoveGmException.class);

    verify(membershipRepository, never()).save(any(CampaignMember.class));
  }

  @Test
  @DisplayName("should persist the member with the new role for a valid change")
  void change_validTarget_savesNewRole() {
    CampaignMember player =
        CampaignMember.create(
            UUID.randomUUID(), CAMPAIGN_ID, TARGET_ID, CampaignRole.PLAYER, Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, TARGET_ID))
        .thenReturn(Optional.of(player));

    service.change(command());

    ArgumentCaptor<CampaignMember> captor = ArgumentCaptor.forClass(CampaignMember.class);
    verify(membershipRepository).save(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo(TARGET_ID);
    assertThat(captor.getValue().role()).isEqualTo(CampaignRole.EDITOR);
  }
}
