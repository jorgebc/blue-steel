package com.bluesteel.application.port.in.proposal;

import com.bluesteel.application.model.proposal.ProposalListView;
import com.bluesteel.domain.proposal.ProposalStatus;
import java.util.UUID;

/** Driving port for listing a campaign's proposals (offset-paginated) for any campaign member. */
public interface ListProposalsUseCase {

  /**
   * Returns the requested page of the campaign's proposals, newest first, optionally narrowed by
   * {@code status}. A null {@code status} returns proposals of every status. {@code page} is
   * zero-based; {@code size} is clamped to a sane range by the implementation.
   */
  ProposalListView list(UUID campaignId, UUID callerId, ProposalStatus status, int page, int size);
}
