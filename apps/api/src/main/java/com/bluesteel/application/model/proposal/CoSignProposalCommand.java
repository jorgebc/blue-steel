package com.bluesteel.application.model.proposal;

import java.util.UUID;

/** Command to cast a co-sign vote on a proposal (D-017). */
public record CoSignProposalCommand(UUID callerId, UUID campaignId, UUID proposalId) {}
