package com.bluesteel.domain.exception;

/** Thrown when an authenticated caller lacks the required role for an operation. */
public class UnauthorizedException extends RuntimeException {

  public UnauthorizedException(String message) {
    super(message);
  }
}
