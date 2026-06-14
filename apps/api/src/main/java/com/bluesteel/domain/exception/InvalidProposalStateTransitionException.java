package com.bluesteel.domain.exception;

/** Thrown when a proposal transition is attempted from an invalid source state. */
public class InvalidProposalStateTransitionException extends DomainException {

  public InvalidProposalStateTransitionException(String message) {
    super(message);
  }
}
