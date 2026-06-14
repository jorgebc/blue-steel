package com.bluesteel.domain.exception;

/**
 * Thrown when a member casts a second vote on a proposal they have already voted on, violating the
 * one-vote-per-voter invariant (D-109).
 */
public class DuplicateVoteException extends DomainException {

  public DuplicateVoteException(String message) {
    super(message);
  }
}
