package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.user.ChangePasswordCommand;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.user.ChangePasswordService;
import com.bluesteel.domain.exception.InvalidPasswordException;
import com.bluesteel.domain.exception.UserNotFoundException;
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
@DisplayName("ChangePasswordService")
class ChangePasswordServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;

  private ChangePasswordService service;

  @BeforeEach
  void setUp() {
    service = new ChangePasswordService(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("should throw UserNotFoundException when user does not exist")
  void change_unknownUser_throwsUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.change(new ChangePasswordCommand(userId, "current", "newpass")))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("should throw InvalidPasswordException when current password is wrong")
  void change_wrongCurrentPassword_throwsInvalidPassword() {
    UUID userId = UUID.randomUUID();
    User user =
        User.create(userId, "user@example.com", "$2a$10$stored", false, false, Instant.now());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrongpass", "$2a$10$stored")).thenReturn(false);

    assertThatThrownBy(
            () -> service.change(new ChangePasswordCommand(userId, "wrongpass", "newpass")))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  @DisplayName("should update password hash and clear forcePasswordChange on success")
  void change_correctCurrentPassword_updatesHashAndClearsFlag() {
    UUID userId = UUID.randomUUID();
    User user =
        User.create(userId, "user@example.com", "$2a$10$stored", false, true, Instant.now());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(eq("correctpass"), any())).thenReturn(true);
    when(passwordEncoder.encode("newpass")).thenReturn("$2a$10$newhash");

    service.change(new ChangePasswordCommand(userId, "correctpass", "newpass"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.passwordHash()).isEqualTo("$2a$10$newhash");
    assertThat(saved.forcePasswordChange()).isFalse();
  }
}
