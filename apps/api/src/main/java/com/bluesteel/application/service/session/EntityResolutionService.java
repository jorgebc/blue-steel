package com.bluesteel.application.service.session;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedEvent;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractedRelation;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.model.ingestion.SimilarityResult;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.ingestion.EntityResolutionPort;
import com.bluesteel.application.port.out.ingestion.EntitySimilaritySearchPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates two-stage entity resolution for every extracted mention (D-041):
 *
 * <ol>
 *   <li><b>Stage 1 — pgvector similarity search:</b> embed the mention and query the {@code
 *       entity_embeddings} table. Mentions whose top-score candidate falls below {@code
 *       similarity-floor} are classified NEW immediately — no LLM call.
 *   <li><b>Stage 2 — bounded LLM resolution:</b> for mentions whose top score exceeds the floor,
 *       map the top-{@code N} {@link SimilarityResult}s to {@link EntityContext} and delegate to
 *       {@link EntityResolutionPort} to obtain MATCH / NEW / UNCERTAIN (D-042).
 * </ol>
 */
@Service
public class EntityResolutionService {

  private static final Logger log = LoggerFactory.getLogger(EntityResolutionService.class);

  private final EmbeddingPort embeddingPort;
  private final EntitySimilaritySearchPort entitySimilaritySearchPort;
  private final EntityResolutionPort entityResolutionPort;
  private final double similarityFloor;
  private final int topN;

  public EntityResolutionService(
      EmbeddingPort embeddingPort,
      EntitySimilaritySearchPort entitySimilaritySearchPort,
      EntityResolutionPort entityResolutionPort,
      @Value("${blue-steel.resolution.similarity-floor}") double similarityFloor,
      @Value("${blue-steel.resolution.top-n}") int topN) {
    this.embeddingPort = embeddingPort;
    this.entitySimilaritySearchPort = entitySimilaritySearchPort;
    this.entityResolutionPort = entityResolutionPort;
    this.similarityFloor = similarityFloor;
    this.topN = topN;
  }

  /**
   * Resolves all extracted mentions to MATCH / NEW / UNCERTAIN outcomes.
   *
   * @param campaignId scopes the pgvector search to this campaign's existing entities
   * @param extraction the structured output from the extraction stage
   * @return one {@link ResolvedEntity} per mention across all entity types
   */
  public List<ResolvedEntity> run(UUID campaignId, ExtractionResult extraction) {
    log.info("Starting resolution stage campaignId={}", campaignId);
    List<ResolvedEntity> results = new ArrayList<>();

    results.addAll(resolveAll(extraction.actors(), "actor", campaignId));
    results.addAll(resolveAll(extraction.spaces(), "space", campaignId));
    results.addAll(
        resolveAll(
            extraction.events().stream().map(ExtractedEvent::toMention).toList(),
            "event",
            campaignId));
    results.addAll(
        resolveAll(
            extraction.relations().stream().map(ExtractedRelation::toMention).toList(),
            "relation",
            campaignId));

    log.info("Resolution complete campaignId={} resolved={}", campaignId, results.size());
    return results;
  }

  private List<ResolvedEntity> resolveAll(
      List<ExtractedMention> mentions, String entityType, UUID campaignId) {
    List<ResolvedEntity> results = new ArrayList<>(mentions.size());
    for (ExtractedMention mention : mentions) {
      results.add(resolveOne(mention, entityType, campaignId));
    }
    return results;
  }

  private ResolvedEntity resolveOne(ExtractedMention mention, String entityType, UUID campaignId) {
    String mentionContent = mention.name() + " " + mention.description();
    float[] vector = embeddingPort.embed(mentionContent);
    List<SimilarityResult> candidates =
        entitySimilaritySearchPort.search(vector, campaignId, entityType, topN);

    double maxSimilarity =
        candidates.stream().mapToDouble(SimilarityResult::similarity).max().orElse(0.0);

    if (maxSimilarity < similarityFloor) {
      return new ResolvedEntity(mention, ResolutionOutcome.NEW, null);
    }

    List<EntityContext> contexts =
        candidates.stream()
            .map(
                r ->
                    new EntityContext(
                        r.entityId(),
                        r.entityType(),
                        r.name(),
                        r.stateSnapshot(),
                        r.sessionId(),
                        r.versionNumber()))
            .toList();

    List<ResolvedEntity> portResults = entityResolutionPort.resolve(List.of(mention), contexts);
    return portResults.get(0);
  }
}
