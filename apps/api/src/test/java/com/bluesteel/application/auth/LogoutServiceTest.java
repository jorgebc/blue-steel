package com.bluesteel.application.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.application.service.auth.LogoutService;
import com.bluesteel.domain.auth.RefreshToken;
import com.bluesteel.domain.auth.RefreshTokenStatus;
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
@DisplayName("LogoutService")
class LogoutServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;

  private LogoutService service;

  @BeforeEach
  void setUp() {
    service = new LogoutService(refreshTokenRepository);
  }

  @Test
  @DisplayName("should revoke the token family when a valid refresh token is presented")
  void logout_knownToken_revokesFamily() {
    UUID familyId = UUID.randomUUID();
    String rawToken = "some-raw-token";
    String hash = RefreshToken.sha256(rawToken);

    RefreshToken token =
        RefreshToken.reconstitute(
            UUID.randomUUID(),
            UUID.randomUUID(),
            familyId,
            hash,
            RefreshTokenStatus.ACTIVE,
            Instant.now().plus(30, ChronoUnit.DAYS),
            Instant.now());

    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

    service.logout(rawToken);

    verify(refreshTokenRepository).revokeFamily(familyId);
  }

  @Test
  @DisplayName("should do nothing when refresh token is not found")
  void logout_unknownToken_noOp() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    service.logout("unknown-token");

    verify(refreshTokenRepository, never()).revokeFamily(any());
  }
}
