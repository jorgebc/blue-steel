package com.bluesteel.domain.proposal;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-once record of a single member's vote on a proposal. The one-vote-per-voter invariant is
 * mirrored by the {@code uidx_proposal_votes_proposal_voter} unique index (D-109).
 */
public class ProposalVote {

  private final UUID id;
  private final UUID proposalId;
  private final UUID voterId;
  private final VoteKind kind;
  private final Instant createdAt;

  private ProposalVote(UUID id, UUID proposalId, UUID voterId, VoteKind kind, Instant createdAt) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    if (proposalId == null) {
      throw new IllegalArgumentException("proposalId must not be null");
    }
    if (voterId == null) {
      throw new IllegalArgumentException("voterId must not be null");
    }
    if (kind == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    this.id = id;
    this.proposalId = proposalId;
    this.voterId = voterId;
    this.kind = kind;
    this.createdAt = createdAt;
  }

  /** Creates a new write-once proposal vote. */
  public static ProposalVote create(
      UUID id, UUID proposalId, UUID voterId, VoteKind kind, Instant createdAt) {
    return new ProposalVote(id, proposalId, voterId, kind, createdAt);
  }

  public UUID id() {
    return id;
  }

  public UUID proposalId() {
    return proposalId;
  }

  public UUID voterId() {
    return voterId;
  }

  public VoteKind kind() {
    return kind;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
