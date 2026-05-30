package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.model.session.SubmitSessionCommand;
import com.bluesteel.application.model.session.SubmitSessionResult;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.SubmitSessionService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.ActiveSessionExistsException;
import com.bluesteel.domain.exception.SummaryTooLargeException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.NarrativeBlock;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmitSessionService")
class SubmitSessionServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private SessionRepository sessionRepository;
  @Mock private NarrativeBlockRepository narrativeBlockRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private SubmitSessionService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final int MAX_TOKENS = 100;
  private static final String VALID_SUMMARY = "A".repeat(100); // ~25 estimated tokens

  @BeforeEach
  void setUp() {
    sut =
        new SubmitSessionService(
            membershipPort,
            sessionRepository,
            narrativeBlockRepository,
            eventPublisher,
            MAX_TOKENS);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void submit_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> sut.submit(new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, VALID_SUMMARY)))
        .isInstanceOf(UnauthorizedException.class);

    verify(sessionRepository, never()).save(any(Session.class));
    verify(narrativeBlockRepository, never()).save(any(NarrativeBlock.class));
    verify(eventPublisher, never()).publishEvent(any(SessionSubmittedEvent.class));
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller has PLAYER role")
  void submit_playerRole_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    assertThatThrownBy(
            () -> sut.submit(new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, VALID_SUMMARY)))
        .isInstanceOf(UnauthorizedException.class);

    verify(sessionRepository, never()).save(any(Session.class));
    verify(narrativeBlockRepository, never()).save(any(NarrativeBlock.class));
  }

  @Test
  @DisplayName("should throw SummaryTooLargeException when estimated token count exceeds max")
  void submit_oversizedSummary_throwsSummaryTooLarge() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    // MAX_TOKENS=100; need >100 estimated tokens → >400 chars
    String oversized = "A".repeat(401);

    assertThatThrownBy(
            () -> sut.submit(new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, oversized)))
        .isInstanceOf(SummaryTooLargeException.class);

    verify(sessionRepository, never()).save(any(Session.class));
    verify(narrativeBlockRepository, never()).save(any(NarrativeBlock.class));
  }

  @Test
  @DisplayName("should throw ActiveSessionExistsException when an active session already exists")
  void submit_activeSessionPresent_throwsActiveSessionExists() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    UUID existingId = UUID.randomUUID();
    Session existing = Session.create(existingId, CAMPAIGN_ID, UUID.randomUUID(), Instant.now());
    existing.startProcessing();
    when(sessionRepository.findActiveByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () -> sut.submit(new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, VALID_SUMMARY)))
        .isInstanceOf(ActiveSessionExistsException.class)
        .satisfies(
            e ->
                assertThat(((ActiveSessionExistsException) e).existingSessionId())
                    .isEqualTo(existingId));

    verify(sessionRepository, never()).save(any(Session.class));
    verify(narrativeBlockRepository, never()).save(any(NarrativeBlock.class));
  }

  @Test
  @DisplayName("should save narrative block and session and publish event on successful submission")
  void submit_gmRole_savesAndPublishesEvent() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findActiveByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.empty());

    SubmitSessionResult result =
        sut.submit(new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, VALID_SUMMARY));

    assertThat(result.status()).isEqualTo(SessionStatus.PENDING);
    assertThat(result.sessionId()).isNotNull();

    ArgumentCaptor<NarrativeBlock> blockCaptor = ArgumentCaptor.forClass(NarrativeBlock.class);
    verify(narrativeBlockRepository).save(blockCaptor.capture());
    assertThat(blockCaptor.getValue().rawSummaryText()).isEqualTo(VALID_SUMMARY);
    assertThat(blockCaptor.getValue().sessionId()).isEqualTo(result.sessionId());

    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(sessionCaptor.capture());
    assertThat(sessionCaptor.getValue().id()).isEqualTo(result.sessionId());
    assertThat(sessionCaptor.getValue().campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(sessionCaptor.getValue().status()).isEqualTo(SessionStatus.PENDING);

    ArgumentCaptor<SessionSubmittedEvent> eventCaptor =
        ArgumentCaptor.forClass(SessionSubmittedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().sessionId()).isEqualTo(result.sessionId());
    assertThat(eventCaptor.getValue().campaignId()).isEqualTo(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("should accept submission from EDITOR role")
  void submit_editorRole_savesAndPublishesEvent() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(sessionRepository.findActiveByCampaignId(CAMPAIGN_ID)).thenReturn(Optional.empty());

    SubmitSessionResult result =
        sut.submit(new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, VALID_SUMMARY));

    assertThat(result.status()).isEqualTo(SessionStatus.PENDING);
    verify(sessionRepository).save(any(Session.class));
    verify(narrativeBlockRepository).save(any(NarrativeBlock.class));
    verify(eventPublisher).publishEvent(any(SessionSubmittedEvent.class));
  }
}
