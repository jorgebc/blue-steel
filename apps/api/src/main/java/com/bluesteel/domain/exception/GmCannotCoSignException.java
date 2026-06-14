package com.bluesteel.domain.exception;

/**
 * Thrown when the GM attempts to co-sign a proposal. The GM is the decider (approve/veto, D-017),
 * so co-signing is reserved for non-author, non-GM members; letting the GM co-sign would also
 * consume their one vote slot and block the later decision vote (D-109).
 */
public class GmCannotCoSignException extends DomainException {

  public GmCannotCoSignException(String message) {
    super(message);
  }
}
