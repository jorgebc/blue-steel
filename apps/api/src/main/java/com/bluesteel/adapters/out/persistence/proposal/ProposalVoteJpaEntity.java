package com.bluesteel.adapters.out.persistence.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proposal_votes")
class ProposalVoteJpaEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "proposal_id", nullable = false)
  private UUID proposalId;

  @Column(name = "voter_id", nullable = false)
  private UUID voterId;

  @Column(name = "vote", nullable = false)
  private String vote;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ProposalVoteJpaEntity() {}

  ProposalVoteJpaEntity(UUID id, UUID proposalId, UUID voterId, String vote, Instant createdAt) {
    this.id = id;
    this.proposalId = proposalId;
    this.voterId = voterId;
    this.vote = vote;
    this.createdAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getProposalId() {
    return proposalId;
  }

  UUID getVoterId() {
    return voterId;
  }

  String getVote() {
    return vote;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
