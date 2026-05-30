package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.ExtractionPipelineService;
import com.bluesteel.application.service.session.SessionIngestionEventListener;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
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
@DisplayName("SessionIngestionEventListener")
class SessionIngestionEventListenerTest {

  @Mock private SessionRepository sessionRepository;
  @Mock private NarrativeBlockRepository narrativeBlockRepository;
  @Mock private ExtractionPipelineService extractionPipelineService;

  private SessionIngestionEventListener sut;

  @BeforeEach
  void setUp() {
    sut =
        new SessionIngestionEventListener(
            sessionRepository, narrativeBlockRepository, extractionPipelineService);
  }

  @Test
  @DisplayName(
      "should load the session and its narrative block then delegate to ExtractionPipelineService")
  void onSessionSubmitted_delegatesToExtractionPipelineService() {
    UUID sessionId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String rawText = "The heroes stormed the fortress at dawn.";

    Session session = Session.create(sessionId, campaignId, ownerId, Instant.now());
    NarrativeBlock block =
        NarrativeBlock.create(UUID.randomUUID(), sessionId, rawText, 10, Instant.now());

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(narrativeBlockRepository.findBySessionId(sessionId)).thenReturn(Optional.of(block));

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(extractionPipelineService).run(sessionCaptor.capture(), textCaptor.capture());

    assertThat(sessionCaptor.getValue()).isSameAs(session);
    assertThat(textCaptor.getValue()).isEqualTo(rawText);
  }
}
