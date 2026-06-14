package com.bluesteel.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.domain.exception.InvalidProposalStateTransitionException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Proposal")
class ProposalTest {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
  private static final Instant EXPIRES = NOW.plus(30, ChronoUnit.DAYS);

  private static Proposal newProposal() {
    return Proposal.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        ProposalTargetType.ACTOR,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "{\"name\":\"Gloria\"}",
        EXPIRES,
        NOW);
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("should start a new proposal in OPEN status with no resulting version")
    void create_startsOpen() {
      Proposal proposal = newProposal();

      assertThat(proposal.status()).isEqualTo(ProposalStatus.OPEN);
      assertThat(proposal.resultingEntityVersionId()).isNull();
    }

    @Test
    @DisplayName("should retain all provided fields")
    void create_retainsFields() {
      UUID id = UUID.randomUUID();
      UUID campaignId = UUID.randomUUID();
      UUID targetId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();

      Proposal proposal =
          Proposal.create(
              id,
              campaignId,
              ProposalTargetType.SPACE,
              targetId,
              ownerId,
              sessionId,
              "{\"description\":\"A tavern\"}",
              EXPIRES,
              NOW);

      assertThat(proposal.id()).isEqualTo(id);
      assertThat(proposal.campaignId()).isEqualTo(campaignId);
      assertThat(proposal.targetType()).isEqualTo(ProposalTargetType.SPACE);
      assertThat(proposal.targetId()).isEqualTo(targetId);
      assertThat(proposal.ownerId()).isEqualTo(ownerId);
      assertThat(proposal.sessionId()).isEqualTo(sessionId);
      assertThat(proposal.proposedDelta()).isEqualTo("{\"description\":\"A tavern\"}");
      assertThat(proposal.expiresAt()).isEqualTo(EXPIRES);
      assertThat(proposal.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("should reject a null id")
    void create_nullId_throws() {
      assertThatThrownBy(
              () ->
                  Proposal.create(
                      null,
                      UUID.randomUUID(),
                      ProposalTargetType.ACTOR,
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      "{}",
                      EXPIRES,
                      NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("id");
    }

    @Test
    @DisplayName("should reject a null target type")
    void create_nullTargetType_throws() {
      assertThatThrownBy(
              () ->
                  Proposal.create(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      null,
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      "{}",
                      EXPIRES,
                      NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("targetType");
    }

    @Test
    @DisplayName("should reject a null provenance session id (D-107)")
    void create_nullSessionId_throws() {
      assertThatThrownBy(
              () ->
                  Proposal.create(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ProposalTargetType.ACTOR,
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      null,
                      "{}",
                      EXPIRES,
                      NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sessionId");
    }

    @Test
    @DisplayName("should reject a blank proposed delta (D-104)")
    void create_blankDelta_throws() {
      assertThatThrownBy(
              () ->
                  Proposal.create(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      ProposalTargetType.ACTOR,
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      "   ",
                      EXPIRES,
                      NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("proposedDelta");
    }
  }

  @Nested
  @DisplayName("when OPEN")
  class WhenOpen {

    @Test
    @DisplayName("should transition to COSIGNED on coSign")
    void coSign_movesToCosigned() {
      Proposal proposal = newProposal();

      proposal.coSign();

      assertThat(proposal.status()).isEqualTo(ProposalStatus.COSIGNED);
    }

    @Test
    @DisplayName("should transition to EXPIRED on expire")
    void expire_movesToExpired() {
      Proposal proposal = newProposal();

      proposal.expire();

      assertThat(proposal.status()).isEqualTo(ProposalStatus.EXPIRED);
    }

    @Test
    @DisplayName("should reject approve before co-sign")
    void approve_whenOpen_throws() {
      Proposal proposal = newProposal();

      assertThatThrownBy(() -> proposal.approve(UUID.randomUUID()))
          .isInstanceOf(InvalidProposalStateTransitionException.class);
    }

    @Test
    @DisplayName("should reject reject before co-sign")
    void reject_whenOpen_throws() {
      Proposal proposal = newProposal();

      assertThatThrownBy(proposal::reject)
          .isInstanceOf(InvalidProposalStateTransitionException.class);
    }
  }

  @Nested
  @DisplayName("when COSIGNED")
  class WhenCosigned {

    private Proposal cosigned() {
      Proposal proposal = newProposal();
      proposal.coSign();
      return proposal;
    }

    @Test
    @DisplayName("should transition to APPROVED and stamp the resulting version id (D-107)")
    void approve_movesToApprovedAndStampsVersion() {
      Proposal proposal = cosigned();
      UUID versionId = UUID.randomUUID();

      proposal.approve(versionId);

      assertThat(proposal.status()).isEqualTo(ProposalStatus.APPROVED);
      assertThat(proposal.resultingEntityVersionId()).isEqualTo(versionId);
    }

    @Test
    @DisplayName("should reject approve with a null resulting version id")
    void approve_nullVersion_throws() {
      Proposal proposal = cosigned();

      assertThatThrownBy(() -> proposal.approve(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("resultingEntityVersionId");
    }

    @Test
    @DisplayName("should transition to REJECTED on reject")
    void reject_movesToRejected() {
      Proposal proposal = cosigned();

      proposal.reject();

      assertThat(proposal.status()).isEqualTo(ProposalStatus.REJECTED);
    }

    @Test
    @DisplayName("should transition to EXPIRED on expire")
    void expire_movesToExpired() {
      Proposal proposal = cosigned();

      proposal.expire();

      assertThat(proposal.status()).isEqualTo(ProposalStatus.EXPIRED);
    }

    @Test
    @DisplayName("should reject a second co-sign")
    void coSign_whenCosigned_throws() {
      Proposal proposal = cosigned();

      assertThatThrownBy(proposal::coSign)
          .isInstanceOf(InvalidProposalStateTransitionException.class);
    }
  }

  @Nested
  @DisplayName("when terminal")
  class WhenTerminal {

    @Test
    @DisplayName("should reject any transition out of APPROVED")
    void approved_isTerminal() {
      Proposal proposal = newProposal();
      proposal.coSign();
      proposal.approve(UUID.randomUUID());

      assertThatThrownBy(proposal::reject)
          .isInstanceOf(InvalidProposalStateTransitionException.class);
      assertThatThrownBy(proposal::expire)
          .isInstanceOf(InvalidProposalStateTransitionException.class);
    }

    @Test
    @DisplayName("should reject expire after REJECTED")
    void rejected_cannotExpire() {
      Proposal proposal = newProposal();
      proposal.coSign();
      proposal.reject();

      assertThatThrownBy(proposal::expire)
          .isInstanceOf(InvalidProposalStateTransitionException.class);
    }

    @Test
    @DisplayName("should reject co-sign after EXPIRED")
    void expired_cannotCosign() {
      Proposal proposal = newProposal();
      proposal.expire();

      assertThatThrownBy(proposal::coSign)
          .isInstanceOf(InvalidProposalStateTransitionException.class);
    }
  }
}
