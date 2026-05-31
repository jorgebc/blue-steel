package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.session.SessionListView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.ListSessionsService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import java.time.Instant;
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
@DisplayName("ListSessionsService")
class ListSessionsServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private SessionRepository sessionRepository;

  private ListSessionsService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new ListSessionsService(membershipPort, sessionRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void list_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.list(CAMPAIGN_ID, CALLER_ID, 0, 20))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should return mapped session summaries with total count for a member")
  void list_member_returnsMappedSummariesAndTotal() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Session committed = Session.create(UUID.randomUUID(), CAMPAIGN_ID, CALLER_ID, Instant.now());
    committed.startProcessing();
    committed.toDraft("{}");
    committed.commit(1);
    when(sessionRepository.findByCampaignId(CAMPAIGN_ID, 0, 20)).thenReturn(List.of(committed));
    when(sessionRepository.countByCampaignId(CAMPAIGN_ID)).thenReturn(1L);

    SessionListView view = sut.list(CAMPAIGN_ID, CALLER_ID, 0, 20);

    assertThat(view.totalCount()).isEqualTo(1L);
    assertThat(view.page()).isZero();
    assertThat(view.size()).isEqualTo(20);
    assertThat(view.sessions()).hasSize(1);
    assertThat(view.sessions().get(0).sessionId()).isEqualTo(committed.id());
    assertThat(view.sessions().get(0).status()).isEqualTo(SessionStatus.COMMITTED);
    assertThat(view.sessions().get(0).sequenceNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("should clamp an oversized page size to the maximum of 100")
  void list_oversizedSize_clampedTo100() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findByCampaignId(CAMPAIGN_ID, 0, 100)).thenReturn(List.of());
    when(sessionRepository.countByCampaignId(CAMPAIGN_ID)).thenReturn(0L);

    SessionListView view = sut.list(CAMPAIGN_ID, CALLER_ID, 0, 500);

    assertThat(view.size()).isEqualTo(100);
    verify(sessionRepository).findByCampaignId(CAMPAIGN_ID, 0, 100);
  }

  @Test
  @DisplayName("should normalize a negative page and non-positive size to safe defaults")
  void list_negativePageAndZeroSize_normalized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(sessionRepository.findByCampaignId(CAMPAIGN_ID, 0, 20)).thenReturn(List.of());
    when(sessionRepository.countByCampaignId(CAMPAIGN_ID)).thenReturn(0L);

    SessionListView view = sut.list(CAMPAIGN_ID, CALLER_ID, -3, 0);

    assertThat(view.page()).isZero();
    assertThat(view.size()).isEqualTo(20);
    verify(sessionRepository).findByCampaignId(CAMPAIGN_ID, 0, 20);
  }
}
