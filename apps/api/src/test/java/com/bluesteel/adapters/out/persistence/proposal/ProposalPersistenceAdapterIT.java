package com.bluesteel.adapters.out.persistence.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.session.SessionPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.application.model.proposal.ProposalFilter;
import com.bluesteel.application.model.proposal.ProposalPage;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.proposal.ProposalVote;
import com.bluesteel.domain.proposal.VoteKind;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("ProposalPersistenceAdapter")
class ProposalPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private ProposalPersistenceAdapter adapter;
  @Autowired private ProposalVoteJpaRepository voteRepository;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
  private static final Instant EXPIRES = NOW.plus(30, ChronoUnit.DAYS);

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
  @DisplayName("should save and find a proposal by id, round-tripping provenance fields")
  void saveAndFindById() {
    UUID targetId = UUID.randomUUID();
    Proposal proposal = openProposal(ProposalTargetType.ACTOR, targetId);

    adapter.save(proposal);
    Optional<Proposal> found = adapter.findById(proposal.id());

    assertThat(found).isPresent();
    Proposal p = found.get();
    assertThat(p.campaignId()).isEqualTo(campaignId);
    assertThat(p.targetType()).isEqualTo(ProposalTargetType.ACTOR);
    assertThat(p.targetId()).isEqualTo(targetId);
    assertThat(p.ownerId()).isEqualTo(ownerId);
    assertThat(p.sessionId()).isEqualTo(sessionId);
    // jsonb normalizes whitespace, so assert on content rather than exact formatting
    assertThat(p.proposedDelta()).contains("\"name\"").contains("\"Gloria\"");
    assertThat(p.status()).isEqualTo(ProposalStatus.OPEN);
    assertThat(p.expiresAt()).isEqualTo(EXPIRES);
    assertThat(p.resultingEntityVersionId()).isNull();
  }

  @Test
  @DisplayName("should return empty when a proposal is not found by id")
  void findById_notFound_returnsEmpty() {
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("should find proposals targeting a specific entity")
  void findByTarget_returnsMatching() {
    UUID actorId = UUID.randomUUID();
    adapter.save(openProposal(ProposalTargetType.ACTOR, actorId));
    adapter.save(openProposal(ProposalTargetType.SPACE, UUID.randomUUID()));

    List<Proposal> found = adapter.findByTarget(campaignId, ProposalTargetType.ACTOR, actorId);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).targetId()).isEqualTo(actorId);
    assertThat(found.get(0).targetType()).isEqualTo(ProposalTargetType.ACTOR);
  }

  @Test
  @DisplayName("should list all campaign proposals when no status filter is applied")
  void findByCampaign_noFilter_returnsAll() {
    adapter.save(openProposal(ProposalTargetType.ACTOR, UUID.randomUUID()));
    adapter.save(openProposal(ProposalTargetType.SPACE, UUID.randomUUID()));

    List<Proposal> found =
        adapter.findByCampaign(campaignId, ProposalFilter.any(), new ProposalPage(0, 10));

    assertThat(found).hasSize(2);
  }

  @Test
  @DisplayName("should narrow campaign proposals by status filter")
  void findByCampaign_statusFilter_returnsMatching() {
    Proposal cosigned = openProposal(ProposalTargetType.ACTOR, UUID.randomUUID());
    cosigned.coSign();
    adapter.save(cosigned);
    adapter.save(openProposal(ProposalTargetType.SPACE, UUID.randomUUID()));

    List<Proposal> open =
        adapter.findByCampaign(
            campaignId, new ProposalFilter(ProposalStatus.OPEN), new ProposalPage(0, 10));
    List<Proposal> cosignedList =
        adapter.findByCampaign(
            campaignId, new ProposalFilter(ProposalStatus.COSIGNED), new ProposalPage(0, 10));

    assertThat(open).hasSize(1);
    assertThat(open.get(0).status()).isEqualTo(ProposalStatus.OPEN);
    assertThat(cosignedList).hasSize(1);
    assertThat(cosignedList.get(0).status()).isEqualTo(ProposalStatus.COSIGNED);
  }

  @Test
  @DisplayName("should paginate campaign proposals by page and size")
  void findByCampaign_paginates() {
    for (int i = 0; i < 3; i++) {
      adapter.save(openProposal(ProposalTargetType.ACTOR, UUID.randomUUID()));
    }

    assertThat(adapter.findByCampaign(campaignId, ProposalFilter.any(), new ProposalPage(0, 2)))
        .hasSize(2);
    assertThat(adapter.findByCampaign(campaignId, ProposalFilter.any(), new ProposalPage(1, 2)))
        .hasSize(1);
  }

  @Test
  @DisplayName("should count campaign proposals with and without a status filter")
  void countByCampaign_respectsFilter() {
    Proposal cosigned = openProposal(ProposalTargetType.ACTOR, UUID.randomUUID());
    cosigned.coSign();
    adapter.save(cosigned);
    adapter.save(openProposal(ProposalTargetType.SPACE, UUID.randomUUID()));

    assertThat(adapter.countByCampaign(campaignId, ProposalFilter.any())).isEqualTo(2);
    assertThat(adapter.countByCampaign(campaignId, new ProposalFilter(ProposalStatus.OPEN)))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("should report an open proposal exists for a target (D-106)")
  void existsOpenForTarget_open_returnsTrue() {
    UUID actorId = UUID.randomUUID();
    adapter.save(openProposal(ProposalTargetType.ACTOR, actorId));

    assertThat(adapter.existsOpenForTarget(campaignId, ProposalTargetType.ACTOR, actorId)).isTrue();
  }

  @Test
  @DisplayName("should report a cosigned proposal exists for a target (D-106)")
  void existsOpenForTarget_cosigned_returnsTrue() {
    UUID actorId = UUID.randomUUID();
    Proposal proposal = openProposal(ProposalTargetType.ACTOR, actorId);
    proposal.coSign();
    adapter.save(proposal);

    assertThat(adapter.existsOpenForTarget(campaignId, ProposalTargetType.ACTOR, actorId)).isTrue();
  }

  @Test
  @DisplayName("should not report an open proposal once it reaches a terminal status (D-106)")
  void existsOpenForTarget_terminal_returnsFalse() {
    UUID actorId = UUID.randomUUID();
    Proposal proposal = openProposal(ProposalTargetType.ACTOR, actorId);
    proposal.coSign();
    proposal.approve(UUID.randomUUID());
    adapter.save(proposal);

    assertThat(adapter.existsOpenForTarget(campaignId, ProposalTargetType.ACTOR, actorId))
        .isFalse();
  }

  @Test
  @DisplayName("should persist a vote")
  void saveVote_persists() {
    Proposal proposal = openProposal(ProposalTargetType.ACTOR, UUID.randomUUID());
    adapter.save(proposal);

    ProposalVote vote =
        ProposalVote.create(UUID.randomUUID(), proposal.id(), ownerId, VoteKind.COSIGN, NOW);
    adapter.saveVote(vote);

    assertThat(voteRepository.findById(vote.id())).isPresent();
  }

  @Test
  @DisplayName(
      "should throw when the same voter votes twice on a proposal (uidx_proposal_votes_proposal_voter, D-109)")
  void saveVote_duplicate_throwsDataIntegrityViolation() {
    Proposal proposal = openProposal(ProposalTargetType.ACTOR, UUID.randomUUID());
    adapter.save(proposal);
    UUID voterId = savedUser();

    adapter.saveVote(
        ProposalVote.create(UUID.randomUUID(), proposal.id(), voterId, VoteKind.COSIGN, NOW));
    ProposalVote duplicate =
        ProposalVote.create(UUID.randomUUID(), proposal.id(), voterId, VoteKind.APPROVE, NOW);

    assertThatThrownBy(() -> adapter.saveVote(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("should persist the resulting entity version id stamped on approval (D-107)")
  void save_afterApprove_persistsResultingVersion() {
    Proposal proposal = openProposal(ProposalTargetType.ACTOR, UUID.randomUUID());
    adapter.save(proposal);

    UUID versionId = UUID.randomUUID();
    proposal.coSign();
    proposal.approve(versionId);
    adapter.save(proposal);

    Optional<Proposal> found = adapter.findById(proposal.id());
    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(found.get().resultingEntityVersionId()).isEqualTo(versionId);
  }

  private Proposal openProposal(ProposalTargetType targetType, UUID targetId) {
    return Proposal.create(
        UUID.randomUUID(),
        campaignId,
        targetType,
        targetId,
        ownerId,
        sessionId,
        "{\"name\":\"Gloria\"}",
        EXPIRES,
        NOW);
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
