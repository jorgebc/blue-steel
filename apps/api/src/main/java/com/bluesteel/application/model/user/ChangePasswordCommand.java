package com.bluesteel.application.model.user;

import java.util.UUID;

/** Command for the password change use case. */
public record ChangePasswordCommand(UUID callerId, String currentPassword, String newPassword) {}
