package com.bluesteel.application.model.user;

import java.util.UUID;

/** Command for the platform-level user invitation use case. */
public record InvitePlatformUserCommand(UUID callerId, boolean callerIsAdmin, String email) {}
