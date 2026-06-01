package com.bluesteel.application.service.user;

import com.bluesteel.application.model.user.InvitationResult;
import com.bluesteel.application.model.user.InvitePlatformUserCommand;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.out.email.EmailPort;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.email.InvitationEmailFactory;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a new platform user or refreshes credentials for an existing one (D-051, D-070, D-077).
 */
@Service
public class InvitePlatformUserService implements InvitePlatformUserUseCase {

  private static final Logger log = LoggerFactory.getLogger(InvitePlatformUserService.class);

  private final UserRepository userRepository;
  private final EmailPort emailPort;
  private final PasswordEncoder passwordEncoder;
  private final TemporaryPasswordGenerator temporaryPasswordGenerator;
  private final InvitationEmailFactory invitationEmailFactory;

  public InvitePlatformUserService(
      UserRepository userRepository,
      EmailPort emailPort,
      PasswordEncoder passwordEncoder,
      TemporaryPasswordGenerator temporaryPasswordGenerator,
      InvitationEmailFactory invitationEmailFactory) {
    this.userRepository = userRepository;
    this.emailPort = emailPort;
    this.passwordEncoder = passwordEncoder;
    this.temporaryPasswordGenerator = temporaryPasswordGenerator;
    this.invitationEmailFactory = invitationEmailFactory;
  }

  @Override
  @Transactional
  public InvitationResult invite(InvitePlatformUserCommand command) {
    if (!command.callerIsAdmin()) {
      throw new UnauthorizedException("Only admins may invite platform users");
    }

    String tempPassword = temporaryPasswordGenerator.generate();
    String passwordHash = passwordEncoder.encode(tempPassword);

    Optional<User> existing = userRepository.findByEmail(command.email());
    InvitationResult result;

    if (existing.isPresent()) {
      User refreshed = existing.get().withRefreshedInvitation(passwordHash);
      userRepository.save(refreshed);
      result = InvitationResult.REFRESHED;
      log.info("Re-invited existing user email={}", command.email());
    } else {
      User newUser =
          User.create(UUID.randomUUID(), command.email(), passwordHash, false, true, Instant.now());
      userRepository.save(newUser);
      result = InvitationResult.CREATED;
      log.info("Created invited user email={}", command.email());
    }

    emailPort.send(invitationEmailFactory.platformInvitation(command.email(), tempPassword));

    return result;
  }
}
