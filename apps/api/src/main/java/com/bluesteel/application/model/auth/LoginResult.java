package com.bluesteel.application.model.auth;

import java.util.UUID;

/** Returned by {@code LoginUseCase} — carries tokens plus user metadata. */
public record LoginResult(
    String accessToken,
    String rawRefreshToken,
    UUID userId,
    boolean isAdmin,
    boolean forcePasswordChange) {}
