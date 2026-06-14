package com.bluesteel.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.proposal.CoSignProposalCommand;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.application.service.proposal.ProposalCoSignService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AuthorCannotCoSignException;
import com.bluesteel.domain.exception.DuplicateVoteException;
import com.bluesteel.domain.exception.InvalidProposalStateTransitionException;
import com.bluesteel.domain.exception.ProposalNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import com.bluesteel.domain.proposal.ProposalVote;
import com.bluesteel.domain.proposal.VoteKind;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalCoSignService")
class ProposalCoSignServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private ProposalRepository proposalRepository;

  private ProposalCoSignService sut;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PROPOSAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID AUTHOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID COSIGNER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @BeforeEach
  void setUp() {
    sut = new ProposalCoSignService(membershipPort, proposalRepository);
  }

  @Test
  @DisplayName("should record a cosign vote and move OPEN to COSIGNED for a non-author member")
  void coSign_nonAuthorMember_recordsVoteAndTransitions() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, COSIGNER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(openProposal(AUTHOR_ID)));
    ArgumentCaptor<ProposalVote> voteCaptor = forClass(ProposalVote.class);
    ArgumentCaptor<Proposal> proposalCaptor = forClass(Proposal.class);

    ProposalView view =
        sut.coSign(new CoSignProposalCommand(COSIGNER_ID, CAMPAIGN_ID, PROPOSAL_ID));

    verify(proposalRepository).saveVote(voteCaptor.capture());
    assertThat(voteCaptor.getValue().kind()).isEqualTo(VoteKind.COSIGN);
    assertThat(voteCaptor.getValue().voterId()).isEqualTo(COSIGNER_ID);
    verify(proposalRepository).save(proposalCaptor.capture());
    assertThat(proposalCaptor.getValue().status()).isEqualTo(ProposalStatus.COSIGNED);
    assertThat(view.status()).isEqualTo(ProposalStatus.COSIGNED);
  }

  @Test
  @DisplayName(
      "should throw AuthorCannotCoSignException when the author co-signs their own proposal")
  void coSign_byAuthor_throwsAuthorCannotCoSign() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(openProposal(AUTHOR_ID)));

    assertThatThrownBy(
            () -> sut.coSign(new CoSignProposalCommand(AUTHOR_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .isInstanceOf(AuthorCannotCoSignException.class);
    verify(proposalRepository, never()).saveVote(any(ProposalVote.class));
  }

  @Test
  @DisplayName("should throw DuplicateVoteException when the voter has already voted")
  void coSign_duplicateVote_throwsDuplicate() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, COSIGNER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(openProposal(AUTHOR_ID)));
    doThrow(new DataIntegrityViolationException("uidx_proposal_votes_proposal_voter"))
        .when(proposalRepository)
        .saveVote(any(ProposalVote.class));

    assertThatThrownBy(
            () -> sut.coSign(new CoSignProposalCommand(COSIGNER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .isInstanceOf(DuplicateVoteException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw ProposalNotFoundException when the proposal does not exist")
  void coSign_missingProposal_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, COSIGNER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> sut.coSign(new CoSignProposalCommand(COSIGNER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .isInstanceOf(ProposalNotFoundException.class);
  }

  @Test
  @DisplayName("should throw ProposalNotFoundException when the proposal is in another campaign")
  void coSign_foreignCampaign_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, COSIGNER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findById(PROPOSAL_ID))
        .thenReturn(Optional.of(openProposalInCampaign(AUTHOR_ID, UUID.randomUUID())));

    assertThatThrownBy(
            () -> sut.coSign(new CoSignProposalCommand(COSIGNER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .isInstanceOf(ProposalNotFoundException.class);
  }

  @Test
  @DisplayName("should throw when co-signing a proposal that is no longer OPEN")
  void coSign_alreadyCosigned_throwsInvalidTransition() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, COSIGNER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Proposal cosigned = openProposal(AUTHOR_ID);
    cosigned.coSign();
    when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(cosigned));

    assertThatThrownBy(
            () -> sut.coSign(new CoSignProposalCommand(COSIGNER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .isInstanceOf(InvalidProposalStateTransitionException.class);
    verify(proposalRepository, never()).save(any(Proposal.class));
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a member")
  void coSign_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, COSIGNER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> sut.coSign(new CoSignProposalCommand(COSIGNER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .isInstanceOf(UnauthorizedException.class);
    verify(proposalRepository, never()).saveVote(any(ProposalVote.class));
  }

  private static Proposal openProposal(UUID ownerId) {
    return openProposalInCampaign(ownerId, CAMPAIGN_ID);
  }

  private static Proposal openProposalInCampaign(UUID ownerId, UUID campaignId) {
    Instant now = Instant.now();
    return Proposal.create(
        PROPOSAL_ID,
        campaignId,
        ProposalTargetType.ACTOR,
        UUID.randomUUID(),
        ownerId,
        UUID.randomUUID(),
        "{\"name\":\"X\"}",
        now.plus(30, ChronoUnit.DAYS),
        now);
  }
}
