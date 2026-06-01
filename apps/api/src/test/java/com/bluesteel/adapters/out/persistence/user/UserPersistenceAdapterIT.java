package com.bluesteel.adapters.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("UserPersistenceAdapter")
class UserPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private UserPersistenceAdapter adapter;

  @Test
  @DisplayName("should save and find a user by id")
  void saveAndFindById() {
    UUID id = UUID.randomUUID();
    User user =
        User.create(
            id,
            id + "@example.com",
            "$2a$10$hash",
            false,
            true,
            Instant.now().truncatedTo(ChronoUnit.MICROS));

    adapter.save(user);
    Optional<User> found = adapter.findById(id);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
    assertThat(found.get().email()).isEqualTo(id + "@example.com");
    assertThat(found.get().isAdmin()).isFalse();
    assertThat(found.get().forcePasswordChange()).isTrue();
  }

  @Test
  @DisplayName("should find a user by email")
  void saveAndFindByEmail() {
    UUID id = UUID.randomUUID();
    String email = "findme-" + id + "@example.com";
    User user =
        User.create(
            id, email, "$2a$10$hash", false, false, Instant.now().truncatedTo(ChronoUnit.MICROS));

    adapter.save(user);
    Optional<User> found = adapter.findByEmail(email);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
  }

  @Test
  @DisplayName("should find users by a case-insensitive email fragment, ordered alphabetically")
  void searchByEmail_matchesFragmentCaseInsensitive() {
    String tag = "frag" + UUID.randomUUID().toString().replace("-", "");
    save("amy-" + tag + "@example.com");
    save("zoe-" + tag + "@example.com");
    save("other-" + UUID.randomUUID() + "@example.com");

    // Upper-cased fragment must still match the lower-cased stored emails (ILIKE).
    List<User> found = adapter.searchByEmail(tag.toUpperCase());

    assertThat(found)
        .extracting(User::email)
        .containsExactly("amy-" + tag + "@example.com", "zoe-" + tag + "@example.com");
  }

  @Test
  @DisplayName("should cap email-fragment search results at 10")
  void searchByEmail_capsResultsAtTen() {
    String tag = "cap" + UUID.randomUUID().toString().replace("-", "");
    for (int i = 0; i < 12; i++) {
      save("user-" + i + "-" + tag + "@example.com");
    }

    assertThat(adapter.searchByEmail(tag)).hasSize(10);
  }

  @Test
  @DisplayName("should return empty when no email matches the fragment")
  void searchByEmail_noMatch_returnsEmpty() {
    assertThat(adapter.searchByEmail("definitely-no-such-fragment")).isEmpty();
  }

  @Test
  @DisplayName("should return empty when user is not found by id")
  void findById_notFound_returnsEmpty() {
    Optional<User> found = adapter.findById(UUID.randomUUID());
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("should return empty when user is not found by email")
  void findByEmail_notFound_returnsEmpty() {
    Optional<User> found = adapter.findByEmail("nobody@example.com");
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("should return true when an admin user exists")
  void existsByIsAdminTrue_adminExists_returnsTrue() {
    // The bootstrap service seeds an admin in TestcontainersPostgresBaseIT
    assertThat(adapter.existsByIsAdminTrue()).isTrue();
  }

  @Test
  @DisplayName("should update a user on save when the user already exists")
  void save_existingUser_updatesRecord() {
    UUID id = UUID.randomUUID();
    User original =
        User.create(
            id,
            id + "-update@example.com",
            "$2a$10$oldhash",
            false,
            true,
            Instant.now().truncatedTo(ChronoUnit.MICROS));
    adapter.save(original);

    User updated = original.withUpdatedPassword("$2a$10$newhash");
    adapter.save(updated);

    Optional<User> found = adapter.findById(id);
    assertThat(found).isPresent();
    assertThat(found.get().passwordHash()).isEqualTo("$2a$10$newhash");
    assertThat(found.get().forcePasswordChange()).isFalse();
  }

  private void save(String email) {
    adapter.save(
        User.create(
            UUID.randomUUID(),
            email,
            "$2a$10$hash",
            false,
            false,
            Instant.now().truncatedTo(ChronoUnit.MICROS)));
  }
}
