package com.bluesteel.domain.exception;

/** Thrown when a proposal's target entity does not exist in the campaign. */
public class ProposalTargetNotFoundException extends DomainException {

  public ProposalTargetNotFoundException(String message) {
    super(message);
  }
}
