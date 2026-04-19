package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.port.out.session.SessionRecoveryPort;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.user.AdminBootstrapService;
import com.bluesteel.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBootstrapService")
class AdminBootstrapServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionRecoveryPort sessionRecoveryPort;

  private AdminBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new AdminBootstrapService(userRepository, passwordEncoder, sessionRecoveryPort);
    ReflectionTestUtils.setField(service, "adminEmail", "admin@example.com");
    ReflectionTestUtils.setField(service, "adminPassword", "SecurePass!123456");
  }

  @Test
  @DisplayName("should seed admin user when none exists")
  void bootstrap_noAdmin_seedsAdminUser() {
    when(userRepository.existsByIsAdminTrue()).thenReturn(false);
    when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashedpassword");

    service.bootstrap();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.email()).isEqualTo("admin@example.com");
    assertThat(saved.isAdmin()).isTrue();
    assertThat(saved.forcePasswordChange()).isFalse();
    assertThat(saved.passwordHash()).isEqualTo("$2a$10$hashedpassword");
  }

  @Test
  @DisplayName("should skip seeding when admin already exists")
  void bootstrap_adminExists_skipsSeeding() {
    when(userRepository.existsByIsAdminTrue()).thenReturn(true);

    service.bootstrap();

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("should complete without error when sessions table does not yet exist")
  void bootstrap_sessionsTableMissing_doesNotThrow() {
    when(userRepository.existsByIsAdminTrue()).thenReturn(true);
    when(sessionRecoveryPort.recoverStuckSessions()).thenReturn(-1);

    service.bootstrap();

    verify(sessionRecoveryPort).recoverStuckSessions();
  }

  @Test
  @DisplayName("should delegate stuck-session recovery to SessionRecoveryPort")
  void bootstrap_stuckSessionsExist_delegatesToPort() {
    when(userRepository.existsByIsAdminTrue()).thenReturn(true);
    when(sessionRecoveryPort.recoverStuckSessions()).thenReturn(3);

    service.bootstrap();

    verify(sessionRecoveryPort).recoverStuckSessions();
  }
}
