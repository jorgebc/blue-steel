package com.bluesteel.application.port.out.auth;

import com.bluesteel.domain.auth.RefreshToken;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract for {@link RefreshToken} aggregates. */
public interface RefreshTokenRepository {

  void save(RefreshToken token);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Marks all tokens in the family as revoked (sets used_at). */
  void revokeFamily(UUID familyId);
}
