package com.bluesteel.application.model.auth;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(UUID userId, boolean isAdmin, Instant expiresAt) {}
