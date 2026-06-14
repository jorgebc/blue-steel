package com.bluesteel.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.proposal.DecideProposalCommand;
import com.bluesteel.application.model.proposal.ProposalDecisionResult;
import com.bluesteel.application.model.proposal.ProposalDecisionType;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.application.service.proposal.ProposalDecisionService;
import com.bluesteel.application.service.proposal.ProposalDeltaMapper;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.InvalidProposalStateTransitionException;
import com.bluesteel.domain.exception.ProposalNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.proposal.ProposalVote;
import com.bluesteel.domain.proposal.VoteKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalDecisionService")
class ProposalDecisionServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private ProposalRepository proposalRepository;
  @Mock private SessionRepository sessionRepository;
  @Mock private WorldStatePort worldStatePort;
  @Mock private ProposalDeltaMapper deltaMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ProposalDecisionService sut;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID GM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PROPOSAL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID TARGET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID AUTHOR_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID PROVENANCE_SESSION =
      UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID LATEST_SESSION =
      UUID.fromString("77777777-7777-7777-7777-777777777777");
  private static final UUID NEW_VERSION_ID =
      UUID.fromString("88888888-8888-8888-8888-888888888888");

  @BeforeEach
  void setUp() {
    sut =
        new ProposalDecisionService(
            membershipPort,
            proposalRepository,
            sessionRepository,
            worldStatePort,
            deltaMapper,
            new ObjectMapper(),
            eventPublisher);
  }

  @Test
  @DisplayName(
      "should write a new version, record an approve vote, and publish the commit event on approve")
  void decide_approve_writesVersionRecordsVoteAndPublishes() {
    Proposal proposal = cosignedProposal();
    EntityWriteCommand writeCommand = sampleWriteCommand();
    CommittedEntityVersion version =
        new CommittedEntityVersion("actor", TARGET_ID, NEW_VERSION_ID, 2, "content", "hash");
    when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID)).thenReturn(Optional.of(CampaignRole.GM));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
    when(sessionRepository.findLatestCommittedSessionId(CAMPAIGN_ID))
        .thenReturn(Optional.of(LATEST_SESSION));
    when(deltaMapper.toWriteCommand(proposal, Map.of("name", "Author Name"), LATEST_SESSION))
        .thenReturn(writeCommand);
    when(worldStatePort.writeEntity(writeCommand)).thenReturn(version);

    ProposalDecisionResult result = sut.decide(approveCommand(null));

    assertThat(result.resultingEntityVersionId()).isEqualTo(NEW_VERSION_ID);
    assertThat(proposal.status()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(proposal.resultingEntityVersionId()).isEqualTo(NEW_VERSION_ID);

    verify(proposalRepository).save(proposal);

    ArgumentCaptor<ProposalVote> voteCaptor = forClass(ProposalVote.class);
    verify(proposalRepository).saveVote(voteCaptor.capture());
    assertThat(voteCaptor.getValue().kind()).isEqualTo(VoteKind.APPROVE);
    assertThat(voteCaptor.getValue().voterId()).isEqualTo(GM_ID);
    assertThat(voteCaptor.getValue().proposalId()).isEqualTo(PROPOSAL_ID);

    ArgumentCaptor<SessionCommittedEvent> eventCaptor = forClass(SessionCommittedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().sessionId()).isEqualTo(LATEST_SESSION);
    assertThat(eventCaptor.getValue().campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(eventCaptor.getValue().committedVersions()).containsExactly(version);
  }

  @Test
  @DisplayName(
      "should apply the GM-edited delta in place of the author's delta when approving (D-110)")
  void decide_approveWithEdit_usesEditedDelta() {
    Proposal proposal = cosignedProposal();
    Map<String, Object> editedDelta = Map.of("name", "GM Edited Name");
    CommittedEntityVersion version =
        new CommittedEntityVersion("actor", TARGET_ID, NEW_VERSION_ID, 2, "content", "hash");
    when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID)).thenReturn(Optional.of(CampaignRole.GM));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
    when(sessionRepository.findLatestCommittedSessionId(CAMPAIGN_ID))
        .thenReturn(Optional.of(LATEST_SESSION));
    when(deltaMapper.toWriteCommand(proposal, editedDelta, LATEST_SESSION))
        .thenReturn(sampleWriteCommand());
    when(worldStatePort.writeEntity(any(EntityWriteCommand.class))).thenReturn(version);

    sut.decide(approveCommand(editedDelta));

    verify(deltaMapper).toWriteCommand(proposal, editedDelta, LATEST_SESSION);
  }

  @Test
  @DisplayName("should reject unilaterally with a reject vote and no world-state write on veto")
  void decide_veto_rejectsAndRecordsRejectVote() {
    Proposal proposal = cosignedProposal();
    when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID)).thenReturn(Optional.of(CampaignRole.GM));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

    ProposalDecisionResult result =
        sut.decide(
            new DecideProposalCommand(
                GM_ID, CAMPAIGN_ID, PROPOSAL_ID, ProposalDecisionType.REJECT, null));

    assertThat(result.resultingEntityVersionId()).isNull();
    assertThat(proposal.status()).isEqualTo(ProposalStatus.REJECTED);
    verify(proposalRepository).save(proposal);

    ArgumentCaptor<ProposalVote> voteCaptor = forClass(ProposalVote.class);
    verify(proposalRepository).saveVote(voteCaptor.capture());
    assertThat(voteCaptor.getValue().kind()).isEqualTo(VoteKind.REJECT);

    verifyNoInteractions(worldStatePort, eventPublisher);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when the caller is not the GM")
  void decide_nonGm_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));

    assertThatThrownBy(() -> sut.decide(approveCommand(null)))
        .isInstanceOf(UnauthorizedException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
    verifyNoInteractions(worldStatePort, eventPublisher);
  }

  @Test
  @DisplayName("should throw ProposalNotFoundException when the proposal is not in the campaign")
  void decide_missingProposal_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID)).thenReturn(Optional.of(CampaignRole.GM));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.decide(approveCommand(null)))
        .isInstanceOf(ProposalNotFoundException.class);
    verifyNoInteractions(worldStatePort, eventPublisher);
  }

  @Test
  @DisplayName("should throw and skip the world-state write when the proposal is not cosigned")
  void decide_notCosigned_throwsAndSkipsWrite() {
    Proposal open = proposal(ProposalStatus.OPEN);
    when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID)).thenReturn(Optional.of(CampaignRole.GM));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(open));

    assertThatThrownBy(() -> sut.decide(approveCommand(null)))
        .isInstanceOf(InvalidProposalStateTransitionException.class);
    verifyNoInteractions(worldStatePort, eventPublisher);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  private DecideProposalCommand approveCommand(Map<String, Object> editedDelta) {
    return new DecideProposalCommand(
        GM_ID, CAMPAIGN_ID, PROPOSAL_ID, ProposalDecisionType.APPROVE, editedDelta);
  }

  private static Proposal cosignedProposal() {
    return proposal(ProposalStatus.COSIGNED);
  }

  private static Proposal proposal(ProposalStatus status) {
    Instant now = Instant.now();
    return Proposal.reconstitute(
        PROPOSAL_ID,
        CAMPAIGN_ID,
        ProposalTargetType.ACTOR,
        TARGET_ID,
        AUTHOR_ID,
        PROVENANCE_SESSION,
        "{\"name\":\"Author Name\"}",
        status,
        now.plusSeconds(3600),
        null,
        now);
  }

  private static EntityWriteCommand sampleWriteCommand() {
    return new EntityWriteCommand(
        "actor",
        TARGET_ID,
        CAMPAIGN_ID,
        AUTHOR_ID,
        "Author Name",
        Map.of("name", "Author Name"),
        Map.of("name", "Author Name"),
        LATEST_SESSION);
  }
}
