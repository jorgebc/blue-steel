package com.bluesteel.application.service.session;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.model.ingestion.SimilarityResult;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.ingestion.ConflictDetectionPort;
import com.bluesteel.application.port.out.ingestion.EntitySimilaritySearchPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates conflict detection (D-033): embeds the narrative summary header, retrieves bounded
 * world-state context via pgvector similarity search for each entity type, then delegates to {@link
 * ConflictDetectionPort} for LLM call 3. Skips the entire LLM call when no MATCH-resolved entities
 * are present (D-034 cost bounding — no new facts contradict state that wasn't updated).
 */
@Service
public class ConflictDetectionService {

  private static final Logger log = LoggerFactory.getLogger(ConflictDetectionService.class);

  private static final List<String> ENTITY_TYPES = List.of("actor", "space", "event", "relation");

  private final EmbeddingPort embeddingPort;
  private final EntitySimilaritySearchPort entitySimilaritySearchPort;
  private final ConflictDetectionPort conflictDetectionPort;
  private final int contextTopN;

  public ConflictDetectionService(
      EmbeddingPort embeddingPort,
      EntitySimilaritySearchPort entitySimilaritySearchPort,
      ConflictDetectionPort conflictDetectionPort,
      @Value("${blue-steel.conflict.context-top-n}") int contextTopN) {
    this.embeddingPort = embeddingPort;
    this.entitySimilaritySearchPort = entitySimilaritySearchPort;
    this.conflictDetectionPort = conflictDetectionPort;
    this.contextTopN = contextTopN;
  }

  /**
   * Runs conflict detection for a submitted session's pipeline outputs.
   *
   * <p>Returns an empty list immediately if no {@link ResolutionOutcome#MATCH}-resolved entities
   * are present — skipping the embedding + pgvector + LLM calls (D-033, D-034).
   *
   * @param campaignId scopes the pgvector search to this campaign's existing entities
   * @param extraction the structured output from the extraction stage
   * @param resolved the resolution outcomes from {@link EntityResolutionService}
   * @param contentLanguage the campaign's content-language code, injected into the LLM prompt
   *     (D-103)
   * @return non-blocking {@link ConflictWarning}s to surface in the diff (possibly empty)
   */
  public List<ConflictWarning> run(
      UUID campaignId,
      ExtractionResult extraction,
      List<ResolvedEntity> resolved,
      String contentLanguage) {
    log.info("Starting conflict detection stage campaignId={}", campaignId);

    boolean hasMatch = resolved.stream().anyMatch(r -> r.outcome() == ResolutionOutcome.MATCH);
    if (!hasMatch) {
      log.info("No MATCH entities — skipping conflict detection campaignId={}", campaignId);
      return List.of();
    }

    float[] queryVector = embeddingPort.embed(extraction.narrativeSummaryHeader());

    List<EntityContext> relevantContext = new ArrayList<>();
    for (String entityType : ENTITY_TYPES) {
      List<SimilarityResult> candidates =
          entitySimilaritySearchPort.search(queryVector, campaignId, entityType, contextTopN);
      for (SimilarityResult r : candidates) {
        relevantContext.add(
            new EntityContext(
                r.entityId(),
                r.entityType(),
                r.name(),
                r.stateSnapshot(),
                r.sessionId(),
                r.versionNumber()));
      }
    }

    List<ConflictWarning> warnings =
        conflictDetectionPort.detect(extraction, relevantContext, contentLanguage);
    log.info("Conflict detection complete campaignId={} warnings={}", campaignId, warnings.size());
    return warnings;
  }
}
