package com.bluesteel.domain.exception;

/** Thrown when a JWT cannot be parsed, has an invalid signature, or has expired. */
public class JwtValidationException extends RuntimeException {

  public JwtValidationException(String message) {
    super(message);
  }

  public JwtValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
