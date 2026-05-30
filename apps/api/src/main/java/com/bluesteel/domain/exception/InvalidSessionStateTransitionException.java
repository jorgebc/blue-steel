package com.bluesteel.domain.exception;

/** Thrown when a session transition is attempted from an invalid source state. */
public class InvalidSessionStateTransitionException extends DomainException {

  public InvalidSessionStateTransitionException(String message) {
    super(message);
  }
}
