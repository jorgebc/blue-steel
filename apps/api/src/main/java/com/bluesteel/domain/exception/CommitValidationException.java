package com.bluesteel.domain.exception;

/** Thrown when the commit payload fails one of the 8 mandatory checks (D-078–D-081). */
public class CommitValidationException extends DomainException {

  private final String code;

  public CommitValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
