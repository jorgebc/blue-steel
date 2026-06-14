package com.bluesteel.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProposalVote")
class ProposalVoteTest {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

  @Test
  @DisplayName("should create a vote retaining all fields")
  void create_retainsFields() {
    UUID id = UUID.randomUUID();
    UUID proposalId = UUID.randomUUID();
    UUID voterId = UUID.randomUUID();

    ProposalVote vote = ProposalVote.create(id, proposalId, voterId, VoteKind.COSIGN, NOW);

    assertThat(vote.id()).isEqualTo(id);
    assertThat(vote.proposalId()).isEqualTo(proposalId);
    assertThat(vote.voterId()).isEqualTo(voterId);
    assertThat(vote.kind()).isEqualTo(VoteKind.COSIGN);
    assertThat(vote.createdAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("should reject a null id")
  void create_nullId_throws() {
    assertThatThrownBy(
            () ->
                ProposalVote.create(
                    null, UUID.randomUUID(), UUID.randomUUID(), VoteKind.APPROVE, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  @DisplayName("should reject a null proposal id")
  void create_nullProposalId_throws() {
    assertThatThrownBy(
            () ->
                ProposalVote.create(
                    UUID.randomUUID(), null, UUID.randomUUID(), VoteKind.APPROVE, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("proposalId");
  }

  @Test
  @DisplayName("should reject a null voter id")
  void create_nullVoterId_throws() {
    assertThatThrownBy(
            () ->
                ProposalVote.create(
                    UUID.randomUUID(), UUID.randomUUID(), null, VoteKind.REJECT, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("voterId");
  }

  @Test
  @DisplayName("should reject a null vote kind")
  void create_nullKind_throws() {
    assertThatThrownBy(
            () ->
                ProposalVote.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind");
  }

  @Test
  @DisplayName("should reject a null created-at timestamp")
  void create_nullCreatedAt_throws() {
    assertThatThrownBy(
            () ->
                ProposalVote.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), VoteKind.COSIGN, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createdAt");
  }
}
