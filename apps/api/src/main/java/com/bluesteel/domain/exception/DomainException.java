package com.bluesteel.domain.exception;

/** Base unchecked exception for domain invariant violations. */
public class DomainException extends RuntimeException {

  public DomainException(String message) {
    super(message);
  }
}
