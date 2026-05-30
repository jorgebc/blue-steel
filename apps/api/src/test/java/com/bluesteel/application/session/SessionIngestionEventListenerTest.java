package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.SessionIngestionEventListener;
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
@DisplayName("SessionIngestionEventListener")
class SessionIngestionEventListenerTest {

  @Mock private SessionRepository sessionRepository;

  private SessionIngestionEventListener sut;

  @BeforeEach
  void setUp() {
    sut = new SessionIngestionEventListener(sessionRepository);
  }

  @Test
  @DisplayName(
      "should transition session through PROCESSING then to FAILED with PIPELINE_NOT_IMPLEMENTED")
  void onSessionSubmitted_transitionsToFailed() {
    UUID sessionId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();
    Session session = Session.create(sessionId, campaignId, UUID.randomUUID(), Instant.now());
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    // Session is mutable — verify save() was called twice and check the final state
    verify(sessionRepository, times(2)).save(session);
    assertThat(session.status()).isEqualTo(SessionStatus.FAILED);
    assertThat(session.failureReason()).isEqualTo("PIPELINE_NOT_IMPLEMENTED");
  }
}
