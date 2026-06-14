package com.bluesteel.application.model.proposal;

import java.util.Map;
import java.util.UUID;

/**
 * Command capturing a GM's decision on a cosigned proposal. {@code editedDelta} is the optional
 * GM-edited delta applied in place of the author's {@code proposed_delta} when approving (D-110);
 * it is null when the GM approves the author's delta as-is, and ignored on a {@code REJECT}.
 */
public record DecideProposalCommand(
    UUID callerId,
    UUID campaignId,
    UUID proposalId,
    ProposalDecisionType decision,
    Map<String, Object> editedDelta) {}
