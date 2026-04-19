package com.bluesteel.domain.exception;

/** Thrown when a user lookup finds no matching record. */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(String message) {
    super(message);
  }
}
