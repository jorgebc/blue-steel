package com.bluesteel.domain.proposal;

/**
 * Lifecycle status of a {@link Proposal}: {@code open → cosigned → approved | rejected}, with
 * {@code open | cosigned → expired} as a terminal timeout.
 */
public enum ProposalStatus {
  OPEN,
  COSIGNED,
  APPROVED,
  REJECTED,
  EXPIRED
}
