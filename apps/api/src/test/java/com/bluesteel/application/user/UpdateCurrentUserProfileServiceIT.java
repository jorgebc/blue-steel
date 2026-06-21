package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.model.user.UpdateProfileCommand;
import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end profile-update coverage against a live PostgreSQL: proves the {@code PATCH /me}
 * partial-merge contract (D-113) holds through the real persistence path. The pre-fix full-replace
 * implementation would have nulled the {@code NOT NULL} {@code ui_locale}/{@code theme} columns on
 * a single-field update and failed the DB constraint here — the value of doing this as an IT rather
 * than a mocked unit test.
 */
@DisplayName("UpdateCurrentUserProfileService (integration)")
class UpdateCurrentUserProfileServiceIT extends TestcontainersPostgresBaseIT {

  @Autowired private UpdateCurrentUserProfileUseCase updateProfile;
  @Autowired private GetCurrentUserUseCase getCurrentUser;
  @Autowired private UserRepository userRepository;

  private User seedUser(String displayName, String accent, String locale, String theme) {
    UUID id = UUID.randomUUID();
    User user =
        User.create(
            id,
            id + "@example.com",
            "$2a$10$hash",
            false,
            false,
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            displayName,
            accent,
            locale,
            theme);
    userRepository.save(user);
    return user;
  }

  @Test
  @DisplayName(
      "partial update (theme only) preserves the other fields and respects NOT NULL columns")
  void partialUpdate_preservesUntouchedFields() {
    User user = seedUser("Ada Lovelace", "#3366FF", "es", "light");

    // Mirrors the account-menu theme toggle: a body carrying only `theme`.
    updateProfile.update(new UpdateProfileCommand(user.id(), null, null, null, "dark"));

    UserProfile after = getCurrentUser.getCurrentUser(user.id());
    assertThat(after.theme()).isEqualTo("dark");
    assertThat(after.displayName()).isEqualTo("Ada Lovelace");
    assertThat(after.avatarAccentColor()).isEqualTo("#3366FF");
    assertThat(after.uiLocale()).isEqualTo("es");
  }

  @Test
  @DisplayName("partial update (uiLocale only) preserves the theme and the rest of the profile")
  void partialUpdate_localeOnly_preservesTheme() {
    User user = seedUser("Grace Hopper", "#FF8800", "en", "dark");

    updateProfile.update(new UpdateProfileCommand(user.id(), null, null, "es", null));

    UserProfile after = getCurrentUser.getCurrentUser(user.id());
    assertThat(after.uiLocale()).isEqualTo("es");
    assertThat(after.theme()).isEqualTo("dark");
    assertThat(after.displayName()).isEqualTo("Grace Hopper");
    assertThat(after.avatarAccentColor()).isEqualTo("#FF8800");
  }

  @Test
  @DisplayName("an empty display name clears it to null while keeping the other fields")
  void update_emptyDisplayName_clearsName() {
    User user = seedUser("Temporary Name", "#3366FF", "es", "dark");

    updateProfile.update(new UpdateProfileCommand(user.id(), "", "#3366FF", "es", "dark"));

    UserProfile after = getCurrentUser.getCurrentUser(user.id());
    assertThat(after.displayName()).isNull();
    assertThat(after.uiLocale()).isEqualTo("es");
    assertThat(after.theme()).isEqualTo("dark");
  }

  @Test
  @DisplayName("one user's update never affects another user's settings")
  void update_isolatedPerUser() {
    User alice = seedUser("Alice", "#3366FF", "es", "light");
    User bob = seedUser("Bob", "#FF8800", "en", "dark");

    updateProfile.update(new UpdateProfileCommand(alice.id(), "Alice Updated", null, "en", "dark"));

    UserProfile bobAfter = getCurrentUser.getCurrentUser(bob.id());
    assertThat(bobAfter.displayName()).isEqualTo("Bob");
    assertThat(bobAfter.avatarAccentColor()).isEqualTo("#FF8800");
    assertThat(bobAfter.uiLocale()).isEqualTo("en");
    assertThat(bobAfter.theme()).isEqualTo("dark");
  }
}
