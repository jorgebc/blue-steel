package com.bluesteel.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.auth.RefreshResult;
import com.bluesteel.application.port.out.auth.JwtPort;
import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.auth.RefreshTokenService;
import com.bluesteel.domain.auth.RefreshToken;
import com.bluesteel.domain.auth.RefreshTokenStatus;
import com.bluesteel.domain.exception.RefreshTokenException;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private JwtPort jwtPort;

  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    service = new RefreshTokenService(refreshTokenRepository, userRepository, jwtPort);
  }

  @Test
  @DisplayName("should throw RefreshTokenException when token is not found")
  void refresh_unknownToken_throwsException() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh("unknown-raw-token"))
        .isInstanceOf(RefreshTokenException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName(
      "should throw RefreshTokenException and revoke family when consumed token is presented")
  void refresh_consumedToken_revokesFamily() {
    UUID familyId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RefreshToken consumed =
        RefreshToken.reconstitute(
            UUID.randomUUID(),
            userId,
            familyId,
            "hash",
            RefreshTokenStatus.CONSUMED,
            Instant.now().plus(30, ChronoUnit.DAYS),
            Instant.now());
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(consumed));

    assertThatThrownBy(() -> service.refresh("some-raw-token"))
        .isInstanceOf(RefreshTokenException.class)
        .extracting("code")
        .isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");

    verify(refreshTokenRepository).revokeFamily(familyId);
  }

  @Test
  @DisplayName("should throw RefreshTokenException when token is expired")
  void refresh_expiredToken_throwsException() {
    RefreshToken expired =
        RefreshToken.reconstitute(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "hash",
            RefreshTokenStatus.ACTIVE,
            Instant.now().minus(1, ChronoUnit.SECONDS),
            Instant.now().minus(31, ChronoUnit.DAYS));
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.refresh("raw-token"))
        .isInstanceOf(RefreshTokenException.class)
        .hasMessageContaining("expired");
  }

  @Test
  @DisplayName("should rotate token and return new access token on valid refresh")
  void refresh_validToken_rotatesAndReturnsNewTokens() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String rawToken = "valid-raw-token";
    String hash = RefreshToken.sha256(rawToken);

    RefreshToken active =
        RefreshToken.reconstitute(
            UUID.randomUUID(),
            userId,
            familyId,
            hash,
            RefreshTokenStatus.ACTIVE,
            Instant.now().plus(30, ChronoUnit.DAYS),
            Instant.now());

    User user = User.create(userId, "user@example.com", "$2a$hash", false, false, Instant.now());
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(active));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtPort.issue(eq(userId), eq(false), any())).thenReturn("new-access-token");

    RefreshResult result = service.refresh(rawToken);

    assertThat(result.accessToken()).isEqualTo("new-access-token");
    assertThat(result.rawRefreshToken()).isNotNull().isNotBlank().isNotEqualTo(rawToken);
    assertThat(result.userId()).isEqualTo(userId);
  }
}
