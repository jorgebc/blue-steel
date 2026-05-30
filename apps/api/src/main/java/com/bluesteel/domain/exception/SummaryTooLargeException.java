package com.bluesteel.domain.exception;

/** Thrown when the submitted session summary exceeds the configured token budget. */
public class SummaryTooLargeException extends DomainException {

  public SummaryTooLargeException(String message) {
    super(message);
  }
}
