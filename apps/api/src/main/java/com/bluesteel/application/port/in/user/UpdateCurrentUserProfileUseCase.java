package com.bluesteel.application.port.in.user;

import com.bluesteel.application.model.user.UpdateProfileCommand;

/**
 * Updates the caller's profile/settings fields (display name, accent, UI locale, theme) with
 * partial-merge semantics — a {@code null} command field leaves the stored value unchanged (D-113).
 */
public interface UpdateCurrentUserProfileUseCase {

  void update(UpdateProfileCommand command);
}
