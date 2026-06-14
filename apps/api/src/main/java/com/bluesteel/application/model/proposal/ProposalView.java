package com.bluesteel.application.model.proposal;

import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a single proposal. Surfaces the provenance {@code sessionId} and the
 * approved-version back-reference {@code resultingEntityVersionId} (D-107). {@code ownerId} is the
 * authoring member (persisted as {@code author_id}, D-021).
 */
public record ProposalView(
    UUID proposalId,
    UUID campaignId,
    ProposalTargetType targetType,
    UUID targetId,
    UUID ownerId,
    ProposalStatus status,
    String proposedDelta,
    UUID sessionId,
    UUID resultingEntityVersionId,
    Instant expiresAt,
    Instant createdAt) {

  /** Projects a domain {@link Proposal} into its read model. */
  public static ProposalView from(Proposal proposal) {
    return new ProposalView(
        proposal.id(),
        proposal.campaignId(),
        proposal.targetType(),
        proposal.targetId(),
        proposal.ownerId(),
        proposal.status(),
        proposal.proposedDelta(),
        proposal.sessionId(),
        proposal.resultingEntityVersionId(),
        proposal.expiresAt(),
        proposal.createdAt());
  }
}
