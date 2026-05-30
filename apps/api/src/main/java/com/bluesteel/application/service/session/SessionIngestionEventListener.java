package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import java.util.List;
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
  private final EntityResolutionService entityResolutionService;

  public SessionIngestionEventListener(
      SessionRepository sessionRepository,
      NarrativeBlockRepository narrativeBlockRepository,
      ExtractionPipelineService extractionPipelineService,
      EntityResolutionService entityResolutionService) {
    this.sessionRepository = sessionRepository;
    this.narrativeBlockRepository = narrativeBlockRepository;
    this.extractionPipelineService = extractionPipelineService;
    this.entityResolutionService = entityResolutionService;
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
    // On failure: ExtractionPipelineService marks the session FAILED and rethrows.
    ExtractionResult extractionResult =
        extractionPipelineService.run(session, block.rawSummaryText());

    // Resolution stage — resolved entities held in-memory for conflict detection (F2.6).
    @SuppressWarnings("unused")
    List<ResolvedEntity> resolved =
        entityResolutionService.run(session.campaignId(), extractionResult);
  }
}
