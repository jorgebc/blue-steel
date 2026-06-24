package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Async listener that drives a submitted session through the ingestion pipeline. Failures inside
 * each stage service are already handled — this listener is a pure loader and delegator.
 */
@Component
public class SessionIngestionEventListener {

  private static final Logger log = LoggerFactory.getLogger(SessionIngestionEventListener.class);

  private final SessionRepository sessionRepository;
  private final NarrativeBlockRepository narrativeBlockRepository;
  private final CampaignRepository campaignRepository;
  private final ExtractionPipelineService extractionPipelineService;
  private final EntityResolutionService entityResolutionService;
  private final ConflictDetectionService conflictDetectionService;
  private final DiffGenerationService diffGenerationService;
  private final TaskExecutor applicationTaskExecutor;

  public SessionIngestionEventListener(
      SessionRepository sessionRepository,
      NarrativeBlockRepository narrativeBlockRepository,
      CampaignRepository campaignRepository,
      ExtractionPipelineService extractionPipelineService,
      EntityResolutionService entityResolutionService,
      ConflictDetectionService conflictDetectionService,
      DiffGenerationService diffGenerationService,
      TaskExecutor applicationTaskExecutor) {
    this.sessionRepository = sessionRepository;
    this.narrativeBlockRepository = narrativeBlockRepository;
    this.campaignRepository = campaignRepository;
    this.extractionPipelineService = extractionPipelineService;
    this.entityResolutionService = entityResolutionService;
    this.conflictDetectionService = conflictDetectionService;
    this.diffGenerationService = diffGenerationService;
    this.applicationTaskExecutor = applicationTaskExecutor;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onSessionSubmitted(SessionSubmittedEvent event) {
    log.info("Received SessionSubmittedEvent for session {} after commit.", event.sessionId());
    applicationTaskExecutor.execute(
        () -> {
          log.info("Executing pipeline thread for session {}", event.sessionId());
          try {
            runPipeline(event);
          } catch (Exception e) {
            log.error(
                "Execution failed inside pipeline thread for session {}", event.sessionId(), e);
          }
        });
  }

  private void runPipeline(SessionSubmittedEvent event) {
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

    Campaign campaign =
        campaignRepository
            .findById(session.campaignId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Campaign not found for session: " + event.sessionId()));
    String contentLanguage = campaign.contentLanguage();

    ExtractionResult extractionResult =
        extractionPipelineService.run(session, block.rawSummaryText(), contentLanguage);

    List<ResolvedEntity> resolved =
        entityResolutionService.run(session.campaignId(), extractionResult);

    List<ConflictWarning> conflicts =
        conflictDetectionService.run(
            session.campaignId(), extractionResult, resolved, contentLanguage);

    diffGenerationService.run(session, extractionResult, resolved, conflicts);
  }
}
