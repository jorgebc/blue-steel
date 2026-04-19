package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBootstrapService")
class AdminBootstrapServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JdbcTemplate jdbcTemplate;

  private AdminBootstrapService service;

  @BeforeEach
  void setUp() {
    service = new AdminBootstrapService(userRepository, passwordEncoder, jdbcTemplate);
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
  @DisplayName(
      "should silently swallow BadSqlGrammarException from stuck-session recovery when sessions table does not exist")
  void bootstrap_sessionsTableMissing_doesNotThrow() {
    when(userRepository.existsByIsAdminTrue()).thenReturn(true);
    when(jdbcTemplate.update(any(String.class)))
        .thenThrow(
            new BadSqlGrammarException(
                "task", "sql", new java.sql.SQLException("relation does not exist")));

    // Should not throw
    service.bootstrap();
  }

  @Test
  @DisplayName("should update stuck sessions when sessions table exists")
  void bootstrap_stuckSessionsExist_updatesThemToFailed() {
    when(userRepository.existsByIsAdminTrue()).thenReturn(true);
    when(jdbcTemplate.update(any(String.class))).thenReturn(3);

    service.bootstrap();

    verify(jdbcTemplate).update(any(String.class));
  }
}
