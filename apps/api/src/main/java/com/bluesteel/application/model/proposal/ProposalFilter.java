package com.bluesteel.application.model.proposal;

import com.bluesteel.domain.proposal.ProposalStatus;

/**
 * Filter for listing a campaign's proposals. A null {@code status} means no status filter — all
 * statuses are returned.
 */
public record ProposalFilter(ProposalStatus status) {

  /** A filter that matches proposals of any status. */
  public static ProposalFilter any() {
    return new ProposalFilter(null);
  }
}
