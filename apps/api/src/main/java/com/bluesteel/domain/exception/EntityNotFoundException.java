package com.bluesteel.domain.exception;

/** Thrown when a world-state entity is referenced by id but does not exist in the campaign. */
public class EntityNotFoundException extends DomainException {

  public EntityNotFoundException(String message) {
    super(message);
  }
}
