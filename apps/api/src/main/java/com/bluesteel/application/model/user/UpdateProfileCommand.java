package com.bluesteel.application.model.user;

import java.util.UUID;

/** Command for the current-user profile/settings update use case. */
public record UpdateProfileCommand(
    UUID callerId, String displayName, String avatarAccentColor, String uiLocale, String theme) {}
