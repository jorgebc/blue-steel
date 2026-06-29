package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.session.SessionStatusView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.GetSessionStatusService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
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
@DisplayName("GetSessionStatusService")
class GetSessionStatusServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private SessionRepository sessionRepository;

  private GetSessionStatusService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new GetSessionStatusService(membershipPort, sessionRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void getStatus_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getStatus(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session does not exist")
  void getStatus_sessionNotFound_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getStatus(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session belongs to another campaign")
  void getStatus_sessionInOtherCampaign_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Session otherCampaignSession =
        Session.create(SESSION_ID, UUID.randomUUID(), CALLER_ID, Instant.now());
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(otherCampaignSession));

    assertThatThrownBy(() -> sut.getStatus(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  @DisplayName("should return session status view for any campaign member")
  void getStatus_memberWithSession_returnsStatusView() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Session session = Session.create(SESSION_ID, CAMPAIGN_ID, CALLER_ID, Instant.now());
    session.markFailed("PIPELINE_NOT_IMPLEMENTED");
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

    SessionStatusView view = sut.getStatus(SESSION_ID, CALLER_ID, CAMPAIGN_ID);

    assertThat(view.sessionId()).isEqualTo(SESSION_ID);
    assertThat(view.status()).isEqualTo(SessionStatus.FAILED);
    assertThat(view.failureReason()).isEqualTo("PIPELINE_NOT_IMPLEMENTED");
  }
}
