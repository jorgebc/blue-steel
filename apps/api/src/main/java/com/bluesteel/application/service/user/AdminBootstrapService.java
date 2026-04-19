package com.bluesteel.application.service.user;

import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the singleton admin user on startup and recovers sessions stuck in {@code processing} state
 * (D-073, D-074).
 */
@Service
public class AdminBootstrapService implements AdminBootstrapUseCase {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbcTemplate;

  @Value("${admin.email}")
  private String adminEmail;

  @Value("${admin.password}")
  private String adminPassword;

  public AdminBootstrapService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jdbcTemplate = jdbcTemplate;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    bootstrap();
  }

  @Override
  @Transactional
  public void bootstrap() {
    seedAdmin();
    recoverStuckSessions();
  }

  private void seedAdmin() {
    if (userRepository.existsByIsAdminTrue()) {
      log.info("Admin already exists — skipping bootstrap");
      return;
    }
    String hash = passwordEncoder.encode(adminPassword);
    User admin = User.create(UUID.randomUUID(), adminEmail, hash, true, false, Instant.now());
    userRepository.save(admin);
    log.info("Admin user seeded for email={}", adminEmail);
  }

  /**
   * Bulk-transitions sessions stuck in {@code processing} to {@code failed}. If the sessions table
   * does not yet exist (Phase 1), the error is silently swallowed (D-074).
   */
  private void recoverStuckSessions() {
    try {
      int updated =
          jdbcTemplate.update(
              """
              UPDATE sessions
              SET status = 'failed',
                  failure_reason = 'PIPELINE_INTERRUPTED',
                  updated_at = now()
              WHERE status = 'processing'
              """);
      if (updated > 0) {
        log.warn(
            "Recovered {} session(s) stuck in processing state (PIPELINE_INTERRUPTED)", updated);
      }
    } catch (BadSqlGrammarException e) {
      log.debug("Sessions table not yet present — skipping stuck-session recovery");
    }
  }
}
