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

/**
 * Updates the caller's profile/settings fields, leaving identity/credentials untouched.
 *
 * <p>{@code PATCH} is a partial merge: a {@code null} command field means "leave unchanged", so a
 * client may send only the fields it is changing (the account-menu quick toggles send a single
 * field). The display name is the one field that can be cleared back to {@code null}: an empty
 * string is the explicit "clear" sentinel (records cannot distinguish absent from explicit-null
 * without a nullable wrapper type), whereas {@code null} preserves the current value.
 */
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
            mergeDisplayName(command.displayName(), user.displayName()),
            merge(command.avatarAccentColor(), user.avatarAccentColor()),
            merge(command.uiLocale(), user.uiLocale()),
            merge(command.theme(), user.theme())));
    log.info("Profile updated for userId={}", command.callerId());
  }

  /** Returns the requested value, or the current one when the request omits the field (null). */
  private static String merge(String requested, String current) {
    return requested != null ? requested : current;
  }

  /** As {@link #merge}, but an empty requested string clears the display name to {@code null}. */
  private static String mergeDisplayName(String requested, String current) {
    if (requested == null) return current;
    return requested.isEmpty() ? null : requested;
  }
}
