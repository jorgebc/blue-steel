package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.model.campaign.CreateCampaignCommand;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.campaign.CreateCampaignService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.exception.UserNotFoundException;
import com.bluesteel.domain.user.User;
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
@DisplayName("CreateCampaignService")
class CreateCampaignServiceTest {

  @Mock private CampaignRepository campaignRepository;
  @Mock private CampaignMembershipRepository membershipRepository;
  @Mock private UserRepository userRepository;

  private CreateCampaignService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID GM_USER_ID = UUID.randomUUID();
  private static final User GM_USER =
      User.create(GM_USER_ID, "gm@example.com", "$2a$10$hash", false, false, Instant.now());

  @BeforeEach
  void setUp() {
    sut = new CreateCampaignService(campaignRepository, membershipRepository, userRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not admin")
  void create_nonAdmin_throwsUnauthorized() {
    CreateCampaignCommand cmd =
        new CreateCampaignCommand(CALLER_ID, false, "Dragon Keep", GM_USER_ID);

    assertThatThrownBy(() -> sut.create(cmd)).isInstanceOf(UnauthorizedException.class);

    verify(campaignRepository, never()).save(any());
    verify(membershipRepository, never()).save(any());
  }

  @Test
  @DisplayName("should throw UserNotFoundException when GM user does not exist")
  void create_gmUserNotFound_throwsUserNotFound() {
    when(userRepository.findById(GM_USER_ID)).thenReturn(Optional.empty());
    CreateCampaignCommand cmd =
        new CreateCampaignCommand(CALLER_ID, true, "Dragon Keep", GM_USER_ID);

    assertThatThrownBy(() -> sut.create(cmd)).isInstanceOf(UserNotFoundException.class);

    verify(campaignRepository, never()).save(any());
    verify(membershipRepository, never()).save(any());
  }

  @Test
  @DisplayName("should create campaign and GM membership when admin creates a valid campaign")
  void create_validAdminCommand_createsCampaignAndGmMembership() {
    when(userRepository.findById(GM_USER_ID)).thenReturn(Optional.of(GM_USER));
    CreateCampaignCommand cmd =
        new CreateCampaignCommand(CALLER_ID, true, "Dragon Keep", GM_USER_ID);

    CampaignView view = sut.create(cmd);

    assertThat(view.name()).isEqualTo("Dragon Keep");
    assertThat(view.createdBy()).isEqualTo(CALLER_ID);
    assertThat(view.role()).isEqualTo(CampaignRole.GM);
    assertThat(view.id()).isNotNull();
    assertThat(view.createdAt()).isNotNull();

    verify(campaignRepository).save(any());
    verify(membershipRepository).save(any());
  }
}
