package com.bluesteel.adapters.out.persistence.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.campaign.CampaignMembershipAdapter;
import com.bluesteel.adapters.out.persistence.campaign.CampaignPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.session.SessionPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.adapters.out.persistence.worldstate.WorldStateAdapter;
import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.proposal.CoSignProposalCommand;
import com.bluesteel.application.model.proposal.CreateProposalCommand;
import com.bluesteel.application.model.proposal.DecideProposalCommand;
import com.bluesteel.application.model.proposal.ProposalDecisionResult;
import com.bluesteel.application.model.proposal.ProposalDecisionType;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.service.proposal.ProposalCoSignService;
import com.bluesteel.application.service.proposal.ProposalCreationService;
import com.bluesteel.application.service.proposal.ProposalDecisionService;
import com.bluesteel.application.service.proposal.ProposalExpiryScheduler;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.ConcurrentProposalException;
import com.bluesteel.domain.exception.GmCannotCoSignException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * End-to-end approval pipeline: create → co-sign → GM decide, wired against real adapters and a
 * live Postgres. Complements {@code ProposalDecisionServiceTest} (mock-only) by verifying the new
 * entity version is actually written, the {@code resulting_entity_version_id} is persisted, the
 * decision vote is recorded, and {@link SessionCommittedEvent} is published (D-107, D-109, D-110).
 */
@DisplayName("Proposal decision flow (end-to-end)")
@RecordApplicationEvents
class ProposalDecisionFlowIT extends TestcontainersPostgresBaseIT {

  @Autowired private ProposalCreationService creationService;
  @Autowired private ProposalCoSignService coSignService;
  @Autowired private ProposalDecisionService decisionService;
  @Autowired private ProposalExpiryScheduler expiryScheduler;
  @Autowired private ProposalPersistenceAdapter proposalAdapter;
  @Autowired private WorldStateAdapter worldStateAdapter;
  @Autowired private UserPersistenceAdapter userAdapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private SessionPersistenceAdapter sessionAdapter;
  @Autowired private CampaignMembershipAdapter membershipAdapter;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents events;

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

  private UUID campaignId;
  private UUID gmId;
  private UUID authorId;
  private UUID cosignerId;
  private UUID committedSessionId;

  @BeforeEach
  void setUp() {
    gmId = savedUser();
    authorId = savedUser();
    cosignerId = savedUser();
    campaignId = savedCampaign(gmId);
    savedMember(campaignId, gmId, CampaignRole.GM);
    savedMember(campaignId, authorId, CampaignRole.PLAYER);
    savedMember(campaignId, cosignerId, CampaignRole.PLAYER);
    committedSessionId = savedCommittedSession(campaignId, gmId);
  }

  @Test
  @DisplayName("approve writes a new version, stamps the result, records a vote, and publishes")
  void approve_writesVersionAndPublishes() {
    UUID actorId = seedActor("Gloria", "A thief.");
    UUID proposalId = createCosigned(actorId, Map.of("description", "A reformed thief."));

    ProposalDecisionResult result =
        decisionService.decide(
            new DecideProposalCommand(
                gmId, campaignId, proposalId, ProposalDecisionType.APPROVE, null));

    assertThat(result.resultingEntityVersionId()).isNotNull();
    // A second version exists for the actor and reflects the merged delta.
    assertThat(versionCount(actorId)).isEqualTo(2);
    assertThat(snapshotJson(result.resultingEntityVersionId()))
        .contains("A reformed thief.")
        .contains("Gloria");
    // The approved version is stamped with the latest committed session (D-107).
    assertThat(versionSessionId(result.resultingEntityVersionId())).isEqualTo(committedSessionId);

    Proposal persisted = proposalAdapter.findById(proposalId).orElseThrow();
    assertThat(persisted.status()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(persisted.resultingEntityVersionId()).isEqualTo(result.resultingEntityVersionId());
    assertThat(voteKind(proposalId, gmId)).isEqualTo("approve");

    assertThat(events.stream(SessionCommittedEvent.class))
        .anyMatch(
            e -> e.campaignId().equals(campaignId) && e.sessionId().equals(committedSessionId));
  }

  @Test
  @DisplayName("approve with a GM-edited delta applies the GM's values, not the author's (D-110)")
  void approveWithEdit_appliesGmDelta() {
    UUID actorId = seedActor("Gloria", "A thief.");
    UUID proposalId = createCosigned(actorId, Map.of("description", "A reformed thief."));

    ProposalDecisionResult result =
        decisionService.decide(
            new DecideProposalCommand(
                gmId,
                campaignId,
                proposalId,
                ProposalDecisionType.APPROVE,
                Map.of("description", "Edited by the GM.")));

    assertThat(snapshotJson(result.resultingEntityVersionId()))
        .contains("Edited by the GM.")
        .doesNotContain("A reformed thief.");
  }

  @Test
  @DisplayName("veto rejects without writing a new version and records a reject vote")
  void veto_rejectsWithoutWrite() {
    UUID actorId = seedActor("Bram", "A guard.");
    UUID proposalId = createCosigned(actorId, Map.of("description", "A retired guard."));

    ProposalDecisionResult result =
        decisionService.decide(
            new DecideProposalCommand(
                gmId, campaignId, proposalId, ProposalDecisionType.REJECT, null));

    assertThat(result.resultingEntityVersionId()).isNull();
    assertThat(versionCount(actorId)).isEqualTo(1);
    assertThat(proposalAdapter.findById(proposalId).orElseThrow().status())
        .isEqualTo(ProposalStatus.REJECTED);
    assertThat(voteKind(proposalId, gmId)).isEqualTo("reject");
    assertThat(events.stream(SessionCommittedEvent.class)).isEmpty();
  }

  @Test
  @DisplayName(
      "a second submission for the same target is rejected as a concurrent proposal (D-106)")
  void concurrentSubmission_isRejected() {
    UUID actorId = seedActor("Mira", "A scout.");
    createProposal(actorId, Map.of("description", "A veteran scout."));

    assertThatThrownBy(() -> createProposal(actorId, Map.of("description", "A different change.")))
        .isInstanceOf(ConcurrentProposalException.class);
  }

  @Test
  @DisplayName("the GM cannot co-sign a proposal — the GM decides (D-017)")
  void gmCannotCoSign() {
    UUID actorId = seedActor("Wren", "A herald.");
    UUID proposalId = createProposal(actorId, Map.of("description", "A retired herald."));

    assertThatThrownBy(
            () -> coSignService.coSign(new CoSignProposalCommand(gmId, campaignId, proposalId)))
        .isInstanceOf(GmCannotCoSignException.class);
  }

  @Test
  @DisplayName(
      "the partial unique index rejects a second open proposal for the same target (D-106)")
  void uniqueIndex_rejectsSecondOpenProposal() {
    UUID actorId = seedActor("Aldric", "A smith.");
    proposalAdapter.save(openProposal(actorId));

    assertThatThrownBy(() -> proposalAdapter.save(openProposal(actorId)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the expiry scheduler flips a past-due open proposal to EXPIRED, sparing fresh ones")
  void expiryScheduler_flipsStaleProposals() {
    UUID staleId = UUID.randomUUID();
    proposalAdapter.save(
        Proposal.create(
            staleId,
            campaignId,
            ProposalTargetType.ACTOR,
            UUID.randomUUID(),
            authorId,
            committedSessionId,
            "{\"name\":\"Stale\"}",
            NOW.minus(1, ChronoUnit.DAYS),
            NOW.minus(31, ChronoUnit.DAYS)));
    UUID freshId = UUID.randomUUID();
    proposalAdapter.save(
        Proposal.create(
            freshId,
            campaignId,
            ProposalTargetType.ACTOR,
            UUID.randomUUID(),
            authorId,
            committedSessionId,
            "{\"name\":\"Fresh\"}",
            NOW.plus(30, ChronoUnit.DAYS),
            NOW));

    expiryScheduler.expireStaleProposals();

    assertThat(proposalAdapter.findById(staleId).orElseThrow().status())
        .isEqualTo(ProposalStatus.EXPIRED);
    assertThat(proposalAdapter.findById(freshId).orElseThrow().status())
        .isEqualTo(ProposalStatus.OPEN);
  }

  // --- flow helpers -------------------------------------------------------

  private UUID createProposal(UUID actorId, Map<String, Object> delta) {
    ProposalView view =
        creationService.create(
            new CreateProposalCommand(
                authorId, campaignId, "actor", actorId, committedSessionId, delta));
    return view.proposalId();
  }

  private UUID createCosigned(UUID actorId, Map<String, Object> delta) {
    UUID proposalId = createProposal(actorId, delta);
    coSignService.coSign(new CoSignProposalCommand(cosignerId, campaignId, proposalId));
    return proposalId;
  }

  private Proposal openProposal(UUID actorId) {
    return Proposal.create(
        UUID.randomUUID(),
        campaignId,
        ProposalTargetType.ACTOR,
        actorId,
        authorId,
        committedSessionId,
        "{\"name\":\"X\"}",
        NOW.plus(30, ChronoUnit.DAYS),
        NOW);
  }

  // --- assertion helpers --------------------------------------------------

  private int versionCount(UUID actorId) {
    Integer n =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM actor_versions WHERE actor_id = ?", Integer.class, actorId);
    return n == null ? 0 : n;
  }

  private String snapshotJson(UUID versionId) {
    return jdbcTemplate.queryForObject(
        "SELECT full_snapshot::text FROM actor_versions WHERE id = ?", String.class, versionId);
  }

  private UUID versionSessionId(UUID versionId) {
    return jdbcTemplate.queryForObject(
        "SELECT session_id FROM actor_versions WHERE id = ?", UUID.class, versionId);
  }

  private String voteKind(UUID proposalId, UUID voterId) {
    return jdbcTemplate.queryForObject(
        "SELECT vote FROM proposal_votes WHERE proposal_id = ? AND voter_id = ?",
        String.class,
        proposalId,
        voterId);
  }

  // --- seeding ------------------------------------------------------------

  private UUID seedActor(String name, String description) {
    CommittedEntityVersion v =
        worldStateAdapter.writeEntity(
            new EntityWriteCommand(
                "actor",
                null,
                campaignId,
                gmId,
                name,
                null,
                Map.of("name", name, "description", description),
                committedSessionId));
    return v.entityId();
  }

  private UUID savedUser() {
    UUID id = UUID.randomUUID();
    userAdapter.save(User.create(id, id + "@example.com", "$2a$10$hash", false, false, NOW));
    return id;
  }

  private UUID savedCampaign(UUID createdBy) {
    UUID id = UUID.randomUUID();
    campaignAdapter.save(Campaign.create(id, "Campaign-" + id, createdBy, NOW));
    return id;
  }

  private void savedMember(UUID campaign, UUID userId, CampaignRole role) {
    membershipAdapter.save(CampaignMember.create(UUID.randomUUID(), campaign, userId, role, NOW));
  }

  private UUID savedCommittedSession(UUID campaign, UUID owner) {
    UUID id = UUID.randomUUID();
    Session session = Session.create(id, campaign, owner, NOW);
    session.startProcessing();
    session.toDraft("{}");
    session.commit(1);
    sessionAdapter.save(session);
    return id;
  }
}
