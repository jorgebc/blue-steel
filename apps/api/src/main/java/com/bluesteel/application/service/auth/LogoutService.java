package com.bluesteel.application.service.auth;

import com.bluesteel.application.port.in.auth.LogoutUseCase;
import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.domain.auth.RefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revokes the refresh token family on logout. */
@Service
public class LogoutService implements LogoutUseCase {

  private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

  private final RefreshTokenRepository refreshTokenRepository;

  public LogoutService(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Override
  @Transactional
  public void logout(String rawRefreshToken) {
    String hash = RefreshToken.sha256(rawRefreshToken);
    refreshTokenRepository
        .findByTokenHash(hash)
        .ifPresentOrElse(
            token -> {
              refreshTokenRepository.revokeFamily(token.familyId());
              log.info(
                  "Logout: revoked familyId={} for userId={}", token.familyId(), token.userId());
            },
            () -> log.warn("Logout called with unknown refresh token — no-op"));
  }
}
