package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.InviteCampaignMemberCommand;
import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.email.EmailPort;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.campaign.InviteCampaignMemberService;
import com.bluesteel.application.service.email.InvitationEmailFactory;
import com.bluesteel.application.service.user.TemporaryPasswordGenerator;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AlreadyCampaignMemberException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.user.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteCampaignMemberService")
class InviteCampaignMemberServiceTest {

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final String EMAIL = "invitee@example.com";
  private static final String TEMP_PASSWORD = "Temp0Pass1Word2!";

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private CampaignMembershipRepository membershipRepository;
  @Mock private UserRepository userRepository;
  @Mock private EmailPort emailPort;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TemporaryPasswordGenerator temporaryPasswordGenerator;

  private InviteCampaignMemberService service;

  @BeforeEach
  void setUp() {
    service =
        new InviteCampaignMemberService(
            membershipPort,
            membershipRepository,
            userRepository,
            emailPort,
            passwordEncoder,
            temporaryPasswordGenerator,
            new InvitationEmailFactory("https://app.bluesteel.test"));
  }

  private InviteCampaignMemberCommand command() {
    return new InviteCampaignMemberCommand(CAMPAIGN_ID, CALLER_ID, EMAIL, CampaignRole.EDITOR);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a member")
  void invite_callerNotMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.invite(command())).isInstanceOf(UnauthorizedException.class);

    verify(userRepository, never()).save(any(User.class));
    verify(membershipRepository, never()).save(any(CampaignMember.class));
    verify(emailPort, never()).send(any(EmailMessage.class));
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not the GM")
  void invite_callerNotGm_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));

    assertThatThrownBy(() -> service.invite(command())).isInstanceOf(UnauthorizedException.class);

    verify(userRepository, never()).save(any(User.class));
    verify(membershipRepository, never()).save(any(CampaignMember.class));
  }

  @Test
  @DisplayName("should create a new account, add membership, send email and return true")
  void invite_newUser_createsAccountAndMembership() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(temporaryPasswordGenerator.generate()).thenReturn(TEMP_PASSWORD);
    when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn("$2a$10$hash");
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    when(membershipRepository.findByCampaignIdAndUserId(any(UUID.class), any(UUID.class)))
        .thenReturn(Optional.empty());

    boolean created = service.invite(command());

    assertThat(created).isTrue();

    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.email()).isEqualTo(EMAIL);
    assertThat(savedUser.passwordHash()).isEqualTo("$2a$10$hash");
    assertThat(savedUser.forcePasswordChange()).isTrue();
    assertThat(savedUser.isAdmin()).isFalse();

    ArgumentCaptor<CampaignMember> memberCaptor = ArgumentCaptor.forClass(CampaignMember.class);
    verify(membershipRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(memberCaptor.getValue().userId()).isEqualTo(savedUser.id());
    assertThat(memberCaptor.getValue().role()).isEqualTo(CampaignRole.EDITOR);

    ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailPort).send(emailCaptor.capture());
    assertThat(emailCaptor.getValue().to()).isEqualTo(EMAIL);
    assertThat(emailCaptor.getValue().textBody()).contains(TEMP_PASSWORD);
  }

  @Test
  @DisplayName("should refresh an existing account and return false when user is not yet a member")
  void invite_existingUserNotMember_refreshesAndReturnsFalse() {
    User existing =
        User.create(UUID.randomUUID(), EMAIL, "$2a$10$old", false, false, Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(temporaryPasswordGenerator.generate()).thenReturn(TEMP_PASSWORD);
    when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn("$2a$10$newhash");
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, existing.id()))
        .thenReturn(Optional.empty());

    boolean created = service.invite(command());

    assertThat(created).isFalse();

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().id()).isEqualTo(existing.id());
    assertThat(userCaptor.getValue().passwordHash()).isEqualTo("$2a$10$newhash");
    assertThat(userCaptor.getValue().forcePasswordChange()).isTrue();

    verify(membershipRepository).save(any(CampaignMember.class));
    verify(emailPort).send(any(EmailMessage.class));
  }

  @Test
  @DisplayName("should throw AlreadyCampaignMemberException when the user already belongs")
  void invite_existingMember_throwsAlreadyMember() {
    User existing =
        User.create(UUID.randomUUID(), EMAIL, "$2a$10$old", false, false, Instant.now());
    CampaignMember member =
        CampaignMember.create(
            UUID.randomUUID(), CAMPAIGN_ID, existing.id(), CampaignRole.PLAYER, Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(temporaryPasswordGenerator.generate()).thenReturn(TEMP_PASSWORD);
    when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn("$2a$10$newhash");
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
    when(membershipRepository.findByCampaignIdAndUserId(CAMPAIGN_ID, existing.id()))
        .thenReturn(Optional.of(member));

    assertThatThrownBy(() -> service.invite(command()))
        .isInstanceOf(AlreadyCampaignMemberException.class);

    verify(membershipRepository, never()).save(any(CampaignMember.class));
    verify(emailPort, never()).send(any(EmailMessage.class));
  }
}
