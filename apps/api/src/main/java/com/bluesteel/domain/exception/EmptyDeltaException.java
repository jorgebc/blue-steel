package com.bluesteel.domain.exception;

/** Thrown when a proposal's {@code proposed_delta} is missing or an empty change set (D-104). */
public class EmptyDeltaException extends DomainException {

  public EmptyDeltaException(String message) {
    super(message);
  }
}
