package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.user.UpdateProfileCommand;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.user.UpdateCurrentUserProfileService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateCurrentUserProfileService")
class UpdateCurrentUserProfileServiceTest {

  @Mock private UserRepository userRepository;

  private UpdateCurrentUserProfileService service;

  @BeforeEach
  void setUp() {
    service = new UpdateCurrentUserProfileService(userRepository);
  }

  @Test
  @DisplayName("should persist the updated profile and leave identity/credentials untouched")
  void update_existingUser_persistsProfileFields() {
    UUID userId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    User existing = User.create(userId, "user@example.com", "$2a$10$hash", true, false, createdAt);
    when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

    service.update(new UpdateProfileCommand(userId, "Ada Lovelace", "#3366FF", "es", "dark"));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(saved.capture());
    User result = saved.getValue();
    assertThat(result.displayName()).isEqualTo("Ada Lovelace");
    assertThat(result.avatarAccentColor()).isEqualTo("#3366FF");
    assertThat(result.uiLocale()).isEqualTo("es");
    assertThat(result.theme()).isEqualTo("dark");
    assertThat(result.id()).isEqualTo(userId);
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.passwordHash()).isEqualTo("$2a$10$hash");
    assertThat(result.isAdmin()).isTrue();
    assertThat(result.forcePasswordChange()).isFalse();
    assertThat(result.createdAt()).isEqualTo(createdAt);
  }

  @Test
  @DisplayName("should throw UserNotFoundException and not save when the user does not exist")
  void update_unknownUser_throwsAndDoesNotSave() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.update(new UpdateProfileCommand(userId, "Ada", "#3366FF", "en", "light")))
        .isInstanceOf(UserNotFoundException.class);

    verify(userRepository, never()).save(any(User.class));
  }
}
