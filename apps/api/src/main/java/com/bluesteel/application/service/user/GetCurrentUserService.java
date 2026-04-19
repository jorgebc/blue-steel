package com.bluesteel.application.service.user;

import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.exception.UserNotFoundException;
import com.bluesteel.domain.user.User;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Retrieves the profile of the currently authenticated user. */
@Service
public class GetCurrentUserService implements GetCurrentUserUseCase {

  private final UserRepository userRepository;

  public GetCurrentUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserProfile getCurrentUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
    return new UserProfile(user.id(), user.email(), user.isAdmin(), user.forcePasswordChange());
  }
}
