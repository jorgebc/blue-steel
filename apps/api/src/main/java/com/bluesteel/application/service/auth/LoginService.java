package com.bluesteel.application.service.auth;

import com.bluesteel.application.model.auth.LoginCommand;
import com.bluesteel.application.model.auth.LoginResult;
import com.bluesteel.application.port.in.auth.LoginUseCase;
import com.bluesteel.application.port.out.auth.JwtPort;
import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.auth.RefreshToken;
import com.bluesteel.domain.exception.InvalidCredentialsException;
import com.bluesteel.domain.user.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates credentials and issues an access JWT + rotating refresh token pair (D-059). */
@Service
public class LoginService implements LoginUseCase {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);
  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtPort jwtPort;
  private final PasswordEncoder passwordEncoder;

  public LoginService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      JwtPort jwtPort,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtPort = jwtPort;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public LoginResult login(LoginCommand command) {
    User user =
        userRepository
            .findByEmail(command.email())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

    if (!passwordEncoder.matches(command.password(), user.passwordHash())) {
      throw new InvalidCredentialsException("Invalid email or password");
    }

    String accessToken = jwtPort.issue(user.id(), user.isAdmin(), ACCESS_TOKEN_TTL);
    String rawRefreshToken = generateRawToken();
    UUID familyId = UUID.randomUUID();

    RefreshToken refreshToken = RefreshToken.create(user.id(), familyId, rawRefreshToken);
    refreshTokenRepository.save(refreshToken);

    log.info("Login successful for userId={}", user.id());
    return new LoginResult(
        accessToken, rawRefreshToken, user.id(), user.isAdmin(), user.forcePasswordChange());
  }

  private String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
