package com.bluesteel.domain.exception;

/** Thrown when a refresh token is invalid, expired, or reuse is detected. */
public class RefreshTokenException extends RuntimeException {

  private final String code;

  public RefreshTokenException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
