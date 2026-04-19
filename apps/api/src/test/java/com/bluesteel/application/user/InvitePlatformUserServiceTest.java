package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.model.user.InvitationResult;
import com.bluesteel.application.model.user.InvitePlatformUserCommand;
import com.bluesteel.application.port.out.email.EmailPort;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.user.InvitePlatformUserService;
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
@DisplayName("InvitePlatformUserService")
class InvitePlatformUserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private EmailPort emailPort;
  @Mock private PasswordEncoder passwordEncoder;

  private InvitePlatformUserService service;

  @BeforeEach
  void setUp() {
    service = new InvitePlatformUserService(userRepository, emailPort, passwordEncoder);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not admin")
  void invite_nonAdmin_throwsUnauthorized() {
    InvitePlatformUserCommand cmd =
        new InvitePlatformUserCommand(UUID.randomUUID(), false, "user@example.com");

    assertThatThrownBy(() -> service.invite(cmd)).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should create a new user account and send email when email is not registered")
  void invite_newEmail_createsUserAndSendsEmail() {
    when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");

    InvitePlatformUserCommand cmd =
        new InvitePlatformUserCommand(UUID.randomUUID(), true, "new@example.com");
    InvitationResult result = service.invite(cmd);

    assertThat(result).isEqualTo(InvitationResult.CREATED);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User saved = userCaptor.getValue();
    assertThat(saved.email()).isEqualTo("new@example.com");
    assertThat(saved.forcePasswordChange()).isTrue();
    assertThat(saved.isAdmin()).isFalse();

    ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailPort).send(emailCaptor.capture());
    assertThat(emailCaptor.getValue().to()).isEqualTo("new@example.com");
  }

  @Test
  @DisplayName(
      "should refresh credentials and send email when email already exists (re-invitation)")
  void invite_existingEmail_refreshesCredentialsAndSendsEmail() {
    User existing =
        User.create(
            UUID.randomUUID(),
            "existing@example.com",
            "$2a$10$oldhash",
            false,
            false,
            Instant.now());
    when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
    when(passwordEncoder.encode(any())).thenReturn("$2a$10$newhash");

    InvitePlatformUserCommand cmd =
        new InvitePlatformUserCommand(UUID.randomUUID(), true, "existing@example.com");
    InvitationResult result = service.invite(cmd);

    assertThat(result).isEqualTo(InvitationResult.REFRESHED);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User saved = userCaptor.getValue();
    assertThat(saved.passwordHash()).isEqualTo("$2a$10$newhash");
    assertThat(saved.forcePasswordChange()).isTrue();

    verify(emailPort).send(any(EmailMessage.class));
  }
}
