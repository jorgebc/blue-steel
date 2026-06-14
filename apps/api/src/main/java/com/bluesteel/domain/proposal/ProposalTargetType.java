package com.bluesteel.domain.proposal;

/**
 * Entity type a proposal targets. v2 supports actor and space only; events and relations are
 * deferred beyond v2 (D-108).
 */
public enum ProposalTargetType {
  ACTOR,
  SPACE
}
