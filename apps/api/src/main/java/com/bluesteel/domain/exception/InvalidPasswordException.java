package com.bluesteel.domain.exception;

/** Thrown when the provided current password does not match the stored hash. */
public class InvalidPasswordException extends RuntimeException {

  public InvalidPasswordException(String message) {
    super(message);
  }
}
