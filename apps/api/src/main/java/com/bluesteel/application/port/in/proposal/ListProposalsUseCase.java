package com.bluesteel.application.port.in.proposal;

import com.bluesteel.application.model.proposal.ProposalListView;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import java.util.UUID;

/** Driving port for listing a campaign's proposals for any campaign member. */
public interface ListProposalsUseCase {

  /**
   * Returns proposals for the campaign, newest first, optionally narrowed by {@code status} and/or
   * a specific target entity. When both {@code targetType} and {@code targetId} are non-null the
   * result is scoped to that entity and returned unpaginated (an entity has few proposals); a null
   * target uses zero-based offset pagination across the whole campaign. A null {@code status}
   * returns proposals of every status. {@code size} is clamped to a sane range by the
   * implementation.
   */
  ProposalListView list(
      UUID campaignId,
      UUID callerId,
      ProposalStatus status,
      ProposalTargetType targetType,
      UUID targetId,
      int page,
      int size);
}
