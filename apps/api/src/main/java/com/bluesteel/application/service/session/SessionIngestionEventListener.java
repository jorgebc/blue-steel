package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async listener that drives a submitted session through the ingestion pipeline. Incrementally
 * extended in F2.4–F2.7 as each pipeline stage is wired; failures inside each stage service are
 * already handled — this listener is a pure loader and delegator.
 */
@Component
public class SessionIngestionEventListener {

  private static final Logger log = LoggerFactory.getLogger(SessionIngestionEventListener.class);

  private final SessionRepository sessionRepository;
  private final NarrativeBlockRepository narrativeBlockRepository;
  private final ExtractionPipelineService extractionPipelineService;

  public SessionIngestionEventListener(
      SessionRepository sessionRepository,
      NarrativeBlockRepository narrativeBlockRepository,
      ExtractionPipelineService extractionPipelineService) {
    this.sessionRepository = sessionRepository;
    this.narrativeBlockRepository = narrativeBlockRepository;
    this.extractionPipelineService = extractionPipelineService;
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

    NarrativeBlock block =
        narrativeBlockRepository
            .findBySessionId(event.sessionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "NarrativeBlock not found for session: " + event.sessionId()));

    // Extraction stage — session transitions to PROCESSING inside the service.
    // On success: session remains PROCESSING (entity resolution follows in F2.5).
    // On failure: ExtractionPipelineService marks the session FAILED and rethrows.
    extractionPipelineService.run(session, block.rawSummaryText());
  }
}
