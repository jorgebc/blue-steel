package com.bluesteel.domain.proposal;

/**
 * The kind of vote a member casts on a proposal: a player co-sign, or a GM approve/reject decision
 * (D-109).
 */
public enum VoteKind {
  COSIGN,
  APPROVE,
  REJECT
}
