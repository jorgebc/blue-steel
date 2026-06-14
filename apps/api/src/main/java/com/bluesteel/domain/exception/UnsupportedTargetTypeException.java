package com.bluesteel.domain.exception;

/**
 * Thrown when a proposal targets an entity type outside the v2 scope of {@code actor}/{@code space}
 * (D-108).
 */
public class UnsupportedTargetTypeException extends DomainException {

  public UnsupportedTargetTypeException(String message) {
    super(message);
  }
}
