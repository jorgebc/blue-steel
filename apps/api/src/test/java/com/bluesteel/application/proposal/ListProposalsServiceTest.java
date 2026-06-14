package com.bluesteel.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.proposal.ProposalFilter;
import com.bluesteel.application.model.proposal.ProposalListView;
import com.bluesteel.application.model.proposal.ProposalPage;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.proposal.ProposalRepository;
import com.bluesteel.application.service.proposal.ListProposalsService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListProposalsService")
class ListProposalsServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private ProposalRepository proposalRepository;

  private ListProposalsService sut;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CALLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @BeforeEach
  void setUp() {
    sut = new ListProposalsService(membershipPort, proposalRepository);
  }

  @Test
  @DisplayName("should return a page of proposals with totals for a member, no status filter")
  void list_member_returnsPage() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findByCampaign(
            CAMPAIGN_ID, new ProposalFilter(null), new ProposalPage(0, 20)))
        .thenReturn(List.of(openProposal()));
    when(proposalRepository.countByCampaign(CAMPAIGN_ID, new ProposalFilter(null))).thenReturn(1L);

    ProposalListView view = sut.list(CAMPAIGN_ID, CALLER_ID, null, 0, 20);

    assertThat(view.proposals()).hasSize(1);
    assertThat(view.totalCount()).isEqualTo(1L);
    assertThat(view.page()).isZero();
    assertThat(view.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("should pass the status filter through when one is provided")
  void list_withStatusFilter_passesFilter() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(proposalRepository.findByCampaign(
            CAMPAIGN_ID, new ProposalFilter(ProposalStatus.OPEN), new ProposalPage(0, 20)))
        .thenReturn(List.of());
    when(proposalRepository.countByCampaign(CAMPAIGN_ID, new ProposalFilter(ProposalStatus.OPEN)))
        .thenReturn(0L);

    ProposalListView view = sut.list(CAMPAIGN_ID, CALLER_ID, ProposalStatus.OPEN, 0, 20);

    assertThat(view.proposals()).isEmpty();
    assertThat(view.totalCount()).isZero();
  }

  @Test
  @DisplayName("should clamp an oversized page size to the maximum of 100")
  void list_oversizedPageSize_clampsTo100() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(proposalRepository.findByCampaign(
            CAMPAIGN_ID, new ProposalFilter(null), new ProposalPage(0, 100)))
        .thenReturn(List.of());
    when(proposalRepository.countByCampaign(CAMPAIGN_ID, new ProposalFilter(null))).thenReturn(0L);

    ProposalListView view = sut.list(CAMPAIGN_ID, CALLER_ID, null, 0, 500);

    assertThat(view.size()).isEqualTo(100);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a member")
  void list_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.list(CAMPAIGN_ID, CALLER_ID, null, 0, 20))
        .isInstanceOf(UnauthorizedException.class);
  }

  private static Proposal openProposal() {
    Instant now = Instant.now();
    return Proposal.create(
        UUID.randomUUID(),
        CAMPAIGN_ID,
        ProposalTargetType.ACTOR,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "{\"name\":\"X\"}",
        now.plus(30, ChronoUnit.DAYS),
        now);
  }
}
