package com.bluesteel.application.service.auth;

import com.bluesteel.application.model.auth.RefreshResult;
import com.bluesteel.application.port.in.auth.RefreshTokenUseCase;
import com.bluesteel.application.port.out.auth.JwtPort;
import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.auth.RefreshToken;
import com.bluesteel.domain.auth.RefreshTokenStatus;
import com.bluesteel.domain.exception.RefreshTokenException;
import com.bluesteel.domain.exception.UserNotFoundException;
import com.bluesteel.domain.user.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rotates refresh tokens with family-based reuse detection (D-059). */
@Service
public class RefreshTokenService implements RefreshTokenUseCase {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final JwtPort jwtPort;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      UserRepository userRepository,
      JwtPort jwtPort) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
    this.jwtPort = jwtPort;
  }

  @Override
  @Transactional
  public RefreshResult refresh(String rawRefreshToken) {
    String hash = RefreshToken.sha256(rawRefreshToken);

    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new RefreshTokenException("REFRESH_TOKEN_INVALID", "Refresh token not found"));

    if (token.isExpired()) {
      throw new RefreshTokenException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
    }

    if (token.status() == RefreshTokenStatus.CONSUMED
        || token.status() == RefreshTokenStatus.REVOKED) {
      log.warn(
          "Refresh token reuse detected for familyId={} userId={}",
          token.familyId(),
          token.userId());
      refreshTokenRepository.revokeFamily(token.familyId());
      throw new RefreshTokenException(
          "REFRESH_TOKEN_REUSE_DETECTED", "Refresh token reuse detected");
    }

    RefreshToken consumed = token.consume();
    refreshTokenRepository.save(consumed);

    User user =
        userRepository
            .findById(token.userId())
            .orElseThrow(() -> new UserNotFoundException("User not found"));

    String newAccessToken = jwtPort.issue(user.id(), user.isAdmin(), ACCESS_TOKEN_TTL);
    String newRawToken = generateRawToken();
    RefreshToken newToken = RefreshToken.create(user.id(), token.familyId(), newRawToken);
    refreshTokenRepository.save(newToken);

    log.info("Refresh token rotated for userId={}", user.id());
    return new RefreshResult(newAccessToken, newRawToken, user.id(), user.isAdmin());
  }

  private String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
