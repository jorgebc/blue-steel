package com.bluesteel.application.port.in.proposal;

import com.bluesteel.application.model.proposal.CoSignProposalCommand;
import com.bluesteel.application.model.proposal.ProposalView;

/** Driving port for casting a co-sign vote on a proposal (D-017). */
public interface CoSignProposalUseCase {

  /**
   * Records a co-sign vote by a non-author member and, on the first co-sign, transitions the
   * proposal {@code OPEN → COSIGNED}. Returns the updated read model.
   */
  ProposalView coSign(CoSignProposalCommand command);
}
