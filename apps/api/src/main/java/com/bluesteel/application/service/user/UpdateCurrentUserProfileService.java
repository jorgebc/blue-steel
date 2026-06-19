package com.bluesteel.application.service.user;

import com.bluesteel.application.model.user.UpdateProfileCommand;
import com.bluesteel.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.exception.UserNotFoundException;
import com.bluesteel.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Replaces the caller's profile/settings fields, leaving identity/credentials untouched. */
@Service
public class UpdateCurrentUserProfileService implements UpdateCurrentUserProfileUseCase {

  private static final Logger log = LoggerFactory.getLogger(UpdateCurrentUserProfileService.class);

  private final UserRepository userRepository;

  public UpdateCurrentUserProfileService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional
  public void update(UpdateProfileCommand command) {
    User user =
        userRepository
            .findById(command.callerId())
            .orElseThrow(() -> new UserNotFoundException("User not found"));

    userRepository.save(
        user.withUpdatedProfile(
            command.displayName(),
            command.avatarAccentColor(),
            command.uiLocale(),
            command.theme()));
    log.info("Profile updated for userId={}", command.callerId());
  }
}
