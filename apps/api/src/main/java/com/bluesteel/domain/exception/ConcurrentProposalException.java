package com.bluesteel.domain.exception;

/**
 * Thrown when a new proposal is submitted while an {@code open} or {@code cosigned} proposal
 * already targets the same entity (D-106).
 */
public class ConcurrentProposalException extends DomainException {

  public ConcurrentProposalException(String message) {
    super(message);
  }
}
