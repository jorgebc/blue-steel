package com.bluesteel.application.port.out.auth;

import com.bluesteel.application.model.auth.JwtClaims;
import java.time.Duration;
import java.util.UUID;

/** Contract for issuing and validating HS256 JWTs. */
public interface JwtPort {

  String issue(UUID userId, boolean isAdmin, Duration ttl);

  /** Parses and validates the token. Throws {@code JwtValidationException} if invalid/expired. */
  JwtClaims validate(String token);
}
