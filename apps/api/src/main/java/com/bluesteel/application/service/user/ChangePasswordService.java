package com.bluesteel.application.service.user;

import com.bluesteel.application.model.user.ChangePasswordCommand;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.exception.InvalidPasswordException;
import com.bluesteel.domain.exception.UserNotFoundException;
import com.bluesteel.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates the current password and replaces it, clearing the force_password_change flag. */
@Service
public class ChangePasswordService implements ChangePasswordUseCase {

  private static final Logger log = LoggerFactory.getLogger(ChangePasswordService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public ChangePasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void change(ChangePasswordCommand command) {
    User user =
        userRepository
            .findById(command.callerId())
            .orElseThrow(() -> new UserNotFoundException("User not found"));

    if (!passwordEncoder.matches(command.currentPassword(), user.passwordHash())) {
      throw new InvalidPasswordException("Current password is incorrect");
    }

    String newHash = passwordEncoder.encode(command.newPassword());
    userRepository.save(user.withUpdatedPassword(newHash));
    log.info("Password changed for userId={}", command.callerId());
  }
}
