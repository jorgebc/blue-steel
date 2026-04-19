package com.bluesteel.application.service.user;

import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.out.session.SessionRecoveryPort;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
  private final SessionRecoveryPort sessionRecoveryPort;

  @Value("${admin.email}")
  private String adminEmail;

  @Value("${admin.password}")
  private String adminPassword;

  public AdminBootstrapService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      SessionRecoveryPort sessionRecoveryPort) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionRecoveryPort = sessionRecoveryPort;
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

  private void recoverStuckSessions() {
    int updated = sessionRecoveryPort.recoverStuckSessions();
    if (updated > 0) {
      log.warn("Recovered {} session(s) stuck in processing state (PIPELINE_INTERRUPTED)", updated);
    }
  }
}
