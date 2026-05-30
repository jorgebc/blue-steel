package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Stub ingestion listener — drives the session through PROCESSING and immediately to FAILED.
 * Replaced incrementally in F2.4–F2.7 as real pipeline stages are wired.
 */
@Component
public class SessionIngestionEventListener {

  private static final Logger log = LoggerFactory.getLogger(SessionIngestionEventListener.class);

  private final SessionRepository sessionRepository;

  public SessionIngestionEventListener(SessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @EventListener
  @Async
  public void onSessionSubmitted(SessionSubmittedEvent event) {
    Session session =
        sessionRepository
            .findById(event.sessionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Session not found for submitted event: " + event.sessionId()));

    session.startProcessing();
    sessionRepository.save(session);

    session.markFailed("PIPELINE_NOT_IMPLEMENTED");
    sessionRepository.save(session);

    log.warn(
        "Session ingestion not yet implemented session_id={} reason=PIPELINE_NOT_IMPLEMENTED",
        session.id());
  }
}
