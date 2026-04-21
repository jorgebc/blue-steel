package com.bluesteel.application.port.in.auth;

import com.bluesteel.application.model.auth.JwtClaims;

/**
 * Validates a raw JWT string and returns the extracted claims. Inbound port consumed by the
 * security filter chain; implemented by the outbound JWT adapter.
 */
public interface JwtValidationPort {

  /** Parses and validates the token. Throws {@code JwtValidationException} if invalid/expired. */
  JwtClaims validate(String token);
}
