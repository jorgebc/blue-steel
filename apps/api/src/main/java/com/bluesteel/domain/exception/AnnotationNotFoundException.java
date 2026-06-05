package com.bluesteel.domain.exception;

/** Thrown when an annotation is referenced by id but does not exist. */
public class AnnotationNotFoundException extends DomainException {

  public AnnotationNotFoundException(String message) {
    super(message);
  }
}
