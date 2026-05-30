package com.bluesteel.application.service.session;

import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.NarrativeExtractionPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.domain.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the extraction stage: transitions the session to {@code PROCESSING}, populates MDC
 * for cost logging (LOG-01), delegates to {@link NarrativeExtractionPort}, and handles failures by
 * marking the session {@code FAILED} before rethrowing (D-074).
 */
@Service
public class ExtractionPipelineService {

  private static final Logger log = LoggerFactory.getLogger(ExtractionPipelineService.class);

  private final NarrativeExtractionPort narrativeExtractionPort;
  private final SessionRepository sessionRepository;

  public ExtractionPipelineService(
      NarrativeExtractionPort narrativeExtractionPort, SessionRepository sessionRepository) {
    this.narrativeExtractionPort = narrativeExtractionPort;
    this.sessionRepository = sessionRepository;
  }

  /**
   * Runs the extraction stage. Transitions the session to {@code PROCESSING} on entry and returns
   * the {@link ExtractionResult} on success. On any exception, transitions the session to {@code
   * FAILED} with reason {@code EXTRACTION_FAILED} and rethrows.
   */
  public ExtractionResult run(Session session, String rawSummaryText) {
    log.info("Starting extraction stage session_id={}", session.id());
    session.startProcessing();
    sessionRepository.save(session);

    MDC.put("session_id", session.id().toString());
    MDC.put("user_id", session.ownerId().toString());
    try {
      ExtractionResult result = narrativeExtractionPort.extract(rawSummaryText);
      log.info("Extraction complete session_id={}", session.id());
      return result;
    } catch (Exception e) {
      session.markFailed("EXTRACTION_FAILED");
      sessionRepository.save(session);
      log.error("Extraction failed session_id={} reason=EXTRACTION_FAILED", session.id(), e);
      throw e;
    } finally {
      MDC.remove("session_id");
      MDC.remove("user_id");
    }
  }
}
