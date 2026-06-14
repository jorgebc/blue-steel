package com.bluesteel.domain.exception;

/** Thrown when a proposal's author attempts to co-sign their own proposal. */
public class AuthorCannotCoSignException extends DomainException {

  public AuthorCannotCoSignException(String message) {
    super(message);
  }
}
