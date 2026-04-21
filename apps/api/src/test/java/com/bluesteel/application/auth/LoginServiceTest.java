package com.bluesteel.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.auth.LoginCommand;
import com.bluesteel.application.model.auth.LoginResult;
import com.bluesteel.application.port.out.auth.JwtPort;
import com.bluesteel.application.port.out.auth.RefreshTokenRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.auth.LoginService;
import com.bluesteel.domain.auth.RefreshToken;
import com.bluesteel.domain.exception.InvalidCredentialsException;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService")
class LoginServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private JwtPort jwtPort;
  @Mock private PasswordEncoder passwordEncoder;

  private LoginService service;

  @BeforeEach
  void setUp() {
    service = new LoginService(userRepository, refreshTokenRepository, jwtPort, passwordEncoder);
  }

  @Test
  @DisplayName("should throw InvalidCredentialsException when email is not found")
  void login_unknownEmail_throwsInvalidCredentials() {
    when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginCommand("unknown@example.com", "pass")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("should throw InvalidCredentialsException when password does not match")
  void login_wrongPassword_throwsInvalidCredentials() {
    UUID userId = UUID.randomUUID();
    User user = User.create(userId, "user@example.com", "$2a$hash", false, false, Instant.now());
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginCommand("user@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("should return access token and save refresh token on successful login")
  void login_validCredentials_returnsTokensAndSavesRefreshToken() {
    UUID userId = UUID.randomUUID();
    User user = User.create(userId, "user@example.com", "$2a$hash", false, true, Instant.now());
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(eq("password"), any())).thenReturn(true);
    when(jwtPort.issue(eq(userId), eq(false), any())).thenReturn("access-token-123");

    LoginResult result = service.login(new LoginCommand("user@example.com", "password"));

    assertThat(result.accessToken()).isEqualTo("access-token-123");
    assertThat(result.forcePasswordChange()).isTrue();
    assertThat(result.rawRefreshToken()).isNotNull().isNotBlank();
    assertThat(result.userId()).isEqualTo(userId);

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());
    RefreshToken saved = captor.getValue();
    assertThat(saved.userId()).isEqualTo(userId);
    assertThat(saved.tokenHash()).isEqualTo(RefreshToken.sha256(result.rawRefreshToken()));
  }
}
