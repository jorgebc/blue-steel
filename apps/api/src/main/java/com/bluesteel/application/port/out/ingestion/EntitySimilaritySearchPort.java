package com.bluesteel.application.port.out.ingestion;

import com.bluesteel.application.model.ingestion.SimilarityResult;
import java.util.List;
import java.util.UUID;

/**
 * Driven port for Stage-1 pgvector similarity search during entity resolution (D-041, D-062).
 * Returns candidate world-state entities ordered by cosine similarity to the query vector. Never
 * use Spring AI {@code VectorStore} — all pgvector queries are native SQL (ARCH-04).
 */
public interface EntitySimilaritySearchPort {

  /**
   * Searches for the top-N most similar world-state entities of the given type within a campaign.
   *
   * @param queryVector the embedding of the extracted mention
   * @param campaignId scopes the search to this campaign's entities
   * @param entityType one of {@code "actor"}, {@code "space"}, {@code "event"}, {@code "relation"}
   * @param topN maximum number of candidates to return
   */
  List<SimilarityResult> search(float[] queryVector, UUID campaignId, String entityType, int topN);
}
