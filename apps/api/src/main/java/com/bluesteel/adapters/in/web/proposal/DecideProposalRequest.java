package com.bluesteel.adapters.in.web.proposal;

import com.bluesteel.application.model.proposal.ProposalDecisionType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request body for a GM decision. {@code editedDelta} is the optional GM-edited delta applied in
 * place of the author's on approval (D-110); it is null/ignored on a {@code REJECT}.
 */
public record DecideProposalRequest(
    @NotNull ProposalDecisionType decision, Map<String, Object> editedDelta) {}
