package com.bluesteel.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.domain.exception.DomainException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshToken")
class RefreshTokenTest {

  @Test
  @DisplayName("should create an ACTIVE token with SHA-256 hash of the raw value")
  void create_storesHashNotRawToken() {
    String rawToken = "some-raw-token-value";
    RefreshToken token = RefreshToken.create(UUID.randomUUID(), UUID.randomUUID(), rawToken);

    assertThat(token.status()).isEqualTo(RefreshTokenStatus.ACTIVE);
    assertThat(token.tokenHash()).isNotEqualTo(rawToken);
    assertThat(token.tokenHash()).isEqualTo(RefreshToken.sha256(rawToken));
    assertThat(token.isExpired()).isFalse();
  }

  @Test
  @DisplayName("should set all fields correctly on create()")
  void create_setsAllFields() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    Instant before = Instant.now();

    RefreshToken token = RefreshToken.create(userId, familyId, "raw");

    Instant after = Instant.now();
    assertThat(token.id()).isNotNull();
    assertThat(token.userId()).isEqualTo(userId);
    assertThat(token.familyId()).isEqualTo(familyId);
    assertThat(token.createdAt()).isBetween(before, after);
    assertThat(token.expiresAt()).isAfter(before.plus(29, ChronoUnit.DAYS));
    assertThat(token.expiresAt()).isBefore(after.plus(31, ChronoUnit.DAYS));
  }

  @Test
  @DisplayName(
      "should transition to CONSUMED and preserve all fields when consume() is called on an ACTIVE token")
  void consume_activeToken_transitionsToConsumed() {
    RefreshToken token = RefreshToken.create(UUID.randomUUID(), UUID.randomUUID(), "raw");

    RefreshToken consumed = token.consume();

    assertThat(consumed.status()).isEqualTo(RefreshTokenStatus.CONSUMED);
    assertThat(consumed.id()).isEqualTo(token.id());
    assertThat(consumed.userId()).isEqualTo(token.userId());
    assertThat(consumed.familyId()).isEqualTo(token.familyId());
    assertThat(consumed.tokenHash()).isEqualTo(token.tokenHash());
    assertThat(consumed.expiresAt()).isEqualTo(token.expiresAt());
    assertThat(consumed.createdAt()).isEqualTo(token.createdAt());
  }

  @Test
  @DisplayName("should throw DomainException when consuming an already CONSUMED token")
  void consume_consumedToken_throwsDomainException() {
    RefreshToken token = RefreshToken.create(UUID.randomUUID(), UUID.randomUUID(), "raw");
    RefreshToken consumed = token.consume();

    assertThatThrownBy(consumed::consume).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("should detect expired tokens")
  void isExpired_pastExpiry_returnsTrue() {
    RefreshToken expired =
        RefreshToken.reconstitute(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "hash",
            RefreshTokenStatus.ACTIVE,
            Instant.now().minus(1, ChronoUnit.SECONDS),
            Instant.now().minus(31, ChronoUnit.DAYS));

    assertThat(expired.isExpired()).isTrue();
  }

  @Test
  @DisplayName("should throw DomainException when consuming an expired token")
  void consume_expiredToken_throwsDomainException() {
    RefreshToken expired =
        RefreshToken.reconstitute(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "hash",
            RefreshTokenStatus.ACTIVE,
            Instant.now().minus(1, ChronoUnit.SECONDS),
            Instant.now().minus(31, ChronoUnit.DAYS));

    assertThatThrownBy(expired::consume).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("should produce consistent SHA-256 hashes for the same input")
  void sha256_sameInput_consistentHash() {
    String hash1 = RefreshToken.sha256("token-value");
    String hash2 = RefreshToken.sha256("token-value");

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).hasSize(64);
  }
}
