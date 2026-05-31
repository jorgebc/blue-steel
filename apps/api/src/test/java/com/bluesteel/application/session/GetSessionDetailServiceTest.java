package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.session.SessionDetailView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.GetSessionDetailService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetSessionDetailService")
class GetSessionDetailServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private SessionRepository sessionRepository;
  @Mock private NarrativeBlockRepository narrativeBlockRepository;

  private GetSessionDetailService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new GetSessionDetailService(membershipPort, sessionRepository, narrativeBlockRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void getDetail_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getDetail(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session does not exist")
  void getDetail_sessionNotFound_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getDetail(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session belongs to another campaign")
  void getDetail_sessionInDifferentCampaign_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Session otherCampaignSession =
        Session.create(SESSION_ID, UUID.randomUUID(), CALLER_ID, Instant.now());
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(otherCampaignSession));

    assertThatThrownBy(() -> sut.getDetail(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  @DisplayName("should return detail with the narrative block reference for a member")
  void getDetail_memberWithBlock_returnsDetailWithBlockId() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Session session = Session.create(SESSION_ID, CAMPAIGN_ID, CALLER_ID, Instant.now());
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    UUID blockId = UUID.randomUUID();
    NarrativeBlock block =
        NarrativeBlock.create(blockId, SESSION_ID, "Raw summary text.", 4, Instant.now());
    when(narrativeBlockRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(block));

    SessionDetailView view = sut.getDetail(SESSION_ID, CALLER_ID, CAMPAIGN_ID);

    assertThat(view.sessionId()).isEqualTo(SESSION_ID);
    assertThat(view.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(view.narrativeBlockId()).isEqualTo(blockId);
  }

  @Test
  @DisplayName("should return a null narrative block reference when no block exists")
  void getDetail_noBlock_returnsNullBlockId() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    Session session = Session.create(SESSION_ID, CAMPAIGN_ID, CALLER_ID, Instant.now());
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    when(narrativeBlockRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

    SessionDetailView view = sut.getDetail(SESSION_ID, CALLER_ID, CAMPAIGN_ID);

    assertThat(view.narrativeBlockId()).isNull();
  }
}
