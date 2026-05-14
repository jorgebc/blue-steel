package com.bluesteel.application.port.in.auth;

import com.bluesteel.application.model.auth.RefreshResult;

public interface RefreshTokenUseCase {

  /** Rotates the refresh token. Throws {@code RefreshTokenException} on reuse or invalid token. */
  RefreshResult refresh(String rawRefreshToken);
}
