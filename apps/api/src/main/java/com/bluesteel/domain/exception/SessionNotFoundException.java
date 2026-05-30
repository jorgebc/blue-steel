package com.bluesteel.domain.exception;

/** Thrown when a session is referenced by id but does not exist. */
public class SessionNotFoundException extends DomainException {

  public SessionNotFoundException(String message) {
    super(message);
  }
}
