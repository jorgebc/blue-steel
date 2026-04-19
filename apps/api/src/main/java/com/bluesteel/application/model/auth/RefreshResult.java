package com.bluesteel.application.model.auth;

import java.util.UUID;

/** Returned by {@code RefreshTokenUseCase} on a successful token rotation. */
public record RefreshResult(
    String accessToken, String rawRefreshToken, UUID userId, boolean isAdmin) {}
