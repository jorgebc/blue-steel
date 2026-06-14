package com.bluesteel.application.port.in.proposal;

import com.bluesteel.application.model.proposal.CreateProposalCommand;
import com.bluesteel.application.model.proposal.ProposalView;

/** Driving port for submitting a new proposal targeting a single world-state entity. */
public interface CreateProposalUseCase {

  /** Validates and persists a new {@code OPEN} proposal, returning its read model. */
  ProposalView create(CreateProposalCommand command);
}
