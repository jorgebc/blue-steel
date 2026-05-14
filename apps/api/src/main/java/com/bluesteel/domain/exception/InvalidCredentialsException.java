package com.bluesteel.domain.exception;

/** Thrown when email/password credentials cannot be verified during login. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
