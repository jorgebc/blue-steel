package com.bluesteel.application.port.in.proposal;

import com.bluesteel.application.model.proposal.DecideProposalCommand;
import com.bluesteel.application.model.proposal.ProposalDecisionResult;

/** Driving port for a GM's approve-with-edit or veto decision on a cosigned proposal (D-018). */
public interface DecideProposalUseCase {

  /**
   * Approves (writing a new entity version) or vetoes the proposal, returning the resulting entity
   * version id on approval.
   */
  ProposalDecisionResult decide(DecideProposalCommand command);
}
