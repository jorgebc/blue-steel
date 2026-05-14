package com.bluesteel.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User domain entity")
class UserTest {

  private static final UUID ID = UUID.randomUUID();
  private static final String EMAIL = "user@example.com";
  private static final String HASH = "$2a$10$somehash";
  private static final Instant NOW = Instant.now();

  @Test
  @DisplayName("should create a user with all provided fields")
  void create_withAllFields() {
    User user = User.create(ID, EMAIL, HASH, false, true, NOW);

    assertThat(user.id()).isEqualTo(ID);
    assertThat(user.email()).isEqualTo(EMAIL);
    assertThat(user.passwordHash()).isEqualTo(HASH);
    assertThat(user.isAdmin()).isFalse();
    assertThat(user.forcePasswordChange()).isTrue();
    assertThat(user.createdAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("should reject blank email")
  void create_blankEmail_throwsIllegalArgument() {
    assertThatThrownBy(() -> User.create(ID, "  ", HASH, false, false, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Email must not be blank");
  }

  @Test
  @DisplayName("should reject null email")
  void create_nullEmail_throwsIllegalArgument() {
    assertThatThrownBy(() -> User.create(ID, null, HASH, false, false, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject blank password hash")
  void create_blankHash_throwsIllegalArgument() {
    assertThatThrownBy(() -> User.create(ID, EMAIL, "", false, false, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password hash must not be blank");
  }

  @Test
  @DisplayName("should return a new user with updated password and forcePasswordChange cleared")
  void withUpdatedPassword_clearsForcePasswordChange() {
    User original = User.create(ID, EMAIL, HASH, true, true, NOW);
    User updated = original.withUpdatedPassword("$2a$10$newhash");

    assertThat(updated.passwordHash()).isEqualTo("$2a$10$newhash");
    assertThat(updated.forcePasswordChange()).isFalse();
    assertThat(updated.id()).isEqualTo(original.id());
    assertThat(updated.email()).isEqualTo(original.email());
    assertThat(updated.isAdmin()).isEqualTo(original.isAdmin());
    assertThat(updated.createdAt()).isEqualTo(original.createdAt());
  }

  @Test
  @DisplayName("should return a new user with refreshed invitation and forcePasswordChange set")
  void withRefreshedInvitation_setsForcePasswordChange() {
    User original = User.create(ID, EMAIL, HASH, true, false, NOW);
    User refreshed = original.withRefreshedInvitation("$2a$10$freshhash");

    assertThat(refreshed.passwordHash()).isEqualTo("$2a$10$freshhash");
    assertThat(refreshed.forcePasswordChange()).isTrue();
    assertThat(refreshed.id()).isEqualTo(original.id());
    assertThat(refreshed.email()).isEqualTo(original.email());
    assertThat(refreshed.isAdmin()).isEqualTo(original.isAdmin());
    assertThat(refreshed.createdAt()).isEqualTo(original.createdAt());
  }
}
