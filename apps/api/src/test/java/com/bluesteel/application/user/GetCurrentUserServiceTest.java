package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.user.GetCurrentUserService;
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
@DisplayName("GetCurrentUserService")
class GetCurrentUserServiceTest {

  @Mock private UserRepository userRepository;

  private GetCurrentUserService service;

  @BeforeEach
  void setUp() {
    service = new GetCurrentUserService(userRepository);
  }

  @Test
  @DisplayName("should return user profile when user exists")
  void getCurrentUser_existingUser_returnsProfile() {
    UUID userId = UUID.randomUUID();
    User user = User.create(userId, "user@example.com", "$2a$10$hash", true, false, Instant.now());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserProfile profile = service.getCurrentUser(userId);

    assertThat(profile.id()).isEqualTo(userId);
    assertThat(profile.email()).isEqualTo("user@example.com");
    assertThat(profile.isAdmin()).isTrue();
    assertThat(profile.forcePasswordChange()).isFalse();
  }

  @Test
  @DisplayName("should throw UserNotFoundException when user does not exist")
  void getCurrentUser_unknownUser_throwsUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCurrentUser(userId))
        .isInstanceOf(UserNotFoundException.class);
  }
}
