package com.bluesteel.application.port.in.user;

import com.bluesteel.application.model.user.UpdateProfileCommand;

/** Replaces the caller's profile/settings fields (display name, accent, UI locale, theme). */
public interface UpdateCurrentUserProfileUseCase {

  void update(UpdateProfileCommand command);
}
