package com.bluesteel.adapters.out.persistence.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.session.SessionPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("ProposalExpiryAdapter")
class ProposalExpiryAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private ProposalExpiryAdapter expiryAdapter;
  @Autowired private ProposalPersistenceAdapter proposalAdapter;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
  private static final Instant PAST = NOW.minus(1, ChronoUnit.DAYS);
  private static final Instant FUTURE = NOW.plus(30, ChronoUnit.DAYS);

  private UUID ownerId;
  private UUID campaignId;
  private UUID sessionId;

  @BeforeEach
  void setUp() {
    ownerId = savedUser();
    campaignId = savedCampaign(ownerId);
    sessionId = savedSession(campaignId, ownerId);
  }

  @Test
  @DisplayName("should flip only stale open/cosigned proposals to expired (D-019)")
  void expireProposals_flipsStaleOpenAndCosigned() {
    Proposal staleOpen = proposal(PAST, ProposalStatus.OPEN);
    Proposal staleCosigned = proposal(PAST, ProposalStatus.COSIGNED);
    Proposal freshOpen = proposal(FUTURE, ProposalStatus.OPEN);
    Proposal staleApproved = proposal(PAST, ProposalStatus.APPROVED);

    int updated = expiryAdapter.expireProposals(NOW);

    assertThat(updated).isEqualTo(2);
    assertThat(statusOf(staleOpen)).isEqualTo(ProposalStatus.EXPIRED);
    assertThat(statusOf(staleCosigned)).isEqualTo(ProposalStatus.EXPIRED);
    assertThat(statusOf(freshOpen)).isEqualTo(ProposalStatus.OPEN);
    assertThat(statusOf(staleApproved)).isEqualTo(ProposalStatus.APPROVED);
  }

  @Test
  @DisplayName("should return zero when no proposals are stale")
  void expireProposals_noneStale_returnsZero() {
    proposal(FUTURE, ProposalStatus.OPEN);

    assertThat(expiryAdapter.expireProposals(NOW)).isZero();
  }

  private ProposalStatus statusOf(Proposal proposal) {
    return proposalAdapter.findById(proposal.id()).orElseThrow().status();
  }

  private Proposal proposal(Instant expiresAt, ProposalStatus status) {
    Proposal proposal =
        Proposal.create(
            UUID.randomUUID(),
            campaignId,
            ProposalTargetType.ACTOR,
            UUID.randomUUID(),
            ownerId,
            sessionId,
            "{\"name\":\"Gloria\"}",
            expiresAt,
            NOW);
    if (status == ProposalStatus.COSIGNED) {
      proposal.coSign();
    } else if (status == ProposalStatus.APPROVED) {
      proposal.coSign();
      proposal.approve(UUID.randomUUID());
    }
    proposalAdapter.save(proposal);
    return proposal;
  }

  private UUID savedUser() {
    UUID id = UUID.randomUUID();
    userAdapter.save(
        User.create(
            id,
            id + "@example.com",
            "$2a$10$hash",
            false,
            false,
            Instant.now().truncatedTo(ChronoUnit.MICROS)));
    return id;
  }

  private UUID savedCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    campaignAdapter.save(
        Campaign.create(
            id, "Campaign-" + id, createdBy, Instant.now().truncatedTo(ChronoUnit.MICROS)));
    return id;
  }

  private UUID savedSession(UUID campaign, UUID owner) {
    UUID id = UUID.randomUUID();
    sessionAdapter.save(
        Session.create(id, campaign, owner, Instant.now().truncatedTo(ChronoUnit.MICROS)));
    return id;
  }
}
