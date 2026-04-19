package com.bluesteel.application.port.in.user;

import com.bluesteel.application.model.user.UserProfile;
import java.util.UUID;

/** Returns the profile of the currently authenticated user. */
public interface GetCurrentUserUseCase {

  UserProfile getCurrentUser(UUID userId);
}
