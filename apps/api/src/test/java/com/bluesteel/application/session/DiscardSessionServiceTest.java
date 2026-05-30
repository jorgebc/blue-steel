package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.DiscardSessionService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscardSessionService")
class DiscardSessionServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private SessionRepository sessionRepository;

  private DiscardSessionService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID SESSION_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new DiscardSessionService(membershipPort, sessionRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void discard_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(UnauthorizedException.class);

    verify(sessionRepository, never()).save(any(Session.class));
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a GM")
  void discard_editorRole_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));

    assertThatThrownBy(() -> sut.discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(UnauthorizedException.class);

    verify(sessionRepository, never()).save(any(Session.class));
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session does not exist")
  void discard_sessionNotFound_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  @DisplayName("should throw InvalidSessionStateTransitionException when session is not in DRAFT")
  void discard_sessionNotDraft_throwsInvalidTransition() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    Session session = Session.create(SESSION_ID, CAMPAIGN_ID, CALLER_ID, Instant.now());
    session.markFailed("PIPELINE_NOT_IMPLEMENTED");
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> sut.discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .isInstanceOf(InvalidSessionStateTransitionException.class);

    verify(sessionRepository, never()).save(any(Session.class));
  }

  @Test
  @DisplayName("should discard draft session and save when GM requests discard")
  void discard_gmWithDraftSession_savesDiscardedSession() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    Session session = Session.create(SESSION_ID, CAMPAIGN_ID, CALLER_ID, Instant.now());
    session.startProcessing();
    session.toDraft("{\"cards\":[]}");
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

    sut.discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID);

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(SessionStatus.DISCARDED);
    assertThat(captor.getValue().diffPayload()).isNull();
  }
}
