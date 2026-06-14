package com.bluesteel.domain.exception;

/** Thrown when a proposal is referenced by id but does not exist in the campaign. */
public class ProposalNotFoundException extends DomainException {

  public ProposalNotFoundException(String message) {
    super(message);
  }
}
