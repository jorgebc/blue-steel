package com.bluesteel.adapters.in.web.user;

import com.bluesteel.application.model.user.UserProfile;
import java.util.UUID;

/** Response element for {@code GET /api/v1/users?email=}. */
public record UserSearchResponse(UUID id, String email) {

  static UserSearchResponse from(UserProfile profile) {
    return new UserSearchResponse(profile.id(), profile.email());
  }
}
