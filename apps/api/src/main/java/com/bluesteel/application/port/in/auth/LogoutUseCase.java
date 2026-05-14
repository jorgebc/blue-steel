package com.bluesteel.application.port.in.auth;

public interface LogoutUseCase {

  /** Revokes the entire refresh token family associated with the given raw token. */
  void logout(String rawRefreshToken);
}
