package com.bluesteel.application.model.user;

import java.util.UUID;

/** Read model returned by GetCurrentUserUseCase. */
public record UserProfile(UUID id, String email, boolean isAdmin, boolean forcePasswordChange) {}
