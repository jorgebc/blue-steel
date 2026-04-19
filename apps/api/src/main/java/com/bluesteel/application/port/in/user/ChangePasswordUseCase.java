package com.bluesteel.application.port.in.user;

import com.bluesteel.application.model.user.ChangePasswordCommand;

/**
 * Validates the caller's current password and replaces it with the new one, clearing the {@code
 * force_password_change} flag on success (D-077).
 */
public interface ChangePasswordUseCase {

  void change(ChangePasswordCommand command);
}
