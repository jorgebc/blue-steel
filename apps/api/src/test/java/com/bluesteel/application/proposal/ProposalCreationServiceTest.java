package com.bluesteel.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.proposal.CreateProposalCommand;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.application.service.proposal.ProposalCreationService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.ConcurrentProposalException;
import com.bluesteel.domain.exception.EmptyDeltaException;
import com.bluesteel.domain.exception.ProposalTargetNotFoundException;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.exception.UnsupportedTargetTypeException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.session.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalCreationService")
class ProposalCreationServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private ProposalRepository proposalRepository;
  @Mock private WorldStatePort worldStatePort;
  @Mock private SessionRepository sessionRepository;

  private ProposalCreationService sut;

  private static final int TTL_DAYS = 30;
  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CALLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TARGET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID SESSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final Map<String, Object> DELTA = Map.of("name", "New Name");

  @BeforeEach
  void setUp() {
    sut =
        new ProposalCreationService(
            membershipPort,
            proposalRepository,
            worldStatePort,
            sessionRepository,
            new ObjectMapper(),
            TTL_DAYS);
  }

  @Test
  @DisplayName("should create an OPEN proposal stamped with the TTL expiry when all checks pass")
  void create_validActor_savesAndReturnsView() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(worldStatePort.existsInCampaign("actor", TARGET_ID, CAMPAIGN_ID)).thenReturn(true);
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(CAMPAIGN_ID)));
    when(proposalRepository.existsOpenForTarget(CAMPAIGN_ID, ProposalTargetType.ACTOR, TARGET_ID))
        .thenReturn(false);
    ArgumentCaptor<Proposal> captor = forClass(Proposal.class);

    ProposalView view = sut.create(command("actor", TARGET_ID, DELTA));

    verify(proposalRepository).save(captor.capture());
    Proposal saved = captor.getValue();
    assertThat(saved.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(saved.targetType()).isEqualTo(ProposalTargetType.ACTOR);
    assertThat(saved.ownerId()).isEqualTo(CALLER_ID);
    assertThat(saved.sessionId()).isEqualTo(SESSION_ID);
    assertThat(saved.status()).isEqualTo(ProposalStatus.OPEN);
    assertThat(saved.proposedDelta()).contains("\"name\":\"New Name\"");
    assertThat(ChronoUnit.DAYS.between(saved.createdAt(), saved.expiresAt())).isEqualTo(TTL_DAYS);
    assertThat(view.status()).isEqualTo(ProposalStatus.OPEN);
    assertThat(view.targetId()).isEqualTo(TARGET_ID);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void create_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.create(command("actor", TARGET_ID, DELTA)))
        .isInstanceOf(UnauthorizedException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw UnsupportedTargetTypeException for an event target (D-108)")
  void create_eventTarget_throwsUnsupportedTargetType() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    assertThatThrownBy(() -> sut.create(command("event", TARGET_ID, DELTA)))
        .isInstanceOf(UnsupportedTargetTypeException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw EmptyDeltaException when the proposed delta is empty (D-104)")
  void create_emptyDelta_throwsEmptyDelta() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    assertThatThrownBy(() -> sut.create(command("actor", TARGET_ID, Map.of())))
        .isInstanceOf(EmptyDeltaException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName(
      "should throw ProposalTargetNotFoundException when the target is not in the campaign")
  void create_unknownTarget_throwsTargetNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(worldStatePort.existsInCampaign("space", TARGET_ID, CAMPAIGN_ID)).thenReturn(false);

    assertThatThrownBy(() -> sut.create(command("space", TARGET_ID, DELTA)))
        .isInstanceOf(ProposalTargetNotFoundException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when the provenance session does not exist")
  void create_unknownSession_throwsSessionNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(worldStatePort.existsInCampaign("actor", TARGET_ID, CAMPAIGN_ID)).thenReturn(true);
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.create(command("actor", TARGET_ID, DELTA)))
        .isInstanceOf(SessionNotFoundException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when the session belongs to another campaign")
  void create_foreignSession_throwsSessionNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(worldStatePort.existsInCampaign("actor", TARGET_ID, CAMPAIGN_ID)).thenReturn(true);
    when(sessionRepository.findById(SESSION_ID))
        .thenReturn(Optional.of(session(UUID.randomUUID())));

    assertThatThrownBy(() -> sut.create(command("actor", TARGET_ID, DELTA)))
        .isInstanceOf(SessionNotFoundException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw ConcurrentProposalException when an open proposal exists (D-106)")
  void create_concurrentProposal_throwsConflict() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(worldStatePort.existsInCampaign("actor", TARGET_ID, CAMPAIGN_ID)).thenReturn(true);
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(CAMPAIGN_ID)));
    when(proposalRepository.existsOpenForTarget(CAMPAIGN_ID, ProposalTargetType.ACTOR, TARGET_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> sut.create(command("actor", TARGET_ID, DELTA)))
        .isInstanceOf(ConcurrentProposalException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  private CreateProposalCommand command(
      String targetType, UUID targetId, Map<String, Object> delta) {
    return new CreateProposalCommand(
        CALLER_ID, CAMPAIGN_ID, targetType, targetId, SESSION_ID, delta);
  }

  private static Session session(UUID campaignId) {
    return Session.create(SESSION_ID, campaignId, CALLER_ID, Instant.now());
  }
}
