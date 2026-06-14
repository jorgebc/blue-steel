package com.bluesteel.application.model.proposal;

import java.util.UUID;

/**
 * Outcome of a GM decision. {@code resultingEntityVersionId} is the id of the entity version
 * written on approval (D-107), or null when the proposal was vetoed.
 */
public record ProposalDecisionResult(UUID resultingEntityVersionId) {}
