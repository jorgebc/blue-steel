package com.bluesteel.adapters.in.web.proposal;

import com.bluesteel.application.model.proposal.ProposalDecisionResult;
import java.util.UUID;

/**
 * Response body for a GM decision. {@code resultingEntityVersionId} is the id of the entity version
 * written on approval (D-107), or null when the proposal was vetoed.
 */
public record ProposalDecisionResponse(UUID resultingEntityVersionId) {

  public static ProposalDecisionResponse from(ProposalDecisionResult result) {
    return new ProposalDecisionResponse(result.resultingEntityVersionId());
  }
}
